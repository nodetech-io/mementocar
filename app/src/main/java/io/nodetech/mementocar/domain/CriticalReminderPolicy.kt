// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderEventItemEntity
import io.nodetech.mementocar.data.ReminderStatus

sealed interface CriticalReminderAction {
    data object Stop : CriticalReminderAction
    data object CompleteEvent : CriticalReminderAction
    data class ShowItems(
        val itemIds: List<Long>,
        val nextRepeatCount: Int,
    ) : CriticalReminderAction
}

fun criticalReminderAction(
    eventStatus: ReminderStatus,
    eventItems: List<ReminderEventItemEntity>,
    repeatCount: Int,
    firstShownAtMillis: Long,
    nowMillis: Long,
    suppressedItemIds: Set<Long> = emptySet(),
): CriticalReminderAction {
    if (eventStatus != ReminderStatus.Pending) return CriticalReminderAction.Stop
    if (repeatCount >= CRITICAL_MAX_REPEATS) return CriticalReminderAction.Stop
    if (firstShownAtMillis <= 0L || nowMillis - firstShownAtMillis >= CRITICAL_MAX_WINDOW_MILLIS) {
        return CriticalReminderAction.Stop
    }

    val pendingCriticalIds = eventItems
        .filter { it.critical && !it.completed && it.itemId !in suppressedItemIds }
        .sortedBy { it.sortOrder }
        .map { it.itemId }

    if (pendingCriticalIds.isEmpty()) {
        val allItemsCompleted = eventItems.isNotEmpty() && eventItems.all { it.completed }
        return if (allItemsCompleted) CriticalReminderAction.CompleteEvent else CriticalReminderAction.Stop
    }

    return CriticalReminderAction.ShowItems(
        itemIds = pendingCriticalIds,
        nextRepeatCount = repeatCount + 1,
    )
}

const val CRITICAL_REPEAT_INTERVAL_MILLIS: Long = 10 * 60 * 1000L
const val CRITICAL_MAX_WINDOW_MILLIS: Long = 60 * 60 * 1000L
const val CRITICAL_MAX_REPEATS: Int = 6
