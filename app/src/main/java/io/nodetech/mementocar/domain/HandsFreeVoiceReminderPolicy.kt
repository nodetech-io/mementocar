// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

sealed interface HandsFreeVoiceReminderAction {
    data class AddReminder(val command: VoiceCommand.AddItem) : HandsFreeVoiceReminderAction
    data class MarkItem(val command: VoiceCommand.MarkItem) : HandsFreeVoiceReminderAction
    data object Snooze : HandsFreeVoiceReminderAction
    data object CompleteAll : HandsFreeVoiceReminderAction
    data object MissingVehicle : HandsFreeVoiceReminderAction
    data object UnsupportedCommand : HandsFreeVoiceReminderAction
}

fun handsFreeVoiceReminderAction(
    command: VoiceCommand,
    selectedVehicleId: Long?,
): HandsFreeVoiceReminderAction {
    if (selectedVehicleId == null) return HandsFreeVoiceReminderAction.MissingVehicle
    return when (command) {
        is VoiceCommand.AddItem -> HandsFreeVoiceReminderAction.AddReminder(command)
        is VoiceCommand.MarkItem -> HandsFreeVoiceReminderAction.MarkItem(command)
        is VoiceCommand.Snooze -> HandsFreeVoiceReminderAction.Snooze
        is VoiceCommand.CompleteAll -> HandsFreeVoiceReminderAction.CompleteAll
        is VoiceCommand.Unknown -> HandsFreeVoiceReminderAction.UnsupportedCommand
    }
}
