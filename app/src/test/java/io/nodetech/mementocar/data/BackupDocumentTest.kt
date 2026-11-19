// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupDocumentTest {
    @Test
    fun codecRoundTripPreservesConfigurationAndNormalizesText() {
        val document = sampleDocument(
            vehicles = listOf(
                BackupVehicleV1(
                    name = "  Family car  ",
                    bluetoothIdHash = "A".repeat(64),
                    reminders = listOf(
                        BackupReminderV1(
                            label = "  Buy oil & café  ",
                            trigger = BackupTriggerV1.Disconnect,
                            recurrence = BackupRecurrenceV1.Weekly,
                            activeDaysMask = ActiveDays.Weekdays,
                            critical = true,
                        ),
                    ),
                ),
            ),
        )

        val decoded = BackupDocumentCodec.decode(BackupDocumentCodec.encode(document))

        assertEquals("Family car", decoded.vehicles.single().name)
        assertEquals("a".repeat(64), decoded.vehicles.single().bluetoothIdHash)
        assertEquals("Buy oil & café", decoded.vehicles.single().reminders.single().label)
        assertEquals(BackupRecurrenceV1.Weekly, decoded.vehicles.single().reminders.single().recurrence)
    }

    @Test
    fun decoderImportsVersionOneWithoutFutureTripSchedule() {
        val versionOne = BackupDocumentCodec.encode(sampleDocument())
            .replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
            .replace(Regex(",?\\s*\"eligibleFromMillis\": null"), "")

        val decoded = BackupDocumentCodec.decode(versionOne)

        assertEquals(1, decoded.schemaVersion)
        assertNull(decoded.vehicles.single().reminders.single().eligibleFromMillis)
    }

    @Test
    fun decoderAcceptsUnknownFieldsWithinCurrentVersion() {
        val encoded = BackupDocumentCodec.encode(sampleDocument())
        val withFutureField = encoded.replaceFirst("{", "{\n  \"futureField\": true,")

        val decoded = BackupDocumentCodec.decode(withFutureField)

        assertEquals(BackupDocumentCodec.SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun decoderRejectsFutureVersionAndInvalidReminderMask() {
        val encoded = BackupDocumentCodec.encode(sampleDocument())
        val future = encoded.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3")
        val versionError = assertThrows(BackupValidationException::class.java) {
            BackupDocumentCodec.decode(future)
        }
        assertEquals(BackupValidationError.UnsupportedVersion, versionError.error)

        val invalid = sampleDocument(
            vehicles = listOf(
                sampleVehicle(
                    reminders = listOf(sampleReminder().copy(activeDaysMask = 0)),
                ),
            ),
        )
        val reminderError = assertThrows(BackupValidationException::class.java) {
            BackupDocumentCodec.validate(invalid)
        }
        assertEquals(BackupValidationError.InvalidReminder, reminderError.error)

        val invalidSchedule = sampleDocument(
            vehicles = listOf(
                sampleVehicle(
                    reminders = listOf(sampleReminder().copy(eligibleFromMillis = 10_000L)),
                ),
            ),
        )
        val scheduleError = assertThrows(BackupValidationException::class.java) {
            BackupDocumentCodec.validate(invalidSchedule)
        }
        assertEquals(BackupValidationError.InvalidReminder, scheduleError.error)

        val malformedError = assertThrows(BackupValidationException::class.java) {
            BackupDocumentCodec.decode("{not-json}")
        }
        assertEquals(BackupValidationError.InvalidFormat, malformedError.error)
    }

    @Test
    fun decoderRejectsReminderLabelsThatExceedTheStorageLimit() {
        val document = sampleDocument(
            vehicles = listOf(
                sampleVehicle(
                    reminders = listOf(
                        sampleReminder().copy(
                            label = "x".repeat(DataLimits.ReminderLabelMaxChars + 1),
                        ),
                    ),
                ),
            ),
        )

        val error = assertThrows(BackupValidationException::class.java) {
            BackupDocumentCodec.validate(document)
        }

        assertEquals(BackupValidationError.InvalidReminder, error.error)
    }

    @Test
    fun codecAcceptsMultipleCarsAndAllReminderFeatures() {
        val reminders = List(6) { index ->
            sampleReminder().copy(
                label = "Reminder $index",
                critical = true,
                activeDaysMask = ActiveDays.Weekdays,
            )
        } + sampleReminder().copy(
            recurrence = BackupRecurrenceV1.Once,
            eligibleFromMillis = 10_000L,
        )
        val document = sampleDocument(
            delayMinutes = 30,
            vehicles = listOf(
                sampleVehicle(reminders = reminders),
                sampleVehicle("b".repeat(64)),
            ),
        )

        val decoded = BackupDocumentCodec.decode(BackupDocumentCodec.encode(document))

        assertEquals(document, decoded)
        assertEquals(2, decoded.summary().vehicleCount)
        assertEquals(8, decoded.summary().reminderCount)
    }

    private fun sampleDocument(
        delayMinutes: Int = 5,
        vehicles: List<BackupVehicleV1> = listOf(sampleVehicle()),
    ) = BackupDocumentV1(
        format = BackupDocumentCodec.FORMAT,
        schemaVersion = BackupDocumentCodec.SCHEMA_VERSION,
        exportedAtEpochMillis = 1_000L,
        settings = BackupSettingsV1(delayMinutes),
        vehicles = vehicles,
    )

    private fun sampleVehicle(
        hash: String = "a".repeat(64),
        reminders: List<BackupReminderV1> = listOf(sampleReminder()),
    ) = BackupVehicleV1(
        name = "Family car",
        bluetoothIdHash = hash,
        reminders = reminders,
    )

    private fun sampleReminder() = BackupReminderV1(
        label = "Buy oil",
        trigger = BackupTriggerV1.Disconnect,
        recurrence = BackupRecurrenceV1.EveryTrigger,
        activeDaysMask = ActiveDays.All,
        critical = false,
    )
}
