// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.nodetech.mementocar.R
import io.nodetech.mementocar.domain.isVoiceCompleteAllConfirmed

/**
 * Asks (spoken and on screen) whether to mark every pending item of an alert as done. Only an explicit "yes"
 * confirms; any other recognised answer, a recognition failure or dismissing the dialog cancels.
 */
@Composable
internal fun VoiceCompleteAllConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var finished by remember { mutableStateOf(false) }
    var stopSpeech: () -> Unit = {}
    lateinit var cancelRecognition: () -> Unit
    fun finish(confirm: Boolean) {
        if (finished) return
        finished = true
        stopSpeech()
        cancelRecognition()
        if (confirm) onConfirm() else onCancel()
    }
    val question = pluralStringResource(R.plurals.voice_complete_all_question, count, count)
    val instruction = stringResource(R.string.voice_complete_all_instruction)
    val voice = rememberVoiceReminderInput(
        onReviewResult = {},
        onHandsFreeResult = {},
        onMessage = {},
        onConfirmationResult = { finish(isVoiceCompleteAllConfirmed(it)) },
        onFailure = { finish(false) },
        onCancelled = { finish(false) },
        confirmationPrompt = instruction,
    )
    cancelRecognition = voice.cancel
    val prompt = remember(question, instruction) { "$question $instruction" }
    key(prompt) {
        stopSpeech = rememberSpokenMessage(
            prompt = prompt,
            onComplete = { if (!finished) voice.startForConfirmation() },
            onUnavailable = {},
        )
    }
    AlertDialog(
        onDismissRequest = { finish(false) },
        title = { Text(stringResource(R.string.voice_complete_all_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(question)
                Text(instruction)
                TextButton(
                    onClick = {
                        if (!finished) {
                            stopSpeech()
                            voice.startForConfirmation()
                        }
                    },
                    enabled = !voice.isListening,
                ) { Text(stringResource(R.string.voice_confirmation_listen)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { finish(true) }, enabled = !finished) {
                Text(stringResource(R.string.voice_complete_all_button))
            }
        },
        dismissButton = {
            TextButton(onClick = { finish(false) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
