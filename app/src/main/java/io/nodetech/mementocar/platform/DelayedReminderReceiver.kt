// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.domain.dueReminderItems
import io.nodetech.mementocar.domain.persistedEventReminderItems
import io.nodetech.mementocar.platform.ConnectionDiagnosticResult.NoDueReminders
import io.nodetech.mementocar.platform.ConnectionDiagnosticResult.NotificationsBlocked
import io.nodetech.mementocar.platform.ConnectionDiagnosticResult.ReminderShown
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DelayedReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MementoCarRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var diagnosticsStore: ConnectionDiagnosticsStore
    @Inject lateinit var alarmStore: PendingReminderAlarmStore
    @Inject lateinit var criticalReminderScheduler: CriticalReminderScheduler
    @Inject lateinit var operationCoordinator: ReminderOperationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return
        val itemIds = intent.getLongArrayExtra(EXTRA_ITEM_IDS)?.toList().orEmpty()
        if (itemIds.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                operationCoordinator.runExclusive {
                    if (showIfStillPending(eventId, itemIds)) {
                        alarmStore.removeDelayed(eventId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun showIfStillPending(eventId: Long, itemIds: List<Long>): Boolean {
        val event = repository.reminderEventById(eventId) ?: return true
        if (event.status != ReminderStatus.Pending) return true
        val vehicle = repository.vehicleById(event.vehicleId) ?: return true
        val order = itemIds.withIndex().associate { it.value to it.index }
        val checklist = repository.checklistItemsByIds(itemIds)
            .filter { it.active }
            .sortedBy { order[it.id] ?: Int.MAX_VALUE }
        if (checklist.isEmpty()) return true
        val persistedItems = repository.reminderEventItems(event.id)
        val dueItems = if (persistedItems.isEmpty()) {
            dueReminderItems(checklist = checklist, nowMillis = System.currentTimeMillis())
        } else {
            persistedEventReminderItems(checklist = checklist, nowMillis = System.currentTimeMillis())
        }
        val persistedPendingItems = persistedItems.filter { !it.completed }
        val persistedPendingItemsById = persistedPendingItems.associateBy { it.itemId }
        val persistedPendingItemIds = persistedPendingItemsById.keys
        val itemsToShow = if (persistedItems.isEmpty()) {
            dueItems
        } else {
            dueItems.filter { it.id in persistedPendingItemIds }
        }
        if (itemsToShow.isEmpty()) {
            if (persistedItems.isNotEmpty() && persistedPendingItems.isEmpty()) {
                repository.completeReminderEvent(event.id, emptyList())
                notifier.dismissReminder(event.id)
                return true
            }
            repository.updateReminderStatus(event.id, ReminderStatus.Dismissed)
            diagnosticsStore.record(event.vehicleId, event.trigger, NoDueReminders)
            return true
        }
        if (persistedItems.isEmpty()) {
            repository.replaceReminderEventItems(event.id, itemsToShow)
        }
        val shown = notifier.showReminder(
            eventId = event.id,
            vehicleName = vehicle.name,
            trigger = event.trigger,
            reminderItems = itemsToShow.map { item ->
                ReminderNotificationItem(
                    id = item.id,
                    label = persistedPendingItemsById[item.id]?.labelSnapshot ?: item.label,
                )
            },
        )
        if (shown) {
            val nowMillis = System.currentTimeMillis()
            repository.markChecklistItemsTriggered(
                itemIds = itemsToShow.map { it.id },
                triggeredAtMillis = nowMillis,
            )
            val hasCriticalItem = itemsToShow.any { item ->
                persistedPendingItemsById[item.id]?.critical ?: item.critical
            }
            if (hasCriticalItem) {
                criticalReminderScheduler.scheduleNext(
                    eventId = event.id,
                    repeatCount = 0,
                    firstShownAtMillis = nowMillis,
                )
            }
            diagnosticsStore.record(event.vehicleId, event.trigger, ReminderShown)
        } else {
            repository.updateReminderStatus(event.id, ReminderStatus.Dismissed)
            diagnosticsStore.record(event.vehicleId, event.trigger, NotificationsBlocked)
        }
        return true
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_ITEM_IDS = "item_ids"

        fun intent(context: Context, eventId: Long, itemIds: List<Long>): Intent {
            return Intent(context, DelayedReminderReceiver::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_ITEM_IDS, itemIds.toLongArray())
            }
        }
    }
}
