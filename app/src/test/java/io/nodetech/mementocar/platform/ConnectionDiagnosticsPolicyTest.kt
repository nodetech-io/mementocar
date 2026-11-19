// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionDiagnosticsPolicyTest {
    @Test
    fun manualNotificationTestDoesNotCountAsAutomaticSuccess() {
        val state = ConnectionDiagnosticsState().withEvent(event(ConnectionDiagnosticResult.TestShown))

        assertEquals(ConnectionDiagnosticResult.TestShown, state.latestManualTest?.result)
        assertNull(state.latestAutomaticSuccess)
    }

    @Test
    fun automaticReminderShownCountsAsAutomaticSuccess() {
        val state = ConnectionDiagnosticsState().withEvent(event(ConnectionDiagnosticResult.ReminderShown))

        assertEquals(ConnectionDiagnosticResult.ReminderShown, state.latestAutomaticSuccess?.result)
    }

    @Test
    fun automaticReminderScheduledCountsAsAutomaticSuccess() {
        val state = ConnectionDiagnosticsState().withEvent(event(ConnectionDiagnosticResult.ReminderScheduled))

        assertEquals(ConnectionDiagnosticResult.ReminderScheduled, state.latestAutomaticSuccess?.result)
    }

    @Test
    fun neutralEventsDoNotOverwritePreviousAutomaticSuccess() {
        val success = event(ConnectionDiagnosticResult.ReminderShown, occurredAtMillis = 1_000L)
        val duplicate = event(ConnectionDiagnosticResult.DuplicateIgnored, occurredAtMillis = 2_000L)
        val noDue = event(ConnectionDiagnosticResult.NoDueReminders, occurredAtMillis = 3_000L)

        val state = ConnectionDiagnosticsState()
            .withEvent(success)
            .withEvent(duplicate)
            .withEvent(noDue)

        assertEquals(ConnectionDiagnosticResult.NoDueReminders, state.latestEvent?.result)
        assertEquals(success, state.latestAutomaticSuccess)
    }

    @Test
    fun blockedNotificationDoesNotOverwritePreviousAutomaticSuccess() {
        val success = event(ConnectionDiagnosticResult.ReminderScheduled, occurredAtMillis = 1_000L)
        val blocked = event(ConnectionDiagnosticResult.NotificationsBlocked, occurredAtMillis = 2_000L)

        val state = ConnectionDiagnosticsState()
            .withEvent(success)
            .withEvent(blocked)

        assertEquals(ConnectionDiagnosticResult.NotificationsBlocked, state.latestEvent?.result)
        assertEquals(success, state.latestAutomaticSuccess)
    }

    @Test
    fun noDueRemindersDoesNotOverwritePreviousAutomaticSuccess() {
        val success = event(ConnectionDiagnosticResult.ReminderShown, occurredAtMillis = 1_000L)
        val noDue = event(ConnectionDiagnosticResult.NoDueReminders, occurredAtMillis = 2_000L)

        val state = ConnectionDiagnosticsState()
            .withEvent(success)
            .withEvent(noDue)

        assertEquals(ConnectionDiagnosticResult.NoDueReminders, state.latestEvent?.result)
        assertEquals(success, state.latestAutomaticSuccess)
    }

    @Test
    fun roundTripsCurrentDiagnosticsFormat() {
        val manual = event(ConnectionDiagnosticResult.TestShown, occurredAtMillis = 1_000L)
        val automatic = event(ConnectionDiagnosticResult.ReminderShown, occurredAtMillis = 2_000L)
        val latest = event(ConnectionDiagnosticResult.NoDueReminders, occurredAtMillis = 3_000L)
        val state = ConnectionDiagnosticsState(
            latestEvent = latest,
            latestManualTest = manual,
            latestAutomaticSuccess = automatic,
        )

        val decoded = state.toPreferenceValue().decodeConnectionDiagnosticsState(vehicleId = 42L)

        assertEquals(state, decoded)
    }

    private fun event(
        result: ConnectionDiagnosticResult,
        occurredAtMillis: Long = 1_000L,
    ): ConnectionDiagnosticEvent {
        return ConnectionDiagnosticEvent(
            vehicleId = 42L,
            trigger = ReminderTrigger.Disconnect,
            result = result,
            occurredAtMillis = occurredAtMillis,
        )
    }
}
