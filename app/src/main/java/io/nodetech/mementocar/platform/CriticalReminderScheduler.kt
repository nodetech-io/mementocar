// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import io.nodetech.mementocar.domain.CRITICAL_REPEAT_INTERVAL_MILLIS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CriticalReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmStore: PendingReminderAlarmStore,
) {
    fun scheduleNext(eventId: Long, repeatCount: Int, firstShownAtMillis: Long) {
        if (eventId <= 0L || firstShownAtMillis <= 0L) return
        scheduleAt(
            eventId = eventId,
            dueAtMillis = System.currentTimeMillis() + CRITICAL_REPEAT_INTERVAL_MILLIS,
            repeatCount = repeatCount,
            firstShownAtMillis = firstShownAtMillis,
        )
    }

    fun scheduleAt(
        eventId: Long,
        dueAtMillis: Long,
        repeatCount: Int,
        firstShownAtMillis: Long,
        persist: Boolean = true,
    ) {
        if (eventId <= 0L || dueAtMillis <= 0L || firstShownAtMillis <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.criticalRepeatRequestCode(),
            CriticalReminderReceiver.intent(context, eventId, repeatCount, firstShownAtMillis),
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
            alarmStore.saveCriticalRepeat(eventId, dueAtMillis, repeatCount, firstShownAtMillis)
        }
    }

    fun cancel(eventId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.criticalRepeatRequestCode(),
            CriticalReminderReceiver.intent(context, eventId, repeatCount = 0, firstShownAtMillis = 1L),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        alarmStore.removeCriticalRepeat(eventId)
    }
}

private fun Long.criticalRepeatRequestCode(): Int {
    return (31 * ((this and 0x7fffffff).toInt()) + "critical_repeat".hashCode()) and 0x7fffffff
}
