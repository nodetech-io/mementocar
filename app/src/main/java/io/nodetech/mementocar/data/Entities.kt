// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bluetoothIdHash: String,
    val active: Boolean = true,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val label: String,
    val active: Boolean = true,
    val sortOrder: Int,
    val trigger: ReminderTrigger = ReminderTrigger.Disconnect,
    val recurrence: ReminderRecurrence = ReminderRecurrence.EveryTrigger,
    val activeDaysMask: Int = ActiveDays.All,
    val eligibleFromMillis: Long? = null,
    val lastTriggeredAtMillis: Long? = null,
    val critical: Boolean = false,
)

@Entity(
    tableName = "reminder_events",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val disconnectedAtMillis: Long,
    val trigger: ReminderTrigger = ReminderTrigger.Disconnect,
    val status: ReminderStatus = ReminderStatus.Pending,
    val resolvedAtMillis: Long? = null,
)

@Entity(
    tableName = "reminder_event_items",
    primaryKeys = ["eventId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = ReminderEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId")],
)
data class ReminderEventItemEntity(
    val eventId: Long,
    val itemId: Long,
    val labelSnapshot: String,
    val sortOrder: Int,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val critical: Boolean = false,
)

enum class ReminderTrigger {
    Connect,
    Disconnect,
}

enum class ReminderRecurrence {
    Once,
    EveryTrigger,
    Daily,
    Weekly,
    Monthly,
}

object ActiveDays {
    const val Monday = 1 shl 0
    const val Tuesday = 1 shl 1
    const val Wednesday = 1 shl 2
    const val Thursday = 1 shl 3
    const val Friday = 1 shl 4
    const val Saturday = 1 shl 5
    const val Sunday = 1 shl 6

    const val Weekdays = Monday or Tuesday or Wednesday or Thursday or Friday
    const val Weekend = Saturday or Sunday
    const val All = Weekdays or Weekend
}

object DataLimits {
    const val ReminderLabelMaxChars = 200
}

enum class ReminderStatus {
    Pending,
    Completed,
    Snoozed,
    Dismissed,
}
