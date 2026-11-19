// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderRecurrencePolicyTest {
    private val zoneId = ZoneId.of("Europe/Madrid")

    @Test
    fun dueWhenNeverTriggered() {
        val item = item(ReminderRecurrence.Once, lastTriggeredAtMillis = null)

        assertTrue(item.isDueForReminder(nowMillis = 10_000L))
    }

    @Test
    fun onceIsNotDueAfterFirstTrigger() {
        val item = item(ReminderRecurrence.Once, lastTriggeredAtMillis = 1_000L)

        assertFalse(item.isDueForReminder(nowMillis = 10_000L))
    }

    @Test
    fun futureTripIsNotDueBeforeEligibilityBoundary() {
        val item = item(
            recurrence = ReminderRecurrence.Once,
            lastTriggeredAtMillis = null,
            eligibleFromMillis = 10_000L,
        )

        assertFalse(item.isDueForReminder(nowMillis = 9_999L))
    }

    @Test
    fun futureTripIsDueAtAndAfterEligibilityBoundary() {
        val item = item(
            recurrence = ReminderRecurrence.Once,
            lastTriggeredAtMillis = null,
            eligibleFromMillis = 10_000L,
        )

        assertTrue(item.isDueForReminder(nowMillis = 10_000L))
        assertTrue(item.isDueForReminder(nowMillis = 10_001L))
    }

    @Test
    fun everyTriggerIsAlwaysDueAfterFirstTrigger() {
        val item = item(ReminderRecurrence.EveryTrigger, lastTriggeredAtMillis = 1_000L)

        assertTrue(item.isDueForReminder(nowMillis = 1_001L))
    }

    @Test
    fun dailyWaitsForFullInterval() {
        val item = item(ReminderRecurrence.Daily, lastTriggeredAtMillis = 1_000L)

        assertFalse(item.isDueForReminder(nowMillis = 86_400_999L, zoneId = zoneId))
        assertTrue(item.isDueForReminder(nowMillis = 86_401_000L, zoneId = zoneId))
    }

    @Test
    fun rejectsWhenCurrentDayIsNotActive() {
        val sunday = LocalDate.of(2026, 6, 21).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val item = item(
            recurrence = ReminderRecurrence.EveryTrigger,
            lastTriggeredAtMillis = null,
            activeDaysMask = ActiveDays.Weekdays,
        )

        assertFalse(item.isDueForReminder(nowMillis = sunday, zoneId = zoneId))
    }

    @Test
    fun acceptsWhenCurrentDayIsActive() {
        val saturday = LocalDate.of(2026, 6, 20).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val item = item(
            recurrence = ReminderRecurrence.EveryTrigger,
            lastTriggeredAtMillis = null,
            activeDaysMask = ActiveDays.Weekend,
        )

        assertTrue(item.isDueForReminder(nowMillis = saturday, zoneId = zoneId))
    }

    private fun item(
        recurrence: ReminderRecurrence,
        lastTriggeredAtMillis: Long?,
        activeDaysMask: Int = ActiveDays.All,
        eligibleFromMillis: Long? = null,
    ): ChecklistItemEntity {
        return ChecklistItemEntity(
            vehicleId = 1L,
            label = "Comprar pan",
            sortOrder = 0,
            recurrence = recurrence,
            activeDaysMask = activeDaysMask,
            eligibleFromMillis = eligibleFromMillis,
            lastTriggeredAtMillis = lastTriggeredAtMillis,
        )
    }
}
