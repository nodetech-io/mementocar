// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderPreviewPolicyTest {
    private val zoneId = ZoneId.of("Europe/Madrid")

    @Test
    fun includesActiveDueItems() {
        val now = LocalDate.of(2026, 6, 19).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val items = listOf(
            item(id = 1L, label = "Mochila", recurrence = ReminderRecurrence.Once),
            item(id = 2L, label = "Aceite", recurrence = ReminderRecurrence.EveryTrigger),
        )

        val dueItems = dueReminderItems(items, nowMillis = now, zoneId = zoneId)

        assertEquals(listOf(1L, 2L), dueItems.map { it.id })
    }

    @Test
    fun excludesItemsThatAreNotDueNow() {
        val sunday = LocalDate.of(2026, 6, 21).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val items = listOf(
            item(
                id = 1L,
                label = "Trabajo",
                recurrence = ReminderRecurrence.EveryTrigger,
                activeDaysMask = ActiveDays.Weekdays,
            ),
            item(
                id = 2L,
                label = "Único",
                recurrence = ReminderRecurrence.Once,
                lastTriggeredAtMillis = sunday - 1_000L,
            ),
        )

        val dueItems = dueReminderItems(items, nowMillis = sunday, zoneId = zoneId)

        assertEquals(emptyList<Long>(), dueItems.map { it.id })
    }

    @Test
    fun doesNotMutateRecurrenceState() {
        val now = LocalDate.of(2026, 6, 19).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val original = item(id = 1L, label = "Mochila", recurrence = ReminderRecurrence.Once)

        val dueItems = dueReminderItems(listOf(original), nowMillis = now, zoneId = zoneId)

        assertEquals(listOf(1L), dueItems.map { it.id })
        assertEquals(null, original.lastTriggeredAtMillis)
    }

    @Test
    fun excludesFutureTripUntilItsEligibilityTime() {
        val now = LocalDate.of(2026, 6, 19).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val futureTrip = item(
            id = 1L,
            label = "Maleta",
            recurrence = ReminderRecurrence.Once,
            eligibleFromMillis = now + 60_000L,
        )

        assertEquals(emptyList<Long>(), dueReminderItems(listOf(futureTrip), now, zoneId).map { it.id })
        assertEquals(listOf(1L), dueReminderItems(listOf(futureTrip), now + 60_000L, zoneId).map { it.id })
    }

    @Test
    fun persistedEventDoesNotReapplyEditedFutureTripEligibility() {
        val now = LocalDate.of(2026, 6, 19).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rescheduledAfterEvent = item(
            id = 1L,
            label = "Maleta",
            recurrence = ReminderRecurrence.Once,
            eligibleFromMillis = now + 60_000L,
        )
        val alreadyTriggered = item(
            id = 2L,
            label = "Documentación",
            recurrence = ReminderRecurrence.Once,
            lastTriggeredAtMillis = now - 1_000L,
            eligibleFromMillis = now + 60_000L,
        )

        val items = persistedEventReminderItems(
            checklist = listOf(rescheduledAfterEvent, alreadyTriggered),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(listOf(1L), items.map { it.id })
    }

    private fun item(
        id: Long,
        label: String,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int = ActiveDays.All,
        lastTriggeredAtMillis: Long? = null,
        eligibleFromMillis: Long? = null,
    ): ChecklistItemEntity {
        return ChecklistItemEntity(
            id = id,
            vehicleId = 1L,
            label = label,
            sortOrder = id.toInt(),
            recurrence = recurrence,
            activeDaysMask = activeDaysMask,
            eligibleFromMillis = eligibleFromMillis,
            lastTriggeredAtMillis = lastTriggeredAtMillis,
        )
    }
}
