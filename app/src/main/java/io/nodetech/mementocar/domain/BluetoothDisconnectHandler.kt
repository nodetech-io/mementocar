// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.platform.ConnectionDiagnosticResult
import io.nodetech.mementocar.platform.ConnectionDiagnosticsStore
import io.nodetech.mementocar.platform.CriticalReminderScheduler
import io.nodetech.mementocar.platform.DelayedReminderScheduler
import io.nodetech.mementocar.platform.NotificationDelaySettings
import io.nodetech.mementocar.platform.ReminderNotificationItem
import io.nodetech.mementocar.platform.ReminderNotifier
import io.nodetech.mementocar.platform.ReminderOperationCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothDisconnectHandler @Inject constructor(
    private val repository: MementoCarRepository,
    private val notifier: ReminderNotifier,
    private val notificationDelaySettings: NotificationDelaySettings,
    private val delayedReminderScheduler: DelayedReminderScheduler,
    private val criticalReminderScheduler: CriticalReminderScheduler,
    private val diagnosticsStore: ConnectionDiagnosticsStore,
    private val operationCoordinator: ReminderOperationCoordinator,
) {
    suspend fun handleEvent(
        bluetoothAddress: String,
        trigger: ReminderTrigger,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        return operationCoordinator.runExclusive {
            handleEventExclusive(bluetoothAddress, trigger, nowMillis)
        }
    }

    private suspend fun handleEventExclusive(
        bluetoothAddress: String,
        trigger: ReminderTrigger,
        nowMillis: Long,
    ): Boolean {
        val delayMinutes = notificationDelaySettings.currentBluetoothEventDelayMinutes()
        val action = repository.withTransaction {
            val vehicle = repository.findActiveVehicleForBluetooth(bluetoothAddress) ?: return@withTransaction null
            val dueItems = dueReminderItems(
                checklist = repository.activeChecklist(vehicle.id, trigger),
                nowMillis = nowMillis,
            )
            val cancelledDelayedEventIds = cancelUnshownDelayedOppositeEvents(vehicle.id, trigger)
            val recent = repository.recentEvent(vehicle.id, trigger, nowMillis - DEDUPE_WINDOW_MILLIS)
            if (!shouldCreateReminderForDisconnect(hasRecentEvent = recent != null)) {
                return@withTransaction ReminderAction(
                    vehicleId = vehicle.id,
                    cancelledDelayedEventIds = cancelledDelayedEventIds,
                    diagnosticResult = ConnectionDiagnosticResult.DuplicateIgnored,
                )
            }
            if (dueItems.isEmpty()) {
                return@withTransaction ReminderAction(
                    vehicleId = vehicle.id,
                    cancelledDelayedEventIds = cancelledDelayedEventIds,
                    diagnosticResult = ConnectionDiagnosticResult.NoDueReminders,
                )
            }
            val eventId = repository.createReminderEventWithItems(
                vehicleId = vehicle.id,
                trigger = trigger,
                disconnectedAtMillis = nowMillis,
                items = dueItems,
            )
            ReminderAction(
                vehicleId = vehicle.id,
                cancelledDelayedEventIds = cancelledDelayedEventIds,
                reminderToShow = ReminderToShow(
                    eventId = eventId,
                    vehicleName = vehicle.name,
                    items = dueItems,
                ),
            )
        } ?: return false
        action.cancelledDelayedEventIds.forEach { eventId ->
            delayedReminderScheduler.cancel(eventId)
        }
        val reminder = action.reminderToShow
        if (reminder == null) {
            action.diagnosticResult?.let { result ->
                diagnosticsStore.record(
                    vehicleId = action.vehicleId,
                    trigger = trigger,
                    result = result,
                    occurredAtMillis = nowMillis,
                )
            }
            return action.cancelledDelayedEventIds.isNotEmpty()
        }
        if (delayMinutes > 0) {
            delayedReminderScheduler.schedule(
                eventId = reminder.eventId,
                itemIds = reminder.items.map { it.id },
                delayMinutes = delayMinutes,
            )
            diagnosticsStore.record(
                vehicleId = action.vehicleId,
                trigger = trigger,
                result = ConnectionDiagnosticResult.ReminderScheduled,
                occurredAtMillis = nowMillis,
            )
        } else {
            val shown = notifier.showReminder(
                eventId = reminder.eventId,
                vehicleName = reminder.vehicleName,
                trigger = trigger,
                reminderItems = reminder.items.toNotificationItems(),
            )
            if (shown) {
                repository.markChecklistItemsTriggered(reminder.items.map { it.id }, nowMillis)
                scheduleCriticalRepeatIfNeeded(reminder.eventId, reminder.items, nowMillis)
                diagnosticsStore.record(
                    vehicleId = action.vehicleId,
                    trigger = trigger,
                    result = ConnectionDiagnosticResult.ReminderShown,
                    occurredAtMillis = nowMillis,
                )
            } else {
                repository.updateReminderStatus(reminder.eventId, ReminderStatus.Dismissed)
                diagnosticsStore.record(
                    vehicleId = action.vehicleId,
                    trigger = trigger,
                    result = ConnectionDiagnosticResult.NotificationsBlocked,
                    occurredAtMillis = nowMillis,
                )
                return action.cancelledDelayedEventIds.isNotEmpty()
            }
        }
        return true
    }

    private fun scheduleCriticalRepeatIfNeeded(
        eventId: Long,
        items: List<ChecklistItemEntity>,
        nowMillis: Long,
    ) {
        if (items.none { it.critical }) return
        criticalReminderScheduler.scheduleNext(
            eventId = eventId,
            repeatCount = 0,
            firstShownAtMillis = nowMillis,
        )
    }

    private suspend fun cancelUnshownDelayedOppositeEvents(
        vehicleId: Long,
        trigger: ReminderTrigger,
    ): List<Long> {
        val oppositeTrigger = when (trigger) {
            ReminderTrigger.Connect -> ReminderTrigger.Disconnect
            ReminderTrigger.Disconnect -> ReminderTrigger.Connect
        }
        return repository.pendingReminderEventsForVehicleTrigger(vehicleId, oppositeTrigger)
            .filter { event -> notifier.shownReminderIds(event.id).isEmpty() }
            .map { event ->
                repository.updateReminderStatus(event.id, ReminderStatus.Dismissed)
                event.id
            }
    }

    companion object {
        const val DEDUPE_WINDOW_MILLIS = 2 * 60 * 1000L
    }

    private data class ReminderToShow(
        val eventId: Long,
        val vehicleName: String,
        val items: List<ChecklistItemEntity>,
    )

    private data class ReminderAction(
        val vehicleId: Long,
        val cancelledDelayedEventIds: List<Long> = emptyList(),
        val reminderToShow: ReminderToShow? = null,
        val diagnosticResult: ConnectionDiagnosticResult? = null,
    )
}

private fun List<ChecklistItemEntity>.toNotificationItems(): List<ReminderNotificationItem> {
    return map { item -> ReminderNotificationItem(item.id, item.label) }
}
