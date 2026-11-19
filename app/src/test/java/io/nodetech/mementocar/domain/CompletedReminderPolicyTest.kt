// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedReminderPolicyTest {
    @Test
    fun returnsOnlyCompletedOneTimeItems() {
        val items = listOf(
            item(id = 1L, recurrence = ReminderRecurrence.Once),
            item(id = 2L, recurrence = ReminderRecurrence.EveryTrigger),
            item(id = 3L, recurrence = ReminderRecurrence.Once),
        )

        val result = completedOneTimeReminderItemIds(
            checklist = items,
            completedItemIds = setOf(1L, 2L),
        )

        assertEquals(listOf(1L), result)
    }

    @Test
    fun ignoresInactiveOneTimeItems() {
        val result = completedOneTimeReminderItemIds(
            checklist = listOf(item(id = 1L, recurrence = ReminderRecurrence.Once, active = false)),
            completedItemIds = setOf(1L),
        )

        assertEquals(emptyList<Long>(), result)
    }

    private fun item(
        id: Long,
        recurrence: ReminderRecurrence,
        active: Boolean = true,
    ): ChecklistItemEntity {
        return ChecklistItemEntity(
            id = id,
            vehicleId = 10L,
            label = "Recordatorio $id",
            active = active,
            sortOrder = id.toInt(),
            trigger = ReminderTrigger.Disconnect,
            recurrence = recurrence,
            activeDaysMask = ActiveDays.All,
        )
    }
}
