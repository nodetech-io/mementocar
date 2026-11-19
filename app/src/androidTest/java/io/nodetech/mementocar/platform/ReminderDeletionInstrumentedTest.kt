// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.MementoCarDatabase
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.data.ReminderTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeletionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: MementoCarDatabase
    private lateinit var repository: MementoCarRepository
    private lateinit var notifier: ReminderNotifier
    private lateinit var coordinator: ReminderOperationCoordinator
    private lateinit var deletionManager: ReminderDeletionManager

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
            ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
        database = Room.inMemoryDatabaseBuilder(context, MementoCarDatabase::class.java).build()
        repository = MementoCarRepository(context, database)
        notifier = ReminderNotifier(context, PendingReminderAlarmStore(context))
        notifier.ensureChannels()
        coordinator = ReminderOperationCoordinator()
        deletionManager = ReminderDeletionManager(
            repository, notifier, ConnectionDiagnosticsStore(context), coordinator,
        )
    }

    @After
    fun tearDown() {
        notifier.dismissAll()
        database.close()
    }

    @Test
    fun vehicleDeletionWaitsForInFlightDeliveryAndCleansItsNotification() = runBlocking {
        verifyDeletionWaitsForDelivery(deleteVehicle = true)
    }

    @Test
    fun itemDeletionWaitsForInFlightDeliveryAndCleansItsNotification() = runBlocking {
        verifyDeletionWaitsForDelivery(deleteVehicle = false)
    }

    private suspend fun verifyDeletionWaitsForDelivery(deleteVehicle: Boolean) = coroutineScope {
        val fixture = seedReminder()
        val deliveryRead = CompletableDeferred<Unit>()
        val continueDelivery = CompletableDeferred<Unit>()
        val delivery = async {
            coordinator.runExclusive {
                val event = repository.reminderEventById(fixture.eventId)!!
                val items = repository.pendingReminderEventItems(event.id)
                deliveryRead.complete(Unit)
                continueDelivery.await()
                assertTrue(notifier.showReminder(event.id, "Car", event.trigger,
                    items.map { ReminderNotificationItem(it.itemId, it.labelSnapshot) }))
            }
        }
        deliveryRead.await()
        // Start synchronously until suspended on the occupied coordinator mutex.
        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            if (deleteVehicle) {
                deletionManager.deleteVehicle(fixture.vehicleId)
            } else {
                assertTrue(deletionManager.deleteChecklistItem(fixture.itemId))
            }
        }
        try {
            val finishedWhileDelivering = withTimeoutOrNull(1_000L) {
                deletion.await()
                true
            }
            assertNull("Deletion must wait for notification delivery", finishedWhileDelivering)
        } finally {
            continueDelivery.complete(Unit)
        }
        delivery.await()
        deletion.await()

        assertTrue(notifier.shownReminderIds(fixture.eventId).isEmpty())
        assertTrue(repository.checklistItemsByIds(listOf(fixture.itemId)).isEmpty())
        if (deleteVehicle) {
            assertNull(repository.reminderEventById(fixture.eventId))
        } else {
            assertEquals(ReminderStatus.Dismissed, repository.reminderEventById(fixture.eventId)?.status)
        }
    }

    @Test
    fun deliveryAfterVehicleDeletionCannotReadDeletedEvent() = runBlocking {
        val fixture = seedReminder()
        deletionManager.deleteVehicle(fixture.vehicleId)
        coordinator.runExclusive {
            assertNull(repository.reminderEventById(fixture.eventId))
        }
        assertTrue(notifier.shownReminderIds(fixture.eventId).isEmpty())
    }

    @Test
    fun deliveryAfterItemDeletionSeesNoPendingItems() = runBlocking {
        val fixture = seedReminder()
        assertTrue(deletionManager.deleteChecklistItem(fixture.itemId))
        coordinator.runExclusive {
            assertEquals(ReminderStatus.Dismissed, repository.reminderEventById(fixture.eventId)?.status)
            assertTrue(repository.pendingReminderEventItems(fixture.eventId).isEmpty())
        }
        assertTrue(notifier.shownReminderIds(fixture.eventId).isEmpty())
    }

    @Test
    fun deletingOneItemPreservesOtherPendingItemsAndNotifications() = runBlocking {
        val fixture = seedReminder()
        val otherItemId = repository.addChecklistItem(fixture.vehicleId, "Umbrella",
            ReminderTrigger.Disconnect, ReminderRecurrence.EveryTrigger, ActiveDays.All)
        val items = repository.checklistItemsByIds(listOf(fixture.itemId, otherItemId))
        repository.replaceReminderEventItems(fixture.eventId, items)
        assertTrue(notifier.showReminder(fixture.eventId, "Car", ReminderTrigger.Disconnect,
            items.map { ReminderNotificationItem(it.id, it.label) }))
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val deletedTag = reminderIdentity("item", fixture.eventId, fixture.itemId)
        val otherTag = reminderIdentity("item", fixture.eventId, otherItemId)
        val summaryTag = reminderIdentity("summary", fixture.eventId)
        awaitNotifications {
            manager.activeNotifications.map { it.tag }.containsAll(listOf(deletedTag, otherTag, summaryTag))
        }

        assertTrue(deletionManager.deleteChecklistItem(fixture.itemId))

        assertEquals(listOf(otherItemId), repository.pendingReminderEventItems(fixture.eventId).map { it.itemId })
        assertEquals(ReminderStatus.Pending, repository.reminderEventById(fixture.eventId)?.status)
        awaitNotifications {
            val active = manager.activeNotifications
            val tags = active.map { it.tag }
            val lines = active.firstOrNull { it.tag == summaryTag }?.notification?.extras
                ?.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.map { it.toString() }
            deletedTag !in tags && otherTag in tags && lines == listOf("Umbrella")
        }
    }

    @Test
    fun missingItemDoesNotReportSuccessfulDeletion() = runBlocking {
        assertFalse(deletionManager.deleteChecklistItem(Long.MAX_VALUE))
    }

    private fun awaitNotifications(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000L
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        assertTrue("NotificationManager did not reach the expected state", condition())
    }

    private suspend fun seedReminder(): Fixture {
        val vehicleId = repository.addVehicle("Car", "00:11:22:33:44:55")
        val itemId = repository.addChecklistItem(vehicleId, "Wallet",
            ReminderTrigger.Disconnect, ReminderRecurrence.EveryTrigger, ActiveDays.All)
        val eventId = repository.createReminderEventWithItems(vehicleId, ReminderTrigger.Disconnect,
            System.currentTimeMillis(), repository.checklistItemsByIds(listOf(itemId)))
        return Fixture(vehicleId, itemId, eventId)
    }

    private data class Fixture(val vehicleId: Long, val itemId: Long, val eventId: Long)
}
