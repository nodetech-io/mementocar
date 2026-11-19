// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
internal fun rememberSpokenMessage(
    prompt: String,
    onComplete: () -> Unit = {},
    onUnavailable: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val locale = Locale.forLanguageTag(voiceRecognitionLanguageTag(LocalConfiguration.current.locales[0]))
    val lifecycleOwner = LocalLifecycleOwner.current
    val completed by rememberUpdatedState(onComplete)
    val unavailable by rememberUpdatedState(onUnavailable)
    var promptStarted by rememberSaveable { mutableStateOf(false) }
    var stopSpeech by remember { mutableStateOf<() -> Unit>({}) }
    DisposableEffect(prompt, lifecycleOwner, locale) {
        val handler = Handler(Looper.getMainLooper())
        var disposed = false
        var engine: TextToSpeech? = null
        val playback = VoiceConfirmationPlayback(
            alreadyStarted = promptStarted,
            speak = {
                val tts = engine
                tts != null && tts.setLanguage(locale) >= 0 &&
                    tts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "voice-confirmation") != TextToSpeech.ERROR
            },
            stopOutput = { engine?.stop() },
            onStarted = { promptStarted = true },
            onComplete = { completed() },
            onUnavailable = { if (!disposed) unavailable() },
        )
        // All platform callbacks are marshalled to the main thread before touching UI state.
        val initializationTimeout = Runnable { if (!disposed) playback.onError() }
        handler.postDelayed(initializationTimeout, 10_000L)
        runCatching {
            engine = TextToSpeech(context) { status ->
                handler.post {
                    if (!disposed) {
                        handler.removeCallbacks(initializationTimeout)
                        playback.onReady(status == TextToSpeech.SUCCESS)
                    }
                }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    handler.post { if (!disposed) playback.onDone() }
                }
                @Deprecated("Required by the platform listener")
                override fun onError(utteranceId: String?) {
                    handler.post { if (!disposed) playback.onError() }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    handler.post { if (!disposed) playback.onError() }
                }
            })
        }.onFailure {
            handler.removeCallbacks(initializationTimeout)
            playback.onError()
        }
        stopSpeech = playback::stop
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> playback.onResume()
                Lifecycle.Event.ON_PAUSE -> playback.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) playback.onResume()
        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            handler.removeCallbacksAndMessages(null)
            playback.stop()
            runCatching { engine?.shutdown() }
            stopSpeech = {}
        }
    }
    return stopSpeech
}
