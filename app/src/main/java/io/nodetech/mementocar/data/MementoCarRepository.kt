// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import androidx.room.withTransaction
import android.content.Context
import io.nodetech.mementocar.R
import io.nodetech.mementocar.domain.completedOneTimeReminderItemIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MementoCarRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MementoCarDatabase,
) {
    private val vehicleDao = database.vehicleDao()
    private val checklistDao = database.checklistDao()
    private val reminderEventDao = database.reminderEventDao()
    private val reminderEventItemDao = database.reminderEventItemDao()

    fun observeVehicles(): Flow<List<VehicleEntity>> = vehicleDao.observeVehicles()

    fun observeChecklist(vehicleId: Long): Flow<List<ChecklistItemEntity>> =
        checklistDao.observeActiveForVehicle(vehicleId)

    suspend fun createBackupDocument(
        bluetoothEventDelayMinutes: Int,
        exportedAtEpochMillis: Long,
    ): BackupDocumentV1 {
        return database.withTransaction {
            val remindersByVehicle = checklistDao.activeSnapshot().groupBy { it.vehicleId }
            BackupDocumentV1(
                format = BackupDocumentCodec.FORMAT,
                schemaVersion = BackupDocumentCodec.SCHEMA_VERSION,
                exportedAtEpochMillis = exportedAtEpochMillis,
                settings = BackupSettingsV1(bluetoothEventDelayMinutes),
                vehicles = vehicleDao.activeSnapshot().map { vehicle ->
                    BackupVehicleV1(
                        name = vehicle.name,
                        bluetoothIdHash = vehicle.bluetoothIdHash,
                        reminders = remindersByVehicle[vehicle.id].orEmpty().map { reminder ->
                            BackupReminderV1(
                                label = reminder.label,
                                trigger = reminder.trigger.toBackupTrigger(),
                                recurrence = reminder.recurrence.toBackupRecurrence(),
                                activeDaysMask = reminder.activeDaysMask,
                                critical = reminder.critical,
                                eligibleFromMillis = reminder.eligibleFromMillis,
                            )
                        },
                    )
                },
            )
        }
    }

    suspend fun replaceConfiguration(document: BackupDocumentV1) {
        BackupDocumentCodec.validate(document)
        database.withTransaction {
            vehicleDao.deleteAll()
            document.vehicles.forEach { backupVehicle ->
                val vehicleId = vehicleDao.insert(
                    VehicleEntity(
                        name = backupVehicle.name,
                        bluetoothIdHash = backupVehicle.bluetoothIdHash,
                    ),
                )
                backupVehicle.reminders.forEachIndexed { index, reminder ->
                    checklistDao.insert(
                        ChecklistItemEntity(
                            vehicleId = vehicleId,
                            label = reminder.label,
                            sortOrder = index,
                            trigger = reminder.trigger.toReminderTrigger(),
                            recurrence = reminder.recurrence.toReminderRecurrence(),
                            activeDaysMask = reminder.activeDaysMask,
                            eligibleFromMillis = reminder.eligibleFromMillis,
                            critical = reminder.critical,
                        ),
                    )
                }
            }
        }
    }

    fun observeOpenReminderEvents(): Flow<List<ReminderEventEntity>> {
        return reminderEventDao.observeWithStatuses(listOf(ReminderStatus.Pending, ReminderStatus.Snoozed))
    }

    fun observeReminderEventItems(): Flow<List<ReminderEventItemEntity>> {
        return reminderEventItemDao.observeAll()
    }

    suspend fun addVehicle(name: String, bluetoothAddress: String): Long {
        val bluetoothHash = stableHash(bluetoothAddress)
        vehicleDao.findByBluetoothHash(bluetoothHash)?.let { existing ->
            if (!existing.active) {
                vehicleDao.update(existing.copy(name = name.ifBlank { existing.name }, active = true))
            }
            return existing.id
        }
        return vehicleDao.insert(
            VehicleEntity(
                name = name.ifBlank { context.getString(R.string.default_car_name) },
                bluetoothIdHash = bluetoothHash,
            ),
        )
    }

    suspend fun deleteVehicle(vehicleId: Long): List<Long> {
        return database.withTransaction {
            val vehicle = vehicleDao.findById(vehicleId) ?: return@withTransaction emptyList()
            val eventIds = reminderEventDao.idsForVehicle(vehicleId)
            vehicleDao.delete(vehicle)
            eventIds
        }
    }

    suspend fun addChecklistItem(
        vehicleId: Long,
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        eligibleFromMillis: Long? = null,
        critical: Boolean = false,
    ): Long {
        val cleanLabel = label.trim()
        val cleanActiveDaysMask = activeDaysMask and ActiveDays.All
        require(cleanLabel.isNotEmpty()) { "Checklist item cannot be blank" }
        require(cleanLabel.length <= DataLimits.ReminderLabelMaxChars) {
            "Checklist item exceeds ${DataLimits.ReminderLabelMaxChars} characters"
        }
        require(cleanActiveDaysMask != 0) { "Checklist item must be active at least one day" }
        validateEligibility(recurrence, eligibleFromMillis)
        return checklistDao.insert(
            ChecklistItemEntity(
                vehicleId = vehicleId,
                label = cleanLabel,
                sortOrder = checklistDao.nextSortOrder(vehicleId),
                trigger = trigger,
                recurrence = recurrence,
                activeDaysMask = cleanActiveDaysMask,
                eligibleFromMillis = eligibleFromMillis,
                critical = critical,
            ),
        )
    }

    suspend fun addChecklistItems(
        vehicleIds: List<Long>,
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        eligibleFromMillis: Long? = null,
        critical: Boolean = false,
    ): Int {
        val targetVehicleIds = vehicleIds.distinct()
        require(targetVehicleIds.isNotEmpty()) { "At least one vehicle must be selected" }
        return database.withTransaction {
            targetVehicleIds.forEach { vehicleId ->
                require(vehicleDao.findById(vehicleId)?.active == true) {
                    "Vehicle $vehicleId is not active"
                }
            }
            targetVehicleIds.forEach { vehicleId ->
                addChecklistItem(
                    vehicleId = vehicleId,
                    label = label,
                    trigger = trigger,
                    recurrence = recurrence,
                    activeDaysMask = activeDaysMask,
                    eligibleFromMillis = eligibleFromMillis,
                    critical = critical,
                )
            }
            targetVehicleIds.size
        }
    }

    suspend fun updateChecklistItem(
        itemId: Long,
        vehicleIds: List<Long>,
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        eligibleFromMillis: Long? = null,
        critical: Boolean = false,
    ): Int {
        val cleanLabel = label.trim()
        val cleanActiveDaysMask = activeDaysMask and ActiveDays.All
        require(cleanLabel.isNotEmpty()) { "Checklist item cannot be blank" }
        require(cleanLabel.length <= DataLimits.ReminderLabelMaxChars) {
            "Checklist item exceeds ${DataLimits.ReminderLabelMaxChars} characters"
        }
        require(cleanActiveDaysMask != 0) { "Checklist item must be active at least one day" }
        validateEligibility(recurrence, eligibleFromMillis)
        val item = checklistDao.findById(itemId) ?: return 0
        val targetVehicleIds = vehicleIds.distinct().ifEmpty { listOf(item.vehicleId) }
        return database.withTransaction {
            targetVehicleIds.forEach { vehicleId ->
                val targetItem = if (vehicleId == item.vehicleId) {
                    item
                } else {
                    checklistDao.findActiveForVehicleByLabel(vehicleId, item.label)
                }
                if (targetItem == null) {
                    checklistDao.insert(
                        ChecklistItemEntity(
                            vehicleId = vehicleId,
                            label = cleanLabel,
                            sortOrder = checklistDao.nextSortOrder(vehicleId),
                            trigger = trigger,
                            recurrence = recurrence,
                            activeDaysMask = cleanActiveDaysMask,
                            eligibleFromMillis = eligibleFromMillis,
                            critical = critical,
                        ),
                    )
                } else {
                    checklistDao.update(
                        targetItem.copy(
                            lastTriggeredAtMillis = if (
                                targetItem.recurrence != recurrence ||
                                targetItem.trigger != trigger ||
                                targetItem.activeDaysMask != cleanActiveDaysMask ||
                                targetItem.eligibleFromMillis != eligibleFromMillis
                            ) null else targetItem.lastTriggeredAtMillis,
                            label = cleanLabel,
                            trigger = trigger,
                            recurrence = recurrence,
                            activeDaysMask = cleanActiveDaysMask,
                            eligibleFromMillis = eligibleFromMillis,
                            critical = critical,
                        ),
                    )
                }
            }
            targetVehicleIds.size
        }
    }

    suspend fun deleteChecklistItem(itemId: Long): ChecklistDeletionResult? {
        val item = checklistDao.findById(itemId) ?: return null
        if (!item.active) return null
        return database.withTransaction {
            val affectedEventIds = reminderEventItemDao.eventIdsForItem(itemId)
            reminderEventItemDao.deleteForItem(itemId)
            val dismissedEventIds = affectedEventIds.filter { eventId ->
                if (reminderEventItemDao.pendingCountForEvent(eventId) == 0) {
                    updateReminderStatus(eventId, ReminderStatus.Dismissed)
                    true
                } else {
                    false
                }
            }
            checklistDao.delete(item)
            ChecklistDeletionResult(
                updatedEventIds = affectedEventIds - dismissedEventIds.toSet(),
                dismissedEventIds = dismissedEventIds,
            )
        }
    }

    suspend fun findActiveVehicleForBluetooth(bluetoothAddress: String): VehicleEntity? {
        return vehicleDao.findActiveByBluetoothHash(stableHash(bluetoothAddress))
    }

    suspend fun activeChecklist(
        vehicleId: Long,
        trigger: ReminderTrigger,
    ): List<ChecklistItemEntity> {
        return checklistDao.activeForVehicleAndTrigger(vehicleId, trigger)
    }

    suspend fun checklistItemsByIds(itemIds: List<Long>): List<ChecklistItemEntity> {
        if (itemIds.isEmpty()) return emptyList()
        return checklistDao.findByIds(itemIds)
    }

    suspend fun deactivateCompletedOneTimeChecklistItems(itemIds: Collection<Long>) {
        if (itemIds.isEmpty()) return
        val uniqueIds = itemIds.distinct()
        val oneTimeItemIds = completedOneTimeReminderItemIds(
            checklist = checklistDao.findByIds(uniqueIds),
            completedItemIds = uniqueIds.toSet(),
        )
        oneTimeItemIds.forEach { itemId ->
            val item = checklistDao.findById(itemId) ?: return@forEach
            if (item.active) {
                checklistDao.update(item.copy(active = false))
            }
        }
    }

    suspend fun vehicleById(vehicleId: Long): VehicleEntity? {
        return vehicleDao.findById(vehicleId)
    }

    fun vehicleHasBluetooth(vehicle: VehicleEntity): Boolean {
        return vehicle.bluetoothIdHash != stableHash(ManualVehicleBluetoothId)
    }

    suspend fun recentEvent(
        vehicleId: Long,
        trigger: ReminderTrigger,
        sinceMillis: Long,
    ): ReminderEventEntity? {
        return reminderEventDao.recentForVehicle(
            vehicleId = vehicleId,
            trigger = trigger,
            sinceMillis = sinceMillis,
            statuses = listOf(ReminderStatus.Pending, ReminderStatus.Snoozed),
        )
    }

    suspend fun reminderEventById(eventId: Long): ReminderEventEntity? {
        return reminderEventDao.findById(eventId)
    }

    suspend fun pendingReminderEventsForVehicleTrigger(
        vehicleId: Long,
        trigger: ReminderTrigger,
    ): List<ReminderEventEntity> {
        return reminderEventDao.forVehicleTriggerWithStatuses(
            vehicleId = vehicleId,
            trigger = trigger,
            statuses = listOf(ReminderStatus.Pending),
        )
    }

    suspend fun createReminderEvent(
        vehicleId: Long,
        trigger: ReminderTrigger,
        disconnectedAtMillis: Long,
    ): Long {
        return reminderEventDao.insert(
            ReminderEventEntity(
                vehicleId = vehicleId,
                trigger = trigger,
                disconnectedAtMillis = disconnectedAtMillis,
            ),
        )
    }

    suspend fun createReminderEventWithItems(
        vehicleId: Long,
        trigger: ReminderTrigger,
        disconnectedAtMillis: Long,
        items: List<ChecklistItemEntity>,
    ): Long {
        return database.withTransaction {
            val eventId = createReminderEvent(vehicleId, trigger, disconnectedAtMillis)
            saveReminderEventItems(eventId, items)
            eventId
        }
    }

    suspend fun saveReminderEventItems(eventId: Long, items: List<ChecklistItemEntity>) {
        if (eventId <= 0L || items.isEmpty()) return
        reminderEventItemDao.insertAll(
            items.mapIndexed { index, item ->
                ReminderEventItemEntity(
                    eventId = eventId,
                    itemId = item.id,
                    labelSnapshot = item.label,
                    sortOrder = index,
                    critical = item.critical,
                )
            },
        )
    }

    suspend fun replaceReminderEventItems(eventId: Long, items: List<ChecklistItemEntity>) {
        if (eventId <= 0L) return
        database.withTransaction {
            reminderEventItemDao.deleteForEvent(eventId)
            saveReminderEventItems(eventId, items)
        }
    }

    suspend fun reminderEventItems(eventId: Long): List<ReminderEventItemEntity> {
        return reminderEventItemDao.forEvent(eventId)
    }

    suspend fun pendingReminderEventItems(eventId: Long): List<ReminderEventItemEntity> {
        return reminderEventItemDao.pendingForEvent(eventId)
    }

    suspend fun markChecklistItemsTriggered(itemIds: List<Long>, triggeredAtMillis: Long) {
        if (itemIds.isEmpty()) return
        checklistDao.findByIds(itemIds).forEach { item ->
            checklistDao.update(item.copy(lastTriggeredAtMillis = triggeredAtMillis))
        }
    }

    suspend fun updateReminderStatus(eventId: Long, status: ReminderStatus) {
        val event = reminderEventDao.findById(eventId) ?: return
        val resolvedAtMillis = when (status) {
            ReminderStatus.Completed,
            ReminderStatus.Dismissed -> System.currentTimeMillis()
            ReminderStatus.Pending,
            ReminderStatus.Snoozed -> null
        }
        reminderEventDao.update(
            event.copy(
                status = status,
                resolvedAtMillis = resolvedAtMillis,
            ),
        )
    }

    suspend fun completeReminderEvent(eventId: Long, completedItemIds: Collection<Long>) {
        database.withTransaction {
            val eventItems = reminderEventItemDao.forEvent(eventId)
            val idsToDeactivate = if (eventItems.isEmpty()) {
                completedItemIds
            } else {
                reminderEventItemDao.markAllCompleted(eventId, System.currentTimeMillis())
                eventItems.map { it.itemId }
            }
            updateReminderStatus(eventId, ReminderStatus.Completed)
            deactivateCompletedOneTimeChecklistItems(idsToDeactivate)
        }
    }

    suspend fun completeReminderEventItem(eventId: Long, itemId: Long): ReminderEventItemCompletion {
        return database.withTransaction {
            val completedAtMillis = System.currentTimeMillis()
            val updated = reminderEventItemDao.markCompleted(eventId, itemId, completedAtMillis)
            if (updated == 0) return@withTransaction ReminderEventItemCompletion.NoPersistedItem
            deactivateCompletedOneTimeChecklistItems(listOf(itemId))
            val pendingItems = reminderEventItemDao.pendingForEvent(eventId)
            if (pendingItems.isEmpty()) {
                updateReminderStatus(eventId, ReminderStatus.Completed)
                ReminderEventItemCompletion.EventCompleted
            } else {
                ReminderEventItemCompletion.ItemCompleted
            }
        }
    }

    suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction(block)
    }

    private companion object {
        const val ManualVehicleBluetoothId = "mementocar_manual_vehicle"
    }
}

enum class ReminderEventItemCompletion {
    NoPersistedItem,
    ItemCompleted,
    EventCompleted,
}

data class ChecklistDeletionResult(
    val updatedEventIds: List<Long>,
    val dismissedEventIds: List<Long>,
)

private fun validateEligibility(recurrence: ReminderRecurrence, eligibleFromMillis: Long?) {
    require(eligibleFromMillis == null || eligibleFromMillis > 0L) {
        "Reminder eligibility must be a positive timestamp"
    }
    require(eligibleFromMillis == null || recurrence == ReminderRecurrence.Once) {
        "Only one-time reminders can be scheduled for a future trip"
    }
}
