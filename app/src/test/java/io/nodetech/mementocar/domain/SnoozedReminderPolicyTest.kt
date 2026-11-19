// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozedReminderPolicyTest {
    @Test
    fun ignoresEventsThatAreNotSnoozed() {
        assertEquals(
            SnoozedReminderAction.Ignore,
            snoozedReminderAction(
                eventStatus = ReminderStatus.Completed,
                shownItemIds = setOf(1L),
                completedItemIds = emptySet(),
                requestedItemId = -1L,
            ),
        )
        assertEquals(
            SnoozedReminderAction.Ignore,
            snoozedReminderAction(
                eventStatus = ReminderStatus.Dismissed,
                shownItemIds = setOf(1L),
                completedItemIds = emptySet(),
                requestedItemId = -1L,
            ),
        )
        assertEquals(
            SnoozedReminderAction.Ignore,
            snoozedReminderAction(
                eventStatus = ReminderStatus.Pending,
                shownItemIds = setOf(1L),
                completedItemIds = emptySet(),
                requestedItemId = -1L,
            ),
        )
    }

    @Test
    fun showsOnlyPendingShownItemsForWholeEventSnooze() {
        assertEquals(
            SnoozedReminderAction.ShowItems(listOf(1L, 3L)),
            snoozedReminderAction(
                eventStatus = ReminderStatus.Snoozed,
                shownItemIds = setOf(1L, 2L, 3L),
                completedItemIds = setOf(2L),
                requestedItemId = -1L,
            ),
        )
    }

    @Test
    fun limitsReminderToRequestedItemWhenItIsPending() {
        assertEquals(
            SnoozedReminderAction.ShowItems(listOf(3L)),
            snoozedReminderAction(
                eventStatus = ReminderStatus.Snoozed,
                shownItemIds = setOf(1L, 2L, 3L),
                completedItemIds = setOf(2L),
                requestedItemId = 3L,
            ),
        )
    }

    @Test
    fun showsLaterItemAlarmAfterFirstSnoozeReturnsEventToPending() {
        assertEquals(
            SnoozedReminderAction.ShowItems(listOf(3L)),
            snoozedReminderAction(
                eventStatus = ReminderStatus.Pending,
                shownItemIds = setOf(1L, 2L, 3L),
                completedItemIds = setOf(1L),
                requestedItemId = 3L,
            ),
        )
    }

    @Test
    fun ignoresRequestedItemWhenItIsAlreadyCompleted() {
        assertEquals(
            SnoozedReminderAction.Ignore,
            snoozedReminderAction(
                eventStatus = ReminderStatus.Snoozed,
                shownItemIds = setOf(1L, 2L, 3L),
                completedItemIds = setOf(2L),
                requestedItemId = 2L,
            ),
        )
    }

    @Test
    fun completesEventWhenThereAreNoPendingItems() {
        assertEquals(
            SnoozedReminderAction.CompleteEvent,
            snoozedReminderAction(
                eventStatus = ReminderStatus.Snoozed,
                shownItemIds = setOf(1L, 2L),
                completedItemIds = setOf(1L, 2L),
                requestedItemId = -1L,
            ),
        )
    }
}
