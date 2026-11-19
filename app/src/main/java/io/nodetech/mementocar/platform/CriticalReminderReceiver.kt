// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.domain.CriticalReminderAction
import io.nodetech.mementocar.domain.criticalReminderAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CriticalReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MementoCarRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: CriticalReminderScheduler
    @Inject lateinit var alarmStore: PendingReminderAlarmStore
    @Inject lateinit var operationCoordinator: ReminderOperationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return
        val repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, 0)
        val firstShownAtMillis = intent.getLongExtra(EXTRA_FIRST_SHOWN_AT_MILLIS, 0L)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                operationCoordinator.runExclusive {
                    repeatIfStillPending(eventId, repeatCount, firstShownAtMillis)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun repeatIfStillPending(
        eventId: Long,
        repeatCount: Int,
        firstShownAtMillis: Long,
    ) {
        alarmStore.removeCriticalRepeat(eventId)
        val event = repository.reminderEventById(eventId) ?: return
        val eventItems = repository.reminderEventItems(eventId)
        when (
            val action = criticalReminderAction(
                eventStatus = event.status,
                eventItems = eventItems,
                suppressedItemIds = notifier.suppressedCriticalIds(eventId),
                repeatCount = repeatCount,
                firstShownAtMillis = firstShownAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            CriticalReminderAction.Stop -> return
            CriticalReminderAction.CompleteEvent -> {
                repository.completeReminderEvent(eventId, eventItems.map { it.itemId })
                notifier.dismissReminder(eventId)
                return
            }
            is CriticalReminderAction.ShowItems -> showAndScheduleNext(
                eventId = eventId,
                itemIds = action.itemIds,
                nextRepeatCount = action.nextRepeatCount,
                firstShownAtMillis = firstShownAtMillis,
            )
        }
    }

    private suspend fun showAndScheduleNext(
        eventId: Long,
        itemIds: List<Long>,
        nextRepeatCount: Int,
        firstShownAtMillis: Long,
    ) {
        val event = repository.reminderEventById(eventId) ?: return
        val vehicle = repository.vehicleById(event.vehicleId) ?: return
        val order = itemIds.withIndex().associate { it.value to it.index }
        val reminderItems = repository.reminderEventItems(eventId)
            .filter { it.itemId in itemIds }
            .sortedBy { order[it.itemId] ?: Int.MAX_VALUE }
            .map { item -> ReminderNotificationItem(item.itemId, item.labelSnapshot) }
        if (reminderItems.isEmpty()) return
        val shown = notifier.showReminder(
            eventId = eventId,
            vehicleName = vehicle.name,
            trigger = event.trigger,
            reminderItems = reminderItems,
        )
        if (shown) {
            scheduler.scheduleNext(
                eventId = eventId,
                repeatCount = nextRepeatCount,
                firstShownAtMillis = firstShownAtMillis,
            )
        }
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_REPEAT_COUNT = "repeat_count"
        private const val EXTRA_FIRST_SHOWN_AT_MILLIS = "first_shown_at_millis"

        fun intent(
            context: Context,
            eventId: Long,
            repeatCount: Int,
            firstShownAtMillis: Long,
        ): Intent {
            return Intent(context, CriticalReminderReceiver::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_REPEAT_COUNT, repeatCount)
                putExtra(EXTRA_FIRST_SHOWN_AT_MILLIS, firstShownAtMillis)
            }
        }
    }
}
