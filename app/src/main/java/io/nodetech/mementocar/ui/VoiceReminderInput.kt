// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.nodetech.mementocar.R
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal data class VoiceInputState(
    val isListening: Boolean,
    val startForReview: () -> Unit,
    val startForHandsFree: () -> Unit,
    val startForConfirmation: () -> Unit,
    val cancel: () -> Unit,
)

internal enum class VoiceInputMode {
    Review,
    HandsFree,
    Confirmation,
}

@Composable
internal fun rememberVoiceReminderInput(
    onReviewResult: (List<String>) -> Unit,
    onHandsFreeResult: (List<String>) -> Unit,
    onMessage: (String) -> Unit,
    onConfirmationResult: (List<String>) -> Unit = {},
    onFailure: (String) -> Unit = {},
    onCancelled: () -> Unit = {},
    confirmationPrompt: String? = null,
): VoiceInputState {
    val failureResult by rememberUpdatedState(onFailure)
    val cancelledResult by rememberUpdatedState(onCancelled)
    val reviewResult by rememberUpdatedState(onReviewResult)
    val handsFreeResult by rememberUpdatedState(onHandsFreeResult)
    val confirmationResult by rememberUpdatedState(onConfirmationResult)
    val messageResult by rememberUpdatedState(onMessage)
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isListening by remember { mutableStateOf(false) }
    var externalRecognitionPending by rememberSaveable { mutableStateOf(false) }
    var pendingVoiceStart by rememberSaveable { mutableStateOf<String?>(null) }
    var activeVoiceMode by rememberSaveable { mutableStateOf(VoiceInputMode.Review.name) }
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var recognitionGeneration by remember { mutableIntStateOf(0) }
    var embeddedRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val scope = rememberCoroutineScope()
    var startJob by remember { mutableStateOf<Job?>(null) }
    var supportQueried by remember { mutableStateOf(false) }
    var cachedSupport by remember { mutableStateOf<RecognitionLanguages?>(null) }
    var fallbackNoticeShown by remember { mutableStateOf(false) }
    lateinit var startRecognition: (VoiceInputMode) -> Unit

    fun showVoiceMessage(text: String) {
        messageResult(text)
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun showVoiceFailure(text: String) {
        showVoiceMessage(text)
        failureResult(text)
    }

    fun showFallbackNotice() {
        if (fallbackNoticeShown) return
        fallbackNoticeShown = true
        showVoiceMessage(resources.getString(R.string.voice_language_fallback_spanish))
    }

    fun deliverVoiceResult(hypotheses: List<String>) {
        when (voiceInputModeFromSavedName(activeVoiceMode)) {
            VoiceInputMode.Review -> reviewResult(hypotheses)
            VoiceInputMode.HandsFree -> handsFreeResult(hypotheses)
            VoiceInputMode.Confirmation -> confirmationResult(hypotheses)
        }
    }

    /** Language tag to listen with. Only Catalan needs a check; it never blocks or throws. */
    suspend fun resolveRecognitionLanguage(mode: VoiceInputMode): String {
        val locale = resources.configuration.locales[0]
        val appTag = voiceRecognitionLanguageTag(locale)
        if (locale.language.lowercase(Locale.ROOT) != "ca") return appTag
        if (!supportQueried) {
            cachedSupport = queryRecognitionLanguages(context, voiceRecognitionIntent(context, mode, confirmationPrompt, appTag))
            supportQueried = true
        }
        val decision = recognitionLanguageDecision(locale, cachedSupport)
        if (decision.fellBack) showFallbackNotice()
        return decision.tag
    }

    fun stopEmbeddedRecognizer() {
        recognitionGeneration++
        embeddedRecognizer?.let { recognizer ->
            runCatching { recognizer.destroy() }
        }
        embeddedRecognizer = null
        isListening = false
    }

    fun startEmbeddedRecognition(mode: VoiceInputMode, languageTag: String) {
        val recognitionAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)
        if (!recognitionAvailable) {
            isListening = false
            showVoiceFailure(resources.getString(R.string.voice_install_or_enable_recognition))
            return
        }
        stopEmbeddedRecognizer()
        runCatching {
            activeVoiceMode = mode.name
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            embeddedRecognizer = recognizer
            isListening = true
            showVoiceMessage(resources.getString(R.string.voice_listening_message))
            val generation = recognitionGeneration
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (generation != recognitionGeneration) return
                    isListening = true
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (generation != recognitionGeneration) return
                    stopEmbeddedRecognizer()
                    if (isRecognitionLanguageError(error, Build.VERSION.SDK_INT)) {
                        if (isCatalanTag(languageTag)) {
                            showFallbackNotice()
                            startEmbeddedRecognition(mode, SpanishFallbackTag)
                        } else {
                            showVoiceFailure(resources.getString(R.string.voice_error_language_unavailable))
                        }
                        return
                    }
                    showVoiceFailure(error.voiceInputMessage(context))
                }

                override fun onResults(results: Bundle?) {
                    if (generation != recognitionGeneration) return
                    val hypotheses = cleanRecognitionResults(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    )
                    stopEmbeddedRecognizer()
                    if (hypotheses.isEmpty()) {
                        showVoiceFailure(resources.getString(R.string.voice_could_not_recognize_reminder))
                    } else {
                        deliverVoiceResult(hypotheses)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(voiceRecognitionIntent(context, mode, confirmationPrompt, languageTag))
        }.onFailure {
            stopEmbeddedRecognizer()
            showVoiceFailure(resources.getString(R.string.voice_could_not_start_recognition))
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!externalRecognitionPending) return@rememberLauncherForActivityResult
        externalRecognitionPending = false
        isListening = false
        if (result.resultCode == Activity.RESULT_CANCELED) {
            cancelledResult()
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != Activity.RESULT_OK) {
            showVoiceFailure(resources.getString(R.string.voice_could_not_recognize_reminder))
            return@rememberLauncherForActivityResult
        }
        val hypotheses = cleanRecognitionResults(
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
        )
        if (hypotheses.isEmpty()) {
            showVoiceFailure(resources.getString(R.string.voice_could_not_recognize_reminder))
        } else {
            deliverVoiceResult(hypotheses)
        }
    }

    fun launchRecognition(mode: VoiceInputMode, languageTag: String) {
        try {
            activeVoiceMode = mode.name
            isListening = true
            showVoiceMessage(resources.getString(R.string.voice_listening_message))
            externalRecognitionPending = true
            speechLauncher.launch(voiceRecognitionIntent(context, mode, confirmationPrompt, languageTag))
        } catch (_: ActivityNotFoundException) {
            externalRecognitionPending = false
            isListening = false
            startEmbeddedRecognition(mode, languageTag)
        } catch (_: RuntimeException) {
            externalRecognitionPending = false
            isListening = false
            startEmbeddedRecognition(mode, languageTag)
        }
    }

    startRecognition = { mode ->
        activeVoiceMode = mode.name
        startJob?.cancel()
        // UNDISPATCHED: without a language check nothing suspends, so launching stays synchronous.
        startJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            isListening = true
            val tag = resolveRecognitionLanguage(mode)
            launchRecognition(mode, tag)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!permissionPending) return@rememberLauncherForActivityResult
        permissionPending = false
        if (granted) {
            pendingVoiceStart = activeVoiceMode
        } else {
            showVoiceFailure(resources.getString(R.string.voice_enable_microphone_permission))
        }
    }

    LaunchedEffect(pendingVoiceStart) {
        pendingVoiceStart?.let { savedMode ->
            pendingVoiceStart = null
            startRecognition(voiceInputModeFromSavedName(savedMode))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && embeddedRecognizer != null) {
                stopEmbeddedRecognizer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (embeddedRecognizer != null) stopEmbeddedRecognizer()
        }
    }

    val start = { mode: VoiceInputMode ->
        if (isListening || externalRecognitionPending || permissionPending || pendingVoiceStart != null) {
            showVoiceMessage(resources.getString(R.string.voice_error_busy))
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecognition(mode)
        } else {
            activeVoiceMode = mode.name
            permissionPending = true
            runCatching { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                .onFailure {
                    permissionPending = false
                    showVoiceFailure(resources.getString(R.string.voice_could_not_start_recognition))
                }
        }
    }
    return VoiceInputState(
        isListening = isListening || externalRecognitionPending || permissionPending || pendingVoiceStart != null,
        startForReview = { start(VoiceInputMode.Review) },
        startForHandsFree = { start(VoiceInputMode.HandsFree) },
        startForConfirmation = { start(VoiceInputMode.Confirmation) },
        cancel = {
            externalRecognitionPending = false
            permissionPending = false
            pendingVoiceStart = null
            if (startJob?.isActive == true) {
                startJob?.cancel()
                isListening = false
            }
            stopEmbeddedRecognizer()
        },
    )
}

internal fun voiceInputModeFromSavedName(savedName: String?): VoiceInputMode {
    return VoiceInputMode.entries.firstOrNull { it.name == savedName } ?: VoiceInputMode.Review
}

private fun voiceRecognitionIntent(
    context: Context,
    mode: VoiceInputMode,
    confirmationPrompt: String?,
    languageTag: String,
): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RECOGNITION_RESULTS)
        putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            if (mode == VoiceInputMode.Confirmation) {
                confirmationPrompt ?: context.getString(R.string.voice_confirmation_instruction)
            } else {
                context.getString(R.string.voice_prompt)
            },
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
}

