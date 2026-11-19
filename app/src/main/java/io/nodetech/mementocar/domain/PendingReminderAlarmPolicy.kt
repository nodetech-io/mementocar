// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderStatus

enum class PendingReminderAlarmKind {
    Delayed,
    Snoozed,
    CriticalRepeat,
}

data class PendingReminderAlarm(
    val kind: PendingReminderAlarmKind,
    val eventId: Long,
    val itemIds: List<Long>,
    val itemId: Long,
    val dueAtMillis: Long,
    val repeatCount: Int = 0,
    val firstShownAtMillis: Long = 0L,
)

object PendingReminderAlarmCodec {
    fun encode(alarm: PendingReminderAlarm): String {
        return listOf(
            VERSION,
            alarm.kind.name,
            alarm.eventId.toString(),
            alarm.dueAtMillis.toString(),
            alarm.itemId.toString(),
            alarm.itemIds.joinToString(","),
            alarm.repeatCount.toString(),
            alarm.firstShownAtMillis.toString(),
        ).joinToString("|")
    }

    fun decode(value: String): PendingReminderAlarm? {
        val parts = value.split("|", limit = 8)
        if (parts.isEmpty()) return null
        if (parts.size != 8 || parts[0] != VERSION) return null
        val kind = parts[1].toAlarmKind() ?: return null
        val eventId = parts[2].toLongOrNull() ?: return null
        val dueAtMillis = parts[3].toLongOrNull() ?: return null
        val itemId = parts[4].toLongOrNull() ?: return null
        val itemIds = parts[5]
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        val repeatCount = parts[6].toIntOrNull() ?: return null
        val firstShownAtMillis = parts[7].toLongOrNull() ?: return null
        return PendingReminderAlarm(
            kind = kind,
            eventId = eventId,
            itemIds = itemIds,
            itemId = itemId,
            dueAtMillis = dueAtMillis,
            repeatCount = repeatCount,
            firstShownAtMillis = firstShownAtMillis,
        )
    }

    private fun String.toAlarmKind(): PendingReminderAlarmKind? {
        return PendingReminderAlarmKind.values().firstOrNull { it.name == this }
    }

    private const val VERSION = "2"
}

sealed interface PendingReminderAlarmRecoveryAction {
    data object Remove : PendingReminderAlarmRecoveryAction
    data object FireNow : PendingReminderAlarmRecoveryAction
    data class Reschedule(val dueAtMillis: Long) : PendingReminderAlarmRecoveryAction
}

fun pendingReminderAlarmRecoveryAction(
    alarm: PendingReminderAlarm,
    eventStatus: ReminderStatus?,
    nowMillis: Long,
): PendingReminderAlarmRecoveryAction {
    if (alarm.eventId <= 0L || alarm.dueAtMillis <= 0L || eventStatus == null) {
        return PendingReminderAlarmRecoveryAction.Remove
    }
    val hasPayload = when (alarm.kind) {
        PendingReminderAlarmKind.Delayed -> alarm.itemIds.isNotEmpty()
        PendingReminderAlarmKind.Snoozed -> true
        PendingReminderAlarmKind.CriticalRepeat -> alarm.firstShownAtMillis > 0L && alarm.repeatCount >= 0
    }
    if (!hasPayload) return PendingReminderAlarmRecoveryAction.Remove

    val isRecoverable = when (alarm.kind) {
        PendingReminderAlarmKind.Delayed,
        PendingReminderAlarmKind.CriticalRepeat -> eventStatus == ReminderStatus.Pending
        // An individual snooze leaves the event pending so its other items can still alert.
        // Keep this aligned with snoozedReminderAction, including whole-event snoozes.
        PendingReminderAlarmKind.Snoozed -> eventStatus == ReminderStatus.Snoozed ||
            (eventStatus == ReminderStatus.Pending && alarm.itemId > 0L)
    }
    if (!isRecoverable) return PendingReminderAlarmRecoveryAction.Remove

    return if (alarm.dueAtMillis <= nowMillis) {
        PendingReminderAlarmRecoveryAction.FireNow
    } else {
        PendingReminderAlarmRecoveryAction.Reschedule(alarm.dueAtMillis)
    }
}
