// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.app.PendingIntent
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.nodetech.mementocar.data.*
import io.nodetech.mementocar.domain.isDueForReminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRegressionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun collidingPairsHaveIndependentPendingIntents() {
        val first = PendingIntent.getBroadcast(context, 0,
            SnoozeReceiver.remindAgainIntent(context, 1L, 32L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val second = PendingIntent.getBroadcast(context, 0,
            SnoozeReceiver.remindAgainIntent(context, 2L, 1L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            assertNotEquals(first, second)
            assertFalse(SnoozeReceiver.intent(context, 1, 32)
                .filterEquals(SnoozeReceiver.intent(context, 2, 1)))
            assertFalse(CompleteReminderReceiver.intent(context, 1, 32)
                .filterEquals(CompleteReminderReceiver.intent(context, 2, 1)))
            assertEquals(4, setOf(
                reminderIdentity("item", 1, 32), reminderIdentity("item", 2, 1),
                reminderIdentity("summary", 63),
                reminderIdentity("preview", 0),
            ).size)
        } finally {
            first.cancel()
            second.cancel()
        }
    }

    @Test
    fun scheduleEditsResetDeliveryButTextEditsPreserveIt() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MementoCarDatabase::class.java).build()
        try {
            val vehicleId = database.vehicleDao().insert(VehicleEntity(name = "Car", bluetoothIdHash = "test"))
            val original = ChecklistItemEntity(vehicleId = vehicleId, label = "Oil", sortOrder = 0,
                lastTriggeredAtMillis = 1000L)
            val itemId = database.checklistDao().insert(original)
            val repository = MementoCarRepository(context, database)
            repository.updateChecklistItem(itemId, listOf(vehicleId), "New text",
                ReminderTrigger.Disconnect, ReminderRecurrence.EveryTrigger, ActiveDays.All)
            assertEquals(1000L, database.checklistDao().findById(itemId)!!.lastTriggeredAtMillis)
            repository.updateChecklistItem(itemId, listOf(vehicleId), "New text",
                ReminderTrigger.Disconnect, ReminderRecurrence.Once, ActiveDays.All)
            assertTrue(database.checklistDao().findById(itemId)!!.isDueForReminder(2000L))
            database.checklistDao().update(database.checklistDao().findById(itemId)!!.copy(lastTriggeredAtMillis = 2000L))
            val future = System.currentTimeMillis() + 86400000L
            repository.updateChecklistItem(itemId, listOf(vehicleId), "Future",
                ReminderTrigger.Disconnect, ReminderRecurrence.Once, ActiveDays.All, eligibleFromMillis = future)
            val edited = database.checklistDao().findById(itemId)!!
            assertFalse(edited.isDueForReminder(future - 1))
            assertTrue(edited.isDueForReminder(future))
        } finally {
            database.close()
        }
    }

    @Test
    fun collidingNotificationsCoexistAndCancelIndependently() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val notifier = ReminderNotifier(context, PendingReminderAlarmStore(context))
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        notifier.ensureChannels()
        try {
            assertTrue(notifier.showReminder(1, "Car", ReminderTrigger.Disconnect,
                listOf(ReminderNotificationItem(32, "First"))))
            assertTrue(notifier.showReminder(2, "Car", ReminderTrigger.Disconnect,
                listOf(ReminderNotificationItem(1, "Second"))))
            assertTrue(notifier.showReminder(63, "Car", ReminderTrigger.Disconnect,
                listOf(ReminderNotificationItem(3, "Third"), ReminderNotificationItem(4, "Fourth"))))
            val firstTag = reminderIdentity("item", 1, 32)
            val secondTag = reminderIdentity("item", 2, 1)
            val summaryTag = reminderIdentity("summary", 63)
            awaitNotifications {
                manager.activeNotifications.map { it.tag }.containsAll(
                    listOf(firstTag, secondTag, summaryTag))
            }
            val notification = manager.activeNotifications.first { it.tag == firstTag }.notification
            assertEquals("First", notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            assertNull(notification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT))
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertEquals(io.nodetech.mementocar.R.drawable.ic_notification_reminder, notification.smallIcon.resId)
            assertEquals(2, notification.actions.size)
            assertEquals(context.getString(io.nodetech.mementocar.R.string.complete), notification.actions[0].title.toString())
            assertEquals(context.getString(io.nodetech.mementocar.R.string.notification_snooze_10_min), notification.actions[1].title.toString())
            notifier.dismissReminder(1, 32)
            awaitNotifications {
                val remaining = manager.activeNotifications.map { it.tag }
                firstTag !in remaining && secondTag in remaining && summaryTag in remaining
            }
        } finally {
            listOf(1L, 2L, 63L).forEach { notifier.dismissReminder(it) }
        }
    }

    private fun awaitNotifications(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000L
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50L)
        }
        assertTrue("NotificationManager did not reach the expected state", condition())
    }

    @Test
    fun individualSuppressionSurvivesNotifierRecreation() {
        val store = PendingReminderAlarmStore(context)
        val eventId = Long.MAX_VALUE
        val notifier = ReminderNotifier(context, store)
        try {
            notifier.suppressCriticalRepeat(eventId, 1)
            assertEquals(setOf(1L), ReminderNotifier(context, store).suppressedCriticalIds(eventId))
        } finally {
            notifier.dismissReminder(eventId)
        }
    }
}
