// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import io.nodetech.mementocar.data.MementoCarRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Serializes destructive changes and their cleanup with notification delivery. */
@Singleton
class ReminderDeletionManager @Inject constructor(
    private val repository: MementoCarRepository,
    private val notifier: ReminderNotifier,
    private val diagnosticsStore: ConnectionDiagnosticsStore,
    private val operationCoordinator: ReminderOperationCoordinator,
) {
    suspend fun deleteVehicle(vehicleId: Long) {
        operationCoordinator.runExclusive {
            // Once deletion starts, finish cleanup even if the initiating screen closes.
            withContext(NonCancellable) {
                val eventIds = repository.deleteVehicle(vehicleId)
                eventIds.forEach(notifier::dismissReminder)
                diagnosticsStore.remove(vehicleId)
            }
        }
    }

    suspend fun deleteChecklistItem(itemId: Long): Boolean {
        return operationCoordinator.runExclusive {
            withContext(NonCancellable) {
                val result = repository.deleteChecklistItem(itemId) ?: return@withContext false
                result.updatedEventIds.forEach { eventId ->
                    notifier.dismissReminder(eventId, itemId)
                }
                result.dismissedEventIds.forEach(notifier::dismissReminder)
                true
            }
        }
    }
}
