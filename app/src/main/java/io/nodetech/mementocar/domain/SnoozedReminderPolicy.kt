// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderStatus

sealed interface SnoozedReminderAction {
    data object Ignore : SnoozedReminderAction
    data object CompleteEvent : SnoozedReminderAction
    data class ShowItems(val itemIds: List<Long>) : SnoozedReminderAction
}

fun snoozedReminderAction(
    eventStatus: ReminderStatus,
    shownItemIds: Set<Long>,
    completedItemIds: Set<Long>,
    requestedItemId: Long,
): SnoozedReminderAction {
    val isActiveSnooze = eventStatus == ReminderStatus.Snoozed ||
        (eventStatus == ReminderStatus.Pending && requestedItemId > 0L)
    if (!isActiveSnooze) return SnoozedReminderAction.Ignore

    val pendingItemIds = shownItemIds
        .minus(completedItemIds)
        .toList()

    if (pendingItemIds.isEmpty()) return SnoozedReminderAction.CompleteEvent

    if (requestedItemId <= 0L) return SnoozedReminderAction.ShowItems(pendingItemIds)

    return if (requestedItemId in pendingItemIds) {
        SnoozedReminderAction.ShowItems(listOf(requestedItemId))
    } else {
        SnoozedReminderAction.Ignore
    }
}
