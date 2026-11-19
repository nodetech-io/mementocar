// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import io.nodetech.mementocar.domain.PendingReminderAlarm
import io.nodetech.mementocar.domain.PendingReminderAlarmCodec
import io.nodetech.mementocar.domain.PendingReminderAlarmKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingReminderAlarmStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("pending_reminder_alarms", Context.MODE_PRIVATE)
    private val lock = Any()

    fun saveDelayed(eventId: Long, itemIds: List<Long>, dueAtMillis: Long) {
        if (eventId <= 0L || itemIds.isEmpty() || dueAtMillis <= 0L) return
        save(
            PendingReminderAlarm(
                kind = PendingReminderAlarmKind.Delayed,
                eventId = eventId,
                itemIds = itemIds,
                itemId = -1L,
                dueAtMillis = dueAtMillis,
            ),
        )
    }

    fun saveSnoozed(eventId: Long, itemId: Long, dueAtMillis: Long) {
        if (eventId <= 0L || dueAtMillis <= 0L) return
        save(
            PendingReminderAlarm(
                kind = PendingReminderAlarmKind.Snoozed,
                eventId = eventId,
                itemIds = emptyList(),
                itemId = itemId,
                dueAtMillis = dueAtMillis,
            ),
        )
    }

    fun saveCriticalRepeat(
        eventId: Long,
        dueAtMillis: Long,
        repeatCount: Int,
        firstShownAtMillis: Long,
    ) {
        if (eventId <= 0L || dueAtMillis <= 0L || firstShownAtMillis <= 0L) return
        save(
            PendingReminderAlarm(
                kind = PendingReminderAlarmKind.CriticalRepeat,
                eventId = eventId,
                itemIds = emptyList(),
                itemId = -1L,
                dueAtMillis = dueAtMillis,
                repeatCount = repeatCount,
                firstShownAtMillis = firstShownAtMillis,
            ),
        )
    }

    fun all(): List<PendingReminderAlarm> {
        return synchronized(lock) {
            preferences.all.values
                .filterIsInstance<String>()
                .mapNotNull(PendingReminderAlarmCodec::decode)
                .sortedWith(compareBy<PendingReminderAlarm> { it.dueAtMillis }.thenBy { it.eventId })
        }
    }

    fun remove(kind: PendingReminderAlarmKind, eventId: Long) {
        if (kind == PendingReminderAlarmKind.Snoozed) {
            removeSnoozed(eventId)
            return
        }
        synchronized(lock) {
            preferences.edit(commit = true) {
                remove(key(kind, eventId))
            }
        }
    }

    fun remove(alarm: PendingReminderAlarm) {
        synchronized(lock) {
            preferences.edit(commit = true) {
                remove(key(alarm))
            }
        }
    }

    fun removeDelayed(eventId: Long) {
        remove(PendingReminderAlarmKind.Delayed, eventId)
    }

    fun removeSnoozed(eventId: Long) {
        synchronized(lock) {
            preferences.edit(commit = true) {
                preferences.all.keys
                    .filter { it.startsWith("${PendingReminderAlarmKind.Snoozed.name}:$eventId:") }
                    .forEach(::remove)
            }
        }
    }

    fun removeSnoozed(eventId: Long, itemId: Long) {
        synchronized(lock) {
            preferences.edit(commit = true) {
                remove(key(PendingReminderAlarmKind.Snoozed, eventId, itemId))
            }
        }
    }

    fun removeCriticalRepeat(eventId: Long) {
        remove(PendingReminderAlarmKind.CriticalRepeat, eventId)
    }

    @SuppressLint("UseKtx")
    fun clear(): Boolean {
        return synchronized(lock) {
            preferences.edit().clear().commit()
        }
    }

    private fun save(alarm: PendingReminderAlarm) {
        synchronized(lock) {
            preferences.edit(commit = true) {
                putString(key(alarm), PendingReminderAlarmCodec.encode(alarm))
            }
        }
    }

    private fun key(kind: PendingReminderAlarmKind, eventId: Long): String {
        return "${kind.name}:$eventId"
    }

    private fun key(kind: PendingReminderAlarmKind, eventId: Long, itemId: Long): String {
        return "${kind.name}:$eventId:$itemId"
    }

    private fun key(alarm: PendingReminderAlarm): String {
        return when (alarm.kind) {
            PendingReminderAlarmKind.Delayed -> key(alarm.kind, alarm.eventId)
            PendingReminderAlarmKind.Snoozed -> key(alarm.kind, alarm.eventId, alarm.itemId)
            PendingReminderAlarmKind.CriticalRepeat -> key(alarm.kind, alarm.eventId)
        }
    }

}
