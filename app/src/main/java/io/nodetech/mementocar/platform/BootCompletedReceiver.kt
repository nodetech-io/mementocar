// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.domain.PendingReminderAlarm
import io.nodetech.mementocar.domain.PendingReminderAlarmKind
import io.nodetech.mementocar.domain.PendingReminderAlarmRecoveryAction
import io.nodetech.mementocar.domain.pendingReminderAlarmRecoveryAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MementoCarRepository
    @Inject lateinit var alarmStore: PendingReminderAlarmStore
    @Inject lateinit var delayedReminderScheduler: DelayedReminderScheduler
    @Inject lateinit var snoozeReminderScheduler: SnoozeReminderScheduler
    @Inject lateinit var criticalReminderScheduler: CriticalReminderScheduler
    @Inject lateinit var operationCoordinator: ReminderOperationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                operationCoordinator.runExclusive {
                    recoverAlarms(context, System.currentTimeMillis())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun recoverAlarms(context: Context, nowMillis: Long) {
        alarmStore.all().forEach { alarm ->
            val event = repository.reminderEventById(alarm.eventId)
            when (
                pendingReminderAlarmRecoveryAction(
                    alarm = alarm,
                    eventStatus = event?.status,
                    nowMillis = nowMillis,
                )
            ) {
                PendingReminderAlarmRecoveryAction.Remove -> alarmStore.remove(alarm)
                PendingReminderAlarmRecoveryAction.FireNow -> context.sendBroadcast(alarm.intent(context))
                is PendingReminderAlarmRecoveryAction.Reschedule -> reschedule(alarm)
            }
        }
    }

    private fun reschedule(alarm: PendingReminderAlarm) {
        when (alarm.kind) {
            PendingReminderAlarmKind.Delayed -> delayedReminderScheduler.scheduleAt(
                eventId = alarm.eventId,
                itemIds = alarm.itemIds,
                dueAtMillis = alarm.dueAtMillis,
                persist = false,
            )
            PendingReminderAlarmKind.Snoozed -> snoozeReminderScheduler.scheduleAt(
                eventId = alarm.eventId,
                itemId = alarm.itemId,
                dueAtMillis = alarm.dueAtMillis,
                persist = false,
            )
            PendingReminderAlarmKind.CriticalRepeat -> criticalReminderScheduler.scheduleAt(
                eventId = alarm.eventId,
                dueAtMillis = alarm.dueAtMillis,
                repeatCount = alarm.repeatCount,
                firstShownAtMillis = alarm.firstShownAtMillis,
                persist = false,
            )
        }
    }

    private fun PendingReminderAlarm.intent(context: Context): Intent {
        return when (kind) {
            PendingReminderAlarmKind.Delayed -> DelayedReminderReceiver.intent(context, eventId, itemIds)
            PendingReminderAlarmKind.Snoozed -> SnoozeReceiver.remindAgainIntent(context, eventId, itemId)
            PendingReminderAlarmKind.CriticalRepeat -> CriticalReminderReceiver.intent(
                context = context,
                eventId = eventId,
                repeatCount = repeatCount,
                firstShownAtMillis = firstShownAtMillis,
            )
        }
    }
}
