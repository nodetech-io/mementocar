// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ChecklistItemEntity
import java.time.ZoneId

/** Reminders that apply to a new Bluetooth event. */
fun dueReminderItems(
    checklist: List<ChecklistItemEntity>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ChecklistItemEntity> {
    return checklist.filter { it.isDueForReminder(nowMillis, zoneId) }
}

/**
 * Reminders of an event that was already persisted: its future-trip eligibility was checked when
 * the event happened, so a later reschedule must not hide them.
 */
fun persistedEventReminderItems(
    checklist: List<ChecklistItemEntity>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ChecklistItemEntity> {
    return checklist.filter {
        it.isDueForReminder(nowMillis, zoneId, futureTripEligibilityAlreadySatisfied = true)
    }
}
