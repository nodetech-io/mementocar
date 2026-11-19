// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

fun ChecklistItemEntity.isDueForReminder(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    futureTripEligibilityAlreadySatisfied: Boolean = false,
): Boolean {
    if (!futureTripEligibilityAlreadySatisfied && eligibleFromMillis?.let { nowMillis < it } == true) {
        return false
    }
    if (!isActiveOnDay(nowMillis, zoneId)) return false
    val lastTriggered = lastTriggeredAtMillis ?: return true
    return when (recurrence) {
        ReminderRecurrence.Once -> false
        ReminderRecurrence.EveryTrigger -> true
        ReminderRecurrence.Daily -> nowMillis - lastTriggered >= DAY_MILLIS
        ReminderRecurrence.Weekly -> nowMillis - lastTriggered >= WEEK_MILLIS
        ReminderRecurrence.Monthly -> nowMillis - lastTriggered >= MONTH_MILLIS
    }
}

fun ChecklistItemEntity.isActiveOnDay(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return activeDaysMask and dayMask(nowMillis, zoneId) != 0
}

fun dayMask(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int {
    return when (ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).dayOfWeek.value) {
        1 -> ActiveDays.Monday
        2 -> ActiveDays.Tuesday
        3 -> ActiveDays.Wednesday
        4 -> ActiveDays.Thursday
        5 -> ActiveDays.Friday
        6 -> ActiveDays.Saturday
        else -> ActiveDays.Sunday
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val MONTH_MILLIS = 30 * DAY_MILLIS
