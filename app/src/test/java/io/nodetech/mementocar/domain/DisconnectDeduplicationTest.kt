// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectDeduplicationTest {
    @Test
    fun acceptsWhenThereIsNoRecentEvent() {
        assertTrue(shouldCreateReminderForDisconnect(hasRecentEvent = false))
    }

    @Test
    fun rejectsWhenRecentEventExists() {
        assertFalse(shouldCreateReminderForDisconnect(hasRecentEvent = true))
    }
}
