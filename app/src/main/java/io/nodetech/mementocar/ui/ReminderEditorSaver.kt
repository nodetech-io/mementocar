// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.runtime.saveable.listSaver
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderTrigger

/** Keep the editor's baseline independent of asynchronous database reloads. */
internal val ReminderEditorSaver = listSaver<ChecklistItemEntity?, Any?>(
    save = { item ->
        if (item == null) emptyList() else listOf(
            item.id, item.vehicleId, item.label, item.active, item.sortOrder,
            item.trigger.name, item.recurrence.name, item.activeDaysMask,
            item.eligibleFromMillis, item.lastTriggeredAtMillis, item.critical,
        )
    },
    restore = { values ->
        if (values.isEmpty()) null else ChecklistItemEntity(
            id = values[0] as Long,
            vehicleId = values[1] as Long,
            label = values[2] as String,
            active = values[3] as Boolean,
            sortOrder = values[4] as Int,
            trigger = ReminderTrigger.valueOf(values[5] as String),
            recurrence = ReminderRecurrence.valueOf(values[6] as String),
            activeDaysMask = values[7] as Int,
            eligibleFromMillis = values[8] as Long?,
            lastTriggeredAtMillis = values[9] as Long?,
            critical = values[10] as Boolean,
        )
    },
)
