// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingReminderAlarmPolicyTest {
    @Test
    fun codecRoundTripsDelayedAlarm() {
        val alarm = delayedAlarm()

        val decoded = PendingReminderAlarmCodec.decode(PendingReminderAlarmCodec.encode(alarm))

        assertEquals(alarm, decoded)
    }

    @Test
    fun codecRoundTripsSnoozedAlarm() {
        val alarm = snoozedAlarm()

        val decoded = PendingReminderAlarmCodec.decode(PendingReminderAlarmCodec.encode(alarm))

        assertEquals(alarm, decoded)
    }

    @Test
    fun codecRoundTripsCriticalRepeatAlarm() {
        val alarm = criticalRepeatAlarm()

        val decoded = PendingReminderAlarmCodec.decode(PendingReminderAlarmCodec.encode(alarm))

        assertEquals(alarm, decoded)
    }

    @Test
    fun codecRejectsInvalidPayload() {
        val decoded = PendingReminderAlarmCodec.decode("2|Delayed|1|1000|-1|10")

        assertEquals(null, decoded)
    }

    @Test
    fun removesAlarmWhenEventDoesNotExist() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = delayedAlarm(),
            eventStatus = null,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    @Test
    fun removesDelayedAlarmWhenEventIsNotPending() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = delayedAlarm(),
            eventStatus = ReminderStatus.Completed,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    @Test
    fun removesDelayedAlarmWithoutItemIds() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = delayedAlarm(itemIds = emptyList()),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    @Test
    fun firesDueDelayedAlarm() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = delayedAlarm(dueAtMillis = NOW),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.FireNow, action)
    }

    @Test
    fun reschedulesFutureDelayedAlarm() {
        val dueAtMillis = NOW + 60_000L

        val action = pendingReminderAlarmRecoveryAction(
            alarm = delayedAlarm(dueAtMillis = dueAtMillis),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Reschedule(dueAtMillis), action)
    }

    @Test
    fun removesWholeEventSnoozeWhenEventIsNotSnoozed() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = snoozedAlarm().copy(itemId = -1L),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    @Test
    fun reschedulesIndividualSnoozeWhileEventRemainsPending() {
        val alarm = snoozedAlarm()
        val action = pendingReminderAlarmRecoveryAction(alarm, ReminderStatus.Pending, NOW)
        assertEquals(PendingReminderAlarmRecoveryAction.Reschedule(alarm.dueAtMillis), action)
    }

    @Test
    fun firesOverdueIndividualSnoozeWhileEventRemainsPending() {
        val action = pendingReminderAlarmRecoveryAction(
            snoozedAlarm(dueAtMillis = NOW - 1L), ReminderStatus.Pending, NOW,
        )
        assertEquals(PendingReminderAlarmRecoveryAction.FireNow, action)
    }

    @Test
    fun removesIndividualSnoozeForFinishedEvents() {
        for (status in listOf(ReminderStatus.Completed, ReminderStatus.Dismissed)) {
            assertEquals(
                PendingReminderAlarmRecoveryAction.Remove,
                pendingReminderAlarmRecoveryAction(snoozedAlarm(), status, NOW),
            )
        }
    }

    @Test
    fun firesDueSnoozedAlarm() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = snoozedAlarm(dueAtMillis = NOW - 1L),
            eventStatus = ReminderStatus.Snoozed,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.FireNow, action)
    }

    @Test
    fun firesDueCriticalRepeatAlarm() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = criticalRepeatAlarm(dueAtMillis = NOW - 1L),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.FireNow, action)
    }

    @Test
    fun reschedulesFutureCriticalRepeatAlarm() {
        val dueAtMillis = NOW + 60_000L

        val action = pendingReminderAlarmRecoveryAction(
            alarm = criticalRepeatAlarm(dueAtMillis = dueAtMillis),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Reschedule(dueAtMillis), action)
    }

    @Test
    fun removesCriticalRepeatAlarmWhenEventIsNotPending() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = criticalRepeatAlarm(),
            eventStatus = ReminderStatus.Snoozed,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    @Test
    fun removesCriticalRepeatAlarmWithoutFirstShownTime() {
        val action = pendingReminderAlarmRecoveryAction(
            alarm = criticalRepeatAlarm(firstShownAtMillis = 0L),
            eventStatus = ReminderStatus.Pending,
            nowMillis = NOW,
        )

        assertEquals(PendingReminderAlarmRecoveryAction.Remove, action)
    }

    private fun delayedAlarm(
        itemIds: List<Long> = listOf(10L, 11L),
        dueAtMillis: Long = NOW + 10_000L,
    ): PendingReminderAlarm {
        return PendingReminderAlarm(
            kind = PendingReminderAlarmKind.Delayed,
            eventId = 1L,
            itemIds = itemIds,
            itemId = -1L,
            dueAtMillis = dueAtMillis,
        )
    }

    private fun snoozedAlarm(dueAtMillis: Long = NOW + 10_000L): PendingReminderAlarm {
        return PendingReminderAlarm(
            kind = PendingReminderAlarmKind.Snoozed,
            eventId = 1L,
            itemIds = emptyList(),
            itemId = 10L,
            dueAtMillis = dueAtMillis,
        )
    }

    private fun criticalRepeatAlarm(
        dueAtMillis: Long = NOW + 10_000L,
        firstShownAtMillis: Long = NOW - 10_000L,
    ): PendingReminderAlarm {
        return PendingReminderAlarm(
            kind = PendingReminderAlarmKind.CriticalRepeat,
            eventId = 1L,
            itemIds = emptyList(),
            itemId = -1L,
            dueAtMillis = dueAtMillis,
            repeatCount = 1,
            firstShownAtMillis = firstShownAtMillis,
        )
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
