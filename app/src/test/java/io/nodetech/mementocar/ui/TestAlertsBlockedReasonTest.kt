// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAlertsBlockedReasonTest {
    @Test
    fun missingVehicleWinsOverBlockedNotifications() {
        assertEquals(TestAlertsBlockedReason.NoVehicle, testAlertsBlockedReason(false, false))
        assertEquals(TestAlertsBlockedReason.NoVehicle, testAlertsBlockedReason(false, true))
    }

    @Test
    fun blockedNotificationsAreReportedWhenCarExists() {
        assertEquals(TestAlertsBlockedReason.NotificationsBlocked, testAlertsBlockedReason(true, false))
    }

    @Test
    fun noReasonWhenTestsCanRun() {
        assertNull(testAlertsBlockedReason(true, true))
    }

    @Test
    fun canTestAlertsFollowsReason() {
        assertFalse(DiagnosticsUiState().canTestAlerts)
        assertTrue(DiagnosticsUiState(testAlertsBlockedReason = null).canTestAlerts)
    }

    @Test
    fun messagesWithSameTextButDifferentKindAreDistinct() {
        assertTrue(UiMessage("x", MessageKind.Error) != UiMessage("x", MessageKind.Success))
    }
}