internal const val MAX_RECOGNITION_RESULTS = 5
internal const val SpanishFallbackTag = "es-ES"
private const val LANGUAGE_SUPPORT_TIMEOUT_MS = 1500L

/** Trimmed, non-blank, de-duplicated recognizer hypotheses in their original (confidence) order. */
internal fun cleanRecognitionResults(raw: List<String?>?): List<String> =
    raw.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()

internal fun voiceRecognitionLanguageTag(locale: Locale): String {
    return when (locale.language.lowercase(Locale.ROOT)) {
        "en",
        "es",
        "ca" -> locale.toLanguageTag()
        else -> Locale.forLanguageTag(SpanishFallbackTag).toLanguageTag()
    }
}

internal data class RecognitionLanguages(
    val installedOnDeviceLanguages: List<String>,
    val onlineLanguages: List<String>,
)

internal data class RecognitionLanguageDecision(val tag: String, val fellBack: Boolean)

private fun languageOf(tag: String): String = Locale.forLanguageTag(tag.replace('_', '-')).language.lowercase(Locale.ROOT)

internal fun isCatalanTag(tag: String): Boolean = languageOf(tag) == "ca"

/**
 * Only Catalan can fall back: when the recognizer is known to support neither on-device nor online Catalan the
 * Spanish recognizer is used instead. Unknown support (null) keeps the app language.
 */
