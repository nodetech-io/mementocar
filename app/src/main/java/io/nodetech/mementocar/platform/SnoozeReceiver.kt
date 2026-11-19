// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.domain.SnoozedReminderAction
import io.nodetech.mementocar.domain.snoozedReminderAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SnoozeReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MementoCarRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var snoozer: ReminderSnoozer
    @Inject lateinit var alarmStore: PendingReminderAlarmStore
    @Inject lateinit var operationCoordinator: ReminderOperationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1)
        if (eventId <= 0) return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND_AGAIN -> operationCoordinator.runExclusive { remindAgain(eventId, itemId) }
                    else -> snoozer.snooze(eventId, itemId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun remindAgain(eventId: Long, itemId: Long) {
        try {
            val event = repository.reminderEventById(eventId) ?: return
            val persistedItems = repository.reminderEventItems(eventId)
            val shownItemIds = if (persistedItems.isEmpty()) {
                notifier.shownReminderIds(eventId)
            } else {
                persistedItems.map { it.itemId }.toSet()
            }
            val completedItemIds = if (persistedItems.isEmpty()) {
                notifier.completedReminderIds(eventId)
            } else {
                persistedItems.filter { it.completed }.map { it.itemId }.toSet()
            }
            val action = snoozedReminderAction(
                eventStatus = event.status,
                shownItemIds = shownItemIds,
                completedItemIds = completedItemIds,
                requestedItemId = itemId,
            )
            when (action) {
                SnoozedReminderAction.Ignore -> return
                SnoozedReminderAction.CompleteEvent -> {
                    repository.completeReminderEvent(eventId, completedItemIds)
                    notifier.dismissReminder(eventId)
                    return
                }
                is SnoozedReminderAction.ShowItems -> showSnoozedItems(eventId, action.itemIds)
            }
        } finally {
            alarmStore.removeSnoozed(eventId, itemId)
        }
    }

    private suspend fun showSnoozedItems(eventId: Long, itemIds: List<Long>) {
        val event = repository.reminderEventById(eventId) ?: return
        val vehicle = repository.vehicleById(event.vehicleId) ?: return
        val order = itemIds.withIndex().associate { it.value to it.index }
        val persistedItems = repository.reminderEventItems(eventId)
            .filter { it.itemId in itemIds }
            .sortedBy { order[it.itemId] ?: Int.MAX_VALUE }
        val reminderItems = if (persistedItems.isNotEmpty()) {
            persistedItems.map { item -> ReminderNotificationItem(item.itemId, item.labelSnapshot) }
        } else {
            repository.checklistItemsByIds(itemIds)
                .sortedBy { order[it.id] ?: Int.MAX_VALUE }
                .map { item -> ReminderNotificationItem(item.id, item.label) }
        }
        if (reminderItems.isEmpty()) {
            repository.completeReminderEvent(eventId, itemIds)
            notifier.dismissReminder(eventId)
            return
        }
        val shown = notifier.showReminder(
            eventId = eventId,
            vehicleName = vehicle.name,
            trigger = event.trigger,
            reminderItems = reminderItems,
        )
        if (shown) {
            repository.updateReminderStatus(eventId, ReminderStatus.Pending)
        }
    }

    companion object {
        private const val ACTION_SNOOZE = "io.nodetech.mementocar.action.SNOOZE"
        private const val ACTION_REMIND_AGAIN = "io.nodetech.mementocar.action.REMIND_AGAIN"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_ITEM_ID = "item_id"

        fun intent(context: Context, eventId: Long, itemId: Long = -1): Intent {
            return Intent(context, SnoozeReceiver::class.java).apply {
                data = reminderUri("snooze", eventId, itemId)
                action = ACTION_SNOOZE
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_ITEM_ID, itemId)
            }
        }

        fun remindAgainIntent(context: Context, eventId: Long, itemId: Long): Intent {
            return Intent(context, SnoozeReceiver::class.java).apply {
                data = reminderUri("remind-again", eventId, itemId)
                action = ACTION_REMIND_AGAIN
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_ITEM_ID, itemId)
            }
        }
    }
}
