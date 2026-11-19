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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.domain.VoiceCommand
import io.nodetech.mementocar.domain.VoiceConversationAction
import io.nodetech.mementocar.domain.VoiceReminderConversation

private val ConversationSaver = listSaver<VoiceReminderConversation, Any>(
    save = { listOf(it.command.label, it.command.trigger.name, it.replacingText, it.failures, it.turns, it.action.name) },
    restore = {
        VoiceReminderConversation(
            command = VoiceCommand.AddItem(it[0] as String, ReminderTrigger.valueOf(it[1] as String)),
            replacingText = it[2] as Boolean,
            failures = it[3] as Int,
            turns = it[4] as Int,
            action = VoiceConversationAction.valueOf(it[5] as String),
        )
    },
)

@Composable
internal fun VoiceReminderConfirmationDialog(
    initialCommand: VoiceCommand.AddItem,
    vehicleName: String,
    onConfirm: (VoiceCommand.AddItem) -> Unit,
    onCancel: () -> Unit,
) {
    var conversation by rememberSaveable(stateSaver = ConversationSaver) {
        mutableStateOf(VoiceReminderConversation(initialCommand.copy(label = initialCommand.label.toReminderLabel())))
    }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var stopSpeech: () -> Unit = {}
    lateinit var cancelRecognition: () -> Unit
    val retryMessage = stringResource(R.string.voice_confirmation_retry)
    val unavailableMessage = stringResource(R.string.voice_confirmation_no_speech)
    fun finish(confirm: Boolean) {
        if (finished) return
        finished = true
        stopSpeech()
        cancelRecognition()
        if (confirm) onConfirm(conversation.command) else onCancel()
    }
    fun applyResponse(next: VoiceReminderConversation, error: String? = null) {
        if (finished) return
        val failed = next.failures > conversation.failures
        conversation = next
        when (next.action) {
            VoiceConversationAction.Save -> finish(true)
            VoiceConversationAction.Cancel -> finish(false)
            VoiceConversationAction.Prompt -> {
                message = if (failed) error ?: retryMessage else null
                revision++
            }
        }
    }
    val instruction = stringResource(
        if (conversation.replacingText) R.string.voice_replace_text_prompt else R.string.voice_confirmation_instruction,
    )
    val voice = rememberVoiceReminderInput(
        onReviewResult = {},
        onHandsFreeResult = {},
        onMessage = {},
        onConfirmationResult = { applyResponse(conversation.reply(it)) },
        onFailure = { applyResponse(conversation.recognitionFailed(), it) },
        onCancelled = { finish(false) },
        confirmationPrompt = instruction,
    )
    cancelRecognition = voice.cancel
    val summary = stringResource(
        R.string.voice_confirmation_summary,
        conversation.command.label,
        vehicleName,
        stringResource(if (conversation.command.trigger == ReminderTrigger.Connect) R.string.trigger_connect else R.string.trigger_disconnect),
    )
    val prompt = remember(revision, summary, instruction) {
        listOfNotNull(message, if (conversation.replacingText) null else summary, instruction).joinToString(" ")
    }
    key(revision) {
        stopSpeech = rememberSpokenMessage(
            prompt = prompt,
            onComplete = { if (!finished) voice.startForConfirmation() },
            onUnavailable = { if (!finished) message = unavailableMessage },
        )
    }
    VoiceReminderConfirmationContent(
        summary = summary,
        instruction = instruction,
        message = message,
        isListening = voice.isListening,
        canSave = !conversation.replacingText && !finished,
        onReply = {
            if (!finished) {
                stopSpeech()
                voice.startForConfirmation()
            }
        },
        onConfirm = { if (!conversation.replacingText) finish(true) },
        onCancel = { finish(false) },
    )
}

@Composable
internal fun VoiceReminderConfirmationContent(
    summary: String,
    message: String?,
    isListening: Boolean,
    onReply: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    instruction: String = stringResource(R.string.voice_confirmation_instruction),
    canSave: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.voice_confirmation_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(summary)
                Text(instruction)
                message?.let { Text(it) }
                TextButton(onClick = onReply, enabled = !isListening) {
                    Text(stringResource(R.string.voice_confirmation_listen))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canSave) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
