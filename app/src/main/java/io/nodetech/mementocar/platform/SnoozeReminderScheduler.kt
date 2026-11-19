// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnoozeReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmStore: PendingReminderAlarmStore,
) {
    fun schedule(eventId: Long, itemId: Long, delayMillis: Long) {
        if (delayMillis <= 0L) return
        scheduleAt(
            eventId = eventId,
            itemId = itemId,
            dueAtMillis = System.currentTimeMillis() + delayMillis,
        )
    }

    fun scheduleAt(eventId: Long, itemId: Long, dueAtMillis: Long, persist: Boolean = true) {
        if (eventId <= 0L || dueAtMillis <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            SnoozeReceiver.remindAgainIntent(context, eventId, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val delayMillis = (dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        // Inexact: allowed during Doze, but delivery may still be deferred by Android.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            pendingIntent,
        )
        if (persist) {
            alarmStore.saveSnoozed(eventId, itemId, dueAtMillis)
        }
    }

    fun cancel(eventId: Long, itemId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            SnoozeReceiver.remindAgainIntent(context, eventId, itemId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        alarmStore.removeSnoozed(eventId, itemId)
    }
}
