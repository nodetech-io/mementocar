// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDeletionInstrumentedTest {
    private lateinit var database: MementoCarDatabase
    private lateinit var repository: MementoCarRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MementoCarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MementoCarRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteVehicleReturnsEventIdsAndCascadesAllVehicleData() = runBlocking {
        val vehicleId = repository.addVehicle("Family car", "00:11:22:33:44:55")
        val itemId = repository.addChecklistItem(
            vehicleId = vehicleId,
            label = "Buy oil",
            trigger = ReminderTrigger.Disconnect,
            recurrence = ReminderRecurrence.Once,
            activeDaysMask = ActiveDays.All,
        )
        val item = repository.checklistItemsByIds(listOf(itemId)).single()
        val eventId = repository.createReminderEventWithItems(
            vehicleId = vehicleId,
            trigger = ReminderTrigger.Disconnect,
            disconnectedAtMillis = 1_000L,
            items = listOf(item),
        )

        val deletedEventIds = repository.deleteVehicle(vehicleId)

        assertEquals(listOf(eventId), deletedEventIds)
        assertNull(repository.vehicleById(vehicleId))
        assertEquals(emptyList<ChecklistItemEntity>(), repository.checklistItemsByIds(listOf(itemId)))
        assertNull(repository.reminderEventById(eventId))
        assertEquals(emptyList<ReminderEventItemEntity>(), repository.reminderEventItems(eventId))
    }

    @Test
    fun deleteChecklistItemUpdatesThenDismissesAffectedEvent() = runBlocking {
        val vehicleId = repository.addVehicle("Family car", "00:11:22:33:44:55")
        val firstItemId = repository.addChecklistItem(
            vehicleId = vehicleId,
            label = "Buy oil",
            trigger = ReminderTrigger.Disconnect,
            recurrence = ReminderRecurrence.Once,
            activeDaysMask = ActiveDays.All,
        )
        val secondItemId = repository.addChecklistItem(
            vehicleId = vehicleId,
            label = "Take umbrella",
            trigger = ReminderTrigger.Disconnect,
            recurrence = ReminderRecurrence.Once,
            activeDaysMask = ActiveDays.All,
        )
        val items = repository.checklistItemsByIds(listOf(firstItemId, secondItemId))
        val eventId = repository.createReminderEventWithItems(
            vehicleId = vehicleId,
            trigger = ReminderTrigger.Disconnect,
            disconnectedAtMillis = 1_000L,
            items = items,
        )

        val firstResult = repository.deleteChecklistItem(firstItemId)

        assertEquals(listOf(eventId), firstResult?.updatedEventIds)
        assertEquals(emptyList<Long>(), firstResult?.dismissedEventIds)
        assertEquals(listOf(secondItemId), repository.reminderEventItems(eventId).map { it.itemId })
        assertEquals(ReminderStatus.Pending, repository.reminderEventById(eventId)?.status)

        val secondResult = repository.deleteChecklistItem(secondItemId)

        assertEquals(emptyList<Long>(), secondResult?.updatedEventIds)
        assertEquals(listOf(eventId), secondResult?.dismissedEventIds)
        assertEquals(emptyList<ReminderEventItemEntity>(), repository.reminderEventItems(eventId))
        assertEquals(ReminderStatus.Dismissed, repository.reminderEventById(eventId)?.status)
    }

    @Test
    fun addChecklistItemsValidatesEveryVehicleBeforeWriting() = runBlocking {
        val vehicleId = repository.addVehicle("Family car", "00:11:22:33:44:55")

        val failed = try {
            repository.addChecklistItems(
                vehicleIds = listOf(vehicleId, Long.MAX_VALUE),
                label = "Buy oil",
                trigger = ReminderTrigger.Disconnect,
                recurrence = ReminderRecurrence.Once,
                activeDaysMask = ActiveDays.All,
            )
            false
        } catch (_: IllegalArgumentException) {
            true
        }

        assertTrue(failed)
        assertEquals(emptyList<ChecklistItemEntity>(), database.checklistDao().activeSnapshot())
    }

    @Test
    fun backupSnapshotAndReplaceRestoreOnlyActiveConfiguration() = runBlocking {
        val oldVehicleId = repository.addVehicle("Old car", "00:11:22:33:44:55")
        val oldItemId = repository.addChecklistItem(
            vehicleId = oldVehicleId,
            label = "Old reminder",
            trigger = ReminderTrigger.Connect,
            recurrence = ReminderRecurrence.Once,
            activeDaysMask = ActiveDays.All,
        )
        repository.markChecklistItemsTriggered(listOf(oldItemId), 999L)
        val oldItem = repository.checklistItemsByIds(listOf(oldItemId)).single()
        val oldEventId = repository.createReminderEventWithItems(
            vehicleId = oldVehicleId,
            trigger = ReminderTrigger.Connect,
            disconnectedAtMillis = 1_000L,
            items = listOf(oldItem),
        )

        val snapshot = repository.createBackupDocument(
            bluetoothEventDelayMinutes = 5,
            exportedAtEpochMillis = 2_000L,
        )
        assertEquals("Old reminder", snapshot.vehicles.single().reminders.single().label)

        val replacement = BackupDocumentV1(
            format = BackupDocumentCodec.FORMAT,
            schemaVersion = BackupDocumentCodec.SCHEMA_VERSION,
            exportedAtEpochMillis = 3_000L,
            settings = BackupSettingsV1(1),
            vehicles = listOf(
                BackupVehicleV1(
                    name = "Restored car",
                    bluetoothIdHash = "a".repeat(64),
                    reminders = listOf(
                        BackupReminderV1(
                            label = "Restored reminder",
                            trigger = BackupTriggerV1.Disconnect,
                            recurrence = BackupRecurrenceV1.Weekly,
                            activeDaysMask = ActiveDays.Weekdays,
                            critical = true,
                        ),
                    ),
                ),
            ),
        )

        repository.replaceConfiguration(replacement)

        assertNull(repository.vehicleById(oldVehicleId))
        assertNull(repository.reminderEventById(oldEventId))
        val restoredVehicle = database.vehicleDao().activeSnapshot().single()
        val restoredReminder = database.checklistDao().activeSnapshot().single()
        assertEquals("Restored car", restoredVehicle.name)
        assertEquals(restoredVehicle.id, restoredReminder.vehicleId)
        assertEquals("Restored reminder", restoredReminder.label)
        assertEquals(ReminderRecurrence.Weekly, restoredReminder.recurrence)
        assertEquals(ActiveDays.Weekdays, restoredReminder.activeDaysMask)
        assertEquals(true, restoredReminder.critical)
        assertNull(restoredReminder.lastTriggeredAtMillis)
    }
}
