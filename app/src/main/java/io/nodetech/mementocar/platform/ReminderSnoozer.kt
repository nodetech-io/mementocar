// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snoozes an open reminder event (itemId <= 0: the whole event) or a single item of it for [SNOOZE_MILLIS].
 * Shared by the notification Snooze button and the voice command. Takes [ReminderOperationCoordinator] itself, so
 * callers must not already hold it.
 */
@Singleton
class ReminderSnoozer @Inject constructor(
    private val repository: MementoCarRepository,
    private val notifier: ReminderNotifier,
    private val snoozeScheduler: SnoozeReminderScheduler,
    private val criticalReminderScheduler: CriticalReminderScheduler,
    private val operationCoordinator: ReminderOperationCoordinator,
) {
    /** Returns true when the event was open and the snooze was scheduled. */
    suspend fun snooze(eventId: Long, itemId: Long = -1): Boolean = operationCoordinator.runExclusive {
        val event = repository.reminderEventById(eventId) ?: return@runExclusive false
        if (event.status != ReminderStatus.Pending && event.status != ReminderStatus.Snoozed) return@runExclusive false
        if (itemId > 0) {
            notifier.suppressCriticalRepeat(eventId, itemId)
            notifier.updateSnoozed(eventId, itemId)
        } else {
            repository.updateReminderStatus(eventId, ReminderStatus.Snoozed)
            criticalReminderScheduler.cancel(eventId)
            notifier.updateSnoozed(eventId)
        }
        snoozeScheduler.schedule(eventId, itemId, SNOOZE_MILLIS)
        true
    }

    companion object {
        const val SNOOZE_MILLIS = 10 * 60 * 1000L
    }
}
