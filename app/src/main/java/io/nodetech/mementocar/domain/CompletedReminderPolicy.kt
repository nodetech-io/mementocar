// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence

fun completedOneTimeReminderItemIds(
    checklist: List<ChecklistItemEntity>,
    completedItemIds: Set<Long>,
): List<Long> {
    if (completedItemIds.isEmpty()) return emptyList()
    return checklist
        .filter { item ->
            item.active &&
                item.recurrence == ReminderRecurrence.Once &&
                item.id in completedItemIds
        }
        .map { it.id }
}
