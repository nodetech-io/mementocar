// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderEventItemEntity
import io.nodetech.mementocar.data.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CriticalReminderPolicyTest {
    @Test
    fun snoozingOneItemPreservesOtherCriticalRepeats() {
        for (critical in listOf(false, true)) {
            val action = criticalReminderAction(
                eventStatus = ReminderStatus.Pending,
                eventItems = listOf(item(1L, critical = critical), item(2L, critical = true)),
                repeatCount = 0,
                firstShownAtMillis = NOW,
                nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
                suppressedItemIds = setOf(1L),
            )
            assertEquals(CriticalReminderAction.ShowItems(listOf(2L), 1), action)
        }
    }

    @Test
    fun repeatsPendingCriticalItems() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Pending,
            eventItems = listOf(item(1L, critical = true), item(2L, critical = false)),
            repeatCount = 0,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
        )

        assertEquals(CriticalReminderAction.ShowItems(listOf(1L), nextRepeatCount = 1), action)
    }

    @Test
    fun stopsWhenEventIsNotPending() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Snoozed,
            eventItems = listOf(item(1L, critical = true)),
            repeatCount = 0,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
        )

        assertEquals(CriticalReminderAction.Stop, action)
    }

    @Test
    fun stopsWhenCriticalItemsAreCompletedAndNormalItemsRemain() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Pending,
            eventItems = listOf(
                item(1L, critical = true, completed = true),
                item(2L, critical = false, completed = false),
            ),
            repeatCount = 0,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
        )

        assertEquals(CriticalReminderAction.Stop, action)
    }

    @Test
    fun completesEventWhenAllItemsAreCompleted() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Pending,
            eventItems = listOf(item(1L, critical = true, completed = true)),
            repeatCount = 0,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
        )

        assertEquals(CriticalReminderAction.CompleteEvent, action)
    }

    @Test
    fun stopsAtRepeatLimit() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Pending,
            eventItems = listOf(item(1L, critical = true)),
            repeatCount = CRITICAL_MAX_REPEATS,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_REPEAT_INTERVAL_MILLIS,
        )

        assertEquals(CriticalReminderAction.Stop, action)
    }

    @Test
    fun stopsAtWindowLimit() {
        val action = criticalReminderAction(
            eventStatus = ReminderStatus.Pending,
            eventItems = listOf(item(1L, critical = true)),
            repeatCount = 0,
            firstShownAtMillis = NOW,
            nowMillis = NOW + CRITICAL_MAX_WINDOW_MILLIS,
        )

        assertEquals(CriticalReminderAction.Stop, action)
    }

    private fun item(
        itemId: Long,
        critical: Boolean,
        completed: Boolean = false,
    ): ReminderEventItemEntity {
        return ReminderEventItemEntity(
            eventId = 10L,
            itemId = itemId,
            labelSnapshot = "Item $itemId",
            sortOrder = itemId.toInt(),
            completed = completed,
            critical = critical,
        )
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
