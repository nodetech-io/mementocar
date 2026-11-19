// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE active = 1 ORDER BY name, id")
    suspend fun activeSnapshot(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE active = 1 ORDER BY name")
    fun observeVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE active = 1 AND bluetoothIdHash = :bluetoothIdHash LIMIT 1")
    suspend fun findActiveByBluetoothHash(bluetoothIdHash: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE bluetoothIdHash = :bluetoothIdHash LIMIT 1")
    suspend fun findByBluetoothHash(bluetoothIdHash: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE active = 1 ORDER BY vehicleId, sortOrder, id")
    suspend fun activeSnapshot(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE vehicleId = :vehicleId AND active = 1 ORDER BY sortOrder")
    fun observeActiveForVehicle(vehicleId: Long): Flow<List<ChecklistItemEntity>>

    @Query(
        """
        SELECT * FROM checklist_items
        WHERE vehicleId = :vehicleId AND active = 1 AND trigger = :trigger
        ORDER BY sortOrder
        """,
    )
    suspend fun activeForVehicleAndTrigger(
        vehicleId: Long,
        trigger: ReminderTrigger,
    ): List<ChecklistItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM checklist_items WHERE vehicleId = :vehicleId")
    suspend fun nextSortOrder(vehicleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity): Long

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Delete
    suspend fun delete(item: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_items WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ChecklistItemEntity?

    @Query(
        """
        SELECT * FROM checklist_items
        WHERE vehicleId = :vehicleId AND active = 1 AND label = :label
        LIMIT 1
        """,
    )
    suspend fun findActiveForVehicleByLabel(vehicleId: Long, label: String): ChecklistItemEntity?
}

@Dao
interface ReminderEventDao {
    @Query(
        """
        SELECT * FROM reminder_events
        WHERE status IN (:statuses)
        ORDER BY disconnectedAtMillis DESC
        """,
    )
    fun observeWithStatuses(statuses: List<ReminderStatus>): Flow<List<ReminderEventEntity>>

    @Query(
        """
        SELECT * FROM reminder_events
        WHERE vehicleId = :vehicleId
            AND trigger = :trigger
            AND disconnectedAtMillis >= :sinceMillis
            AND status IN (:statuses)
        ORDER BY disconnectedAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun recentForVehicle(
        vehicleId: Long,
        trigger: ReminderTrigger,
        sinceMillis: Long,
        statuses: List<ReminderStatus>,
    ): ReminderEventEntity?

    @Query("SELECT * FROM reminder_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReminderEventEntity?

    @Query("SELECT id FROM reminder_events WHERE vehicleId = :vehicleId")
    suspend fun idsForVehicle(vehicleId: Long): List<Long>

    @Query(
        """
        SELECT * FROM reminder_events
        WHERE vehicleId = :vehicleId AND trigger = :trigger AND status IN (:statuses)
        ORDER BY disconnectedAtMillis DESC
        """,
    )
    suspend fun forVehicleTriggerWithStatuses(
        vehicleId: Long,
        trigger: ReminderTrigger,
        statuses: List<ReminderStatus>,
    ): List<ReminderEventEntity>

    @Insert
    suspend fun insert(event: ReminderEventEntity): Long

    @Update
    suspend fun update(event: ReminderEventEntity)
}

@Dao
interface ReminderEventItemDao {
    @Query("SELECT * FROM reminder_event_items ORDER BY eventId, sortOrder")
    fun observeAll(): Flow<List<ReminderEventItemEntity>>

    @Query(
        """
        SELECT * FROM reminder_event_items
        WHERE eventId = :eventId
        ORDER BY sortOrder
        """,
    )
    suspend fun forEvent(eventId: Long): List<ReminderEventItemEntity>

    @Query(
        """
        SELECT * FROM reminder_event_items
        WHERE eventId = :eventId AND completed = 0
        ORDER BY sortOrder
        """,
    )
    suspend fun pendingForEvent(eventId: Long): List<ReminderEventItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReminderEventItemEntity>)

    @Query("DELETE FROM reminder_event_items WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: Long)

    @Query("SELECT DISTINCT eventId FROM reminder_event_items WHERE itemId = :itemId")
    suspend fun eventIdsForItem(itemId: Long): List<Long>

    @Query("DELETE FROM reminder_event_items WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("SELECT COUNT(*) FROM reminder_event_items WHERE eventId = :eventId AND completed = 0")
    suspend fun pendingCountForEvent(eventId: Long): Int

    @Query(
        """
        UPDATE reminder_event_items
        SET completed = 1, completedAtMillis = :completedAtMillis
        WHERE eventId = :eventId AND itemId = :itemId
        """,
    )
    suspend fun markCompleted(eventId: Long, itemId: Long, completedAtMillis: Long): Int

    @Query(
        """
        UPDATE reminder_event_items
        SET completed = 1, completedAtMillis = :completedAtMillis
        WHERE eventId = :eventId AND completed = 0
        """,
    )
    suspend fun markAllCompleted(eventId: Long, completedAtMillis: Long): Int
}
