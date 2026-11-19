// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.domain.VoiceCommand
import io.nodetech.mementocar.MainActivity
import io.nodetech.mementocar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceReminderIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private class RecognitionRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var pendingRequest: Int? = null
        var lastPrompt: String? = null
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            if (contract is ActivityResultContracts.RequestPermission) {
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, true) }
            } else {
                lastPrompt = (input as? Intent)?.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
                pendingRequest = requestCode
            }
        }
        fun finish(text: String?, resultCode: Int = Activity.RESULT_OK) {
            dispatchResult(
                checkNotNull(pendingRequest),
                ActivityResult(resultCode, Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text.orEmpty()))),
            )
            pendingRequest = null
        }
    }

    @Test
    fun handsFreeResultKeepsItsModeAcrossRecreation() {
        val registry = RecognitionRegistry()
        val delivered = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                val voice = rememberVoiceReminderInput(
                    onReviewResult = { delivered += "review:${it.first()}" },
                    onHandsFreeResult = { delivered += "handsFree:${it.first()}" },
                    onMessage = {},
                )
                Button(onClick = voice.startForHandsFree) { Text("Dictate") }
            }
        }
        compose.onNodeWithText("Dictate").performClick()
        compose.waitUntil { registry.pendingRequest != null }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.finish("recuérdame mochila al entrar.") }
        compose.runOnIdle { assertEquals(listOf("handsFree:recuérdame mochila al entrar."), delivered) }
    }

    @Test
    fun cancellationDoesNotDeliverACommandOrRecognitionError() {
        val registry = RecognitionRegistry()
        val messages = mutableListOf<String>()
        var delivered = false
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                val voice = rememberVoiceReminderInput(
                    onReviewResult = { delivered = true },
                    onHandsFreeResult = { delivered = true },
                    onMessage = { messages += it },
                )
                Button(onClick = voice.startForReview) { Text("Dictate") }
            }
        }
        compose.onNodeWithText("Dictate").performClick()
        compose.waitUntil { registry.pendingRequest != null }
        compose.runOnIdle {
            messages.clear()
            registry.finish(null, Activity.RESULT_CANCELED)
        }
        compose.runOnIdle {
            assertTrue(messages.isEmpty())
            assertTrue(!delivered)
        }
    }

    @Test
    fun confirmationButtonsWorkWithoutSpeechServices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var saves = 0
        var cancels = 0
        compose.setContent {
            VoiceReminderConfirmationContent(
                summary = "mochila · Coche · Al entrar",
                message = null,
                isListening = false,
                onReply = {},
                onConfirm = { saves++ },
                onCancel = { cancels++ },
            )
        }
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, cancels)
        }
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun cancelledRecognitionIgnoresALateSaveResult() {
        val registry = RecognitionRegistry()
        var delivered = false
        var cancel: () -> Unit = {}
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                val voice = rememberVoiceReminderInput(
                    onReviewResult = { delivered = true },
                    onHandsFreeResult = { delivered = true },
                    onConfirmationResult = { delivered = true },
                    onMessage = {},
                )
                cancel = voice.cancel
                Button(onClick = voice.startForConfirmation) { Text("Confirm by voice") }
            }
        }
        compose.onNodeWithText("Confirm by voice").performClick()
        compose.waitUntil { registry.pendingRequest != null }
        compose.runOnIdle {
            cancel()
            registry.finish("guardar")
        }
        compose.runOnIdle { assertTrue(!delivered) }
    }

    @Test
    fun fullConfirmationDialogConsumesOnlyOneDecision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = RecognitionRegistry()
        var saves = 0
        var cancels = 0
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                VoiceReminderConfirmationDialog(
                    initialCommand = VoiceCommand.AddItem("mochila"),
                    vehicleName = "Coche",
                    onConfirm = { saves++ },
                    onCancel = { cancels++ },
                )
            }
        }
        // Keep the dialog composed to exercise a callback arriving before its parent removes it.
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            registry.pendingRequest?.let { registry.finish("guardar") }
            assertEquals(1, saves)
            assertEquals(0, cancels)
        }
    }

    @Test
    fun voiceCorrectionsSurviveRestorationAndSaveOnlyAfterConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = RecognitionRegistry()
        val saved = mutableListOf<VoiceCommand.AddItem>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                VoiceReminderConfirmationDialog(
                    initialCommand = VoiceCommand.AddItem("mochila"),
                    vehicleName = "Coche",
                    onConfirm = { saved += it },
                    onCancel = {},
                )
            }
        }
        fun reply(text: String, expectedPrompt: String? = null) {
            if (registry.pendingRequest == null) {
                compose.onNodeWithText(context.getString(R.string.voice_confirmation_listen)).performClick()
            }
            compose.waitUntil { registry.pendingRequest != null }
            compose.runOnIdle {
                if (expectedPrompt != null) assertEquals(expectedPrompt, registry.lastPrompt)
                registry.finish(text)
            }
            compose.waitForIdle()
        }
        reply("al entrar")
        reply("cambiar texto")
        reply("Llaves de casa", context.getString(R.string.voice_replace_text_prompt))
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        restoration.emulateSavedInstanceStateRestore()
        reply("guardar")
        compose.runOnIdle { assertEquals(listOf(VoiceCommand.AddItem("Llaves de casa", ReminderTrigger.Connect)), saved) }
    }

    @Test
    fun threeUnrecognizedVoiceRepliesCancelWithoutSaving() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = RecognitionRegistry()
        var saves = 0
        var cancels = 0
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                VoiceReminderConfirmationDialog(
                    initialCommand = VoiceCommand.AddItem("mochila"),
                    vehicleName = "Coche",
                    onConfirm = { saves++ },
                    onCancel = { cancels++ },
                )
            }
        }
        repeat(3) {
            if (registry.pendingRequest == null) {
                compose.onNodeWithText(context.getString(R.string.voice_confirmation_listen)).performClick()
            }
            compose.waitUntil { registry.pendingRequest != null }
            compose.runOnIdle { registry.finish("tal vez") }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, cancels)
        }
    }

    @Test
    fun systemDiscoversVoiceShortcut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shortcut = context.getSystemService(ShortcutManager::class.java)
            .manifestShortcuts.single { it.id == "add_voice_reminder" }
        assertEquals(MainActivity.ACTION_ADD_VOICE_REMINDER, shortcut.intent?.action)
        assertEquals(MainActivity::class.java.name, shortcut.intent?.component?.className)
    }
}