internal fun recognitionLanguageDecision(appLocale: Locale, support: RecognitionLanguages?): RecognitionLanguageDecision {
    val tag = voiceRecognitionLanguageTag(appLocale)
    if (appLocale.language.lowercase(Locale.ROOT) != "ca" || support == null) return RecognitionLanguageDecision(tag, false)
    // Some recognizers answer with empty lists: that means "unknown", not "Catalan unsupported".
    if (support.installedOnDeviceLanguages.isEmpty() && support.onlineLanguages.isEmpty()) {
        return RecognitionLanguageDecision(tag, false)
    }
    val supported = (support.installedOnDeviceLanguages + support.onlineLanguages).any(::isCatalanTag)
    return if (supported) {
        RecognitionLanguageDecision(tag, false)
    } else {
        RecognitionLanguageDecision(Locale.forLanguageTag(SpanishFallbackTag).toLanguageTag(), true)
    }
}

// SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED (12) and ERROR_LANGUAGE_UNAVAILABLE (13) exist since API 31.
internal fun isRecognitionLanguageError(error: Int, sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.S && (error == 12 || error == 13)

/** Null means unknown (API < 33, recognizer unavailable, timeout or error). Never throws. */
private suspend fun queryRecognitionLanguages(context: Context, intent: Intent): RecognitionLanguages? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return try {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) null else queryRecognitionLanguagesApi33(context, intent)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun queryRecognitionLanguagesApi33(context: Context, intent: Intent): RecognitionLanguages? {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    return try {
        withTimeoutOrNull(LANGUAGE_SUPPORT_TIMEOUT_MS) {
            suspendCancellableCoroutine<RecognitionLanguages?> { continuation ->
                recognizer.checkRecognitionSupport(
                    intent,
                    ContextCompat.getMainExecutor(context),
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    RecognitionLanguages(
                                        recognitionSupport.installedOnDeviceLanguages,
                                        recognitionSupport.onlineLanguages,
                                    ),
                                )
                            }
                        }

                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        }
    } finally {
        runCatching { recognizer.destroy() }
    }
}

private fun Int.voiceInputMessage(context: Context): String {
    if (isRecognitionLanguageError(this, Build.VERSION.SDK_INT)) {
        return context.getString(R.string.voice_error_language_unavailable)
    }
    return when (this) {
        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_error_audio)
        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.voice_could_not_start_recognition)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_error_missing_microphone_permission)
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_error_network)
        SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.voice_could_not_recognize_reminder)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.voice_error_busy)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_error_timeout)
        else -> context.getString(R.string.voice_error_generic)
    }
}
