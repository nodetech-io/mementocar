// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DelayedReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmStore: PendingReminderAlarmStore,
) {
    fun schedule(eventId: Long, itemIds: List<Long>, delayMinutes: Int) {
        if (delayMinutes <= 0 || itemIds.isEmpty()) return
        scheduleAt(
            eventId = eventId,
            itemIds = itemIds,
            dueAtMillis = System.currentTimeMillis() + delayMinutes * MINUTE_MILLIS,
        )
    }

    fun scheduleAt(eventId: Long, itemIds: List<Long>, dueAtMillis: Long, persist: Boolean = true) {
        if (eventId <= 0L || itemIds.isEmpty() || dueAtMillis <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.requestCode("delayed"),
            DelayedReminderReceiver.intent(context, eventId, itemIds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val delayMillis = (dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
        if (persist) {
            alarmStore.saveDelayed(eventId, itemIds, dueAtMillis)
        }
    }

    fun cancel(eventId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.requestCode("delayed"),
            DelayedReminderReceiver.intent(context, eventId, emptyList()),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        alarmStore.removeDelayed(eventId)
    }

    private companion object {
        const val MINUTE_MILLIS = 60 * 1000L
    }
}

private fun Long.requestCode(suffix: String): Int {
    return (31 * ((this and 0x7fffffff).toInt()) + suffix.hashCode()) and 0x7fffffff
}
