// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeVoiceReminderPolicyTest {
    @Test
    fun addsReminderWhenVehicleIsSelectedAndCommandIsValid() {
        val command = VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect)

        assertEquals(
            HandsFreeVoiceReminderAction.AddReminder(command),
            handsFreeVoiceReminderAction(command, selectedVehicleId = 42L),
        )
    }

    @Test
    fun requiresSelectedVehicleBeforeSaving() {
        val command = VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect)

        assertTrue(
            handsFreeVoiceReminderAction(command, selectedVehicleId = null) is
                HandsFreeVoiceReminderAction.MissingVehicle,
        )
    }

    @Test
    fun rejectsUnknownCommands() {
        assertTrue(
            handsFreeVoiceReminderAction(VoiceCommand.Unknown("hola"), selectedVehicleId = 42L) is
                HandsFreeVoiceReminderAction.UnsupportedCommand,
        )
    }

    @Test
    fun routesMarkSnoozeAndCompleteAll() {
        val mark = VoiceCommand.MarkItem("llaves")
        assertEquals(
            HandsFreeVoiceReminderAction.MarkItem(mark),
            handsFreeVoiceReminderAction(mark, selectedVehicleId = 42L),
        )
        assertEquals(
            HandsFreeVoiceReminderAction.Snooze,
            handsFreeVoiceReminderAction(VoiceCommand.Snooze, selectedVehicleId = 42L),
        )
        assertEquals(
            HandsFreeVoiceReminderAction.CompleteAll,
            handsFreeVoiceReminderAction(VoiceCommand.CompleteAll, selectedVehicleId = 42L),
        )
    }

    @Test
    fun everyCommandRequiresASelectedVehicle() {
        listOf(
            VoiceCommand.MarkItem("llaves"),
            VoiceCommand.Snooze,
            VoiceCommand.CompleteAll,
            VoiceCommand.Unknown("hola"),
        ).forEach { command ->
            assertEquals(
                HandsFreeVoiceReminderAction.MissingVehicle,
                handsFreeVoiceReminderAction(command, selectedVehicleId = null),
            )
        }
    }
}
