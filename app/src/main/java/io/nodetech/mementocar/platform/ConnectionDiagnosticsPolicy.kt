// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

data class ConnectionDiagnosticsState(
    val latestEvent: ConnectionDiagnosticEvent? = null,
    val latestManualTest: ConnectionDiagnosticEvent? = null,
    val latestAutomaticSuccess: ConnectionDiagnosticEvent? = null,
)

fun ConnectionDiagnosticsState.withEvent(event: ConnectionDiagnosticEvent): ConnectionDiagnosticsState {
    return copy(
        latestEvent = event,
        latestManualTest = if (event.result.isManualTest()) event else latestManualTest,
        latestAutomaticSuccess = if (event.result.isAutomaticSuccess()) event else latestAutomaticSuccess,
    )
}

fun ConnectionDiagnosticResult.isManualTest(): Boolean {
    return when (this) {
        ConnectionDiagnosticResult.TestShown,
        ConnectionDiagnosticResult.TestBlocked -> true
        ConnectionDiagnosticResult.ReminderShown,
        ConnectionDiagnosticResult.ReminderScheduled,
        ConnectionDiagnosticResult.NoDueReminders,
        ConnectionDiagnosticResult.DuplicateIgnored,
        ConnectionDiagnosticResult.NotificationsBlocked -> false
    }
}

fun ConnectionDiagnosticResult.isAutomaticSuccess(): Boolean {
    return when (this) {
        ConnectionDiagnosticResult.ReminderShown,
        ConnectionDiagnosticResult.ReminderScheduled -> true
        ConnectionDiagnosticResult.NoDueReminders,
        ConnectionDiagnosticResult.DuplicateIgnored,
        ConnectionDiagnosticResult.NotificationsBlocked,
        ConnectionDiagnosticResult.TestShown,
        ConnectionDiagnosticResult.TestBlocked -> false
    }
}
