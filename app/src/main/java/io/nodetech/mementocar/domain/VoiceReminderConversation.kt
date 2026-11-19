// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.DataLimits
import io.nodetech.mementocar.data.ReminderTrigger

internal enum class VoiceConversationAction { Prompt, Save, Cancel }

internal data class VoiceReminderConversation(
    val command: VoiceCommand.AddItem,
    val replacingText: Boolean = false,
    val failures: Int = 0,
    val turns: Int = 0,
    val action: VoiceConversationAction = VoiceConversationAction.Prompt,
) {
    fun reply(text: String): VoiceReminderConversation = reply(listOf(text))

    fun reply(hypotheses: List<String>): VoiceReminderConversation {
        if (action != VoiceConversationAction.Prompt) return this
        val text = hypotheses.firstOrNull().orEmpty()
        // Alternatives only help interpret control words; while replacing text only the first hypothesis counts.
        val response = if (replacingText) parseVoiceConfirmation(text) else resolveConfirmation(hypotheses)
        if (response == VoiceConfirmation.Cancel) return copy(action = VoiceConversationAction.Cancel)
        // No more than eight recognition rounds, including valid corrections/repeats.
        if (turns >= MAX_TURNS) return copy(action = VoiceConversationAction.Cancel)
        val next = copy(turns = turns + 1)
        if (replacingText) {
            if (response == VoiceConfirmation.Repeat || response == VoiceConfirmation.ChangeText) return next.bounded()
            // Control words cannot accidentally become the new reminder or save the old one.
            if (response != VoiceConfirmation.Unknown) return next.failed()
            val label = text.trim().trim { it in ".!?…" }.trim()
            if (label.isBlank() || label.length > DataLimits.ReminderLabelMaxChars) return next.failed()
            return next.copy(command = command.copy(label = label), replacingText = false).bounded()
        }
        return when (response) {
            VoiceConfirmation.Confirm -> next.copy(action = VoiceConversationAction.Save)
            VoiceConfirmation.Cancel -> next.copy(action = VoiceConversationAction.Cancel)
            VoiceConfirmation.Repeat -> next
            VoiceConfirmation.ChangeText -> next.copy(replacingText = true)
            VoiceConfirmation.OnEntry -> next.copy(command = command.copy(trigger = ReminderTrigger.Connect))
            VoiceConfirmation.OnExit -> next.copy(command = command.copy(trigger = ReminderTrigger.Disconnect))
            VoiceConfirmation.Unknown -> next.failed()
        }.bounded()
    }

    private fun resolveConfirmation(hypotheses: List<String>): VoiceConfirmation {
        val responses = hypotheses.map(::parseVoiceConfirmation)
        val first = responses.firstOrNull() ?: return VoiceConfirmation.Unknown
        if (first != VoiceConfirmation.Unknown) return first
        return responses.filter { it != VoiceConfirmation.Unknown }.distinct().singleOrNull() ?: VoiceConfirmation.Unknown
    }

    fun recognitionFailed(): VoiceReminderConversation {
        if (action != VoiceConversationAction.Prompt) return this
        return copy(turns = turns + 1).failed()
    }

    private fun bounded(): VoiceReminderConversation =
        if (turns >= MAX_TURNS && action == VoiceConversationAction.Prompt) copy(action = VoiceConversationAction.Cancel) else this

    private fun failed(): VoiceReminderConversation = copy(
        failures = failures + 1,
        action = if (failures + 1 >= MAX_FAILURES || turns >= MAX_TURNS) VoiceConversationAction.Cancel else VoiceConversationAction.Prompt,
    )

    companion object {
        const val MAX_FAILURES = 3
        const val MAX_TURNS = 8
    }
}
