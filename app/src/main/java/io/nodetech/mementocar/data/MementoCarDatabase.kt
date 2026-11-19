// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VehicleEntity::class,
        ChecklistItemEntity::class,
        ReminderEventEntity::class,
        ReminderEventItemEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class MementoCarDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun reminderEventItemDao(): ReminderEventItemDao
}
