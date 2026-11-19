// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class BackupDocumentV1(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val settings: BackupSettingsV1,
    val vehicles: List<BackupVehicleV1>,
)

@Serializable
data class BackupSettingsV1(
    val bluetoothEventDelayMinutes: Int,
)

@Serializable
data class BackupVehicleV1(
    val name: String,
    val bluetoothIdHash: String,
    val reminders: List<BackupReminderV1>,
)

@Serializable
data class BackupReminderV1(
    val label: String,
    val trigger: BackupTriggerV1,
    val recurrence: BackupRecurrenceV1,
    val activeDaysMask: Int,
    val critical: Boolean,
    val eligibleFromMillis: Long? = null,
)

@Serializable
enum class BackupTriggerV1 {
    Connect,
    Disconnect,
}

@Serializable
enum class BackupRecurrenceV1 {
    Once,
    EveryTrigger,
    Daily,
    Weekly,
    Monthly,
}

data class BackupSummary(
    val exportedAtEpochMillis: Long,
    val vehicleCount: Int,
    val reminderCount: Int,
)

enum class BackupValidationError {
    InvalidFormat,
    UnsupportedVersion,
    InvalidExportDate,
    InvalidDelay,
    InvalidVehicle,
    DuplicateVehicle,
    InvalidReminder,
}

class BackupValidationException(
    val error: BackupValidationError,
    cause: Throwable? = null,
) : IllegalArgumentException(error.name, cause)

object BackupDocumentCodec {
    const val FORMAT = "memento-car-backup"
    const val SCHEMA_VERSION = 2
    const val MAX_BYTES = 5 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(document: BackupDocumentV1): String {
        validate(document)
        return json.encodeToString(BackupDocumentV1.serializer(), document)
    }

    fun decode(value: String): BackupDocumentV1 {
        val document = try {
            json.decodeFromString(BackupDocumentV1.serializer(), value)
        } catch (error: SerializationException) {
            throw BackupValidationException(BackupValidationError.InvalidFormat, error)
        } catch (error: IllegalArgumentException) {
            throw BackupValidationException(BackupValidationError.InvalidFormat, error)
        }
        validate(document)
        return document.normalized()
    }

    fun validate(document: BackupDocumentV1) {
        if (document.format != FORMAT) {
            throw BackupValidationException(BackupValidationError.InvalidFormat)
        }
        if (document.schemaVersion !in 1..SCHEMA_VERSION) {
            throw BackupValidationException(BackupValidationError.UnsupportedVersion)
        }
        if (document.exportedAtEpochMillis <= 0L) {
            throw BackupValidationException(BackupValidationError.InvalidExportDate)
        }
        if (document.settings.bluetoothEventDelayMinutes !in 0..30) {
            throw BackupValidationException(BackupValidationError.InvalidDelay)
        }
        val hashes = mutableSetOf<String>()
        document.vehicles.forEach { vehicle ->
            val hash = vehicle.bluetoothIdHash.lowercase()
            if (vehicle.name.isBlank() || !SHA_256_PATTERN.matches(hash)) {
                throw BackupValidationException(BackupValidationError.InvalidVehicle)
            }
            if (!hashes.add(hash)) {
                throw BackupValidationException(BackupValidationError.DuplicateVehicle)
            }
            vehicle.reminders.forEach { reminder ->
                val validMask = reminder.activeDaysMask != 0 &&
                    reminder.activeDaysMask and ActiveDays.All == reminder.activeDaysMask
                val validEligibility = reminder.eligibleFromMillis == null ||
                    reminder.eligibleFromMillis > 0L && reminder.recurrence == BackupRecurrenceV1.Once
                if (
                    reminder.label.isBlank() ||
                    reminder.label.trim().length > DataLimits.ReminderLabelMaxChars ||
                    !validMask ||
                    !validEligibility
                ) {
                    throw BackupValidationException(BackupValidationError.InvalidReminder)
                }
            }
        }
    }

    private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
}

fun BackupDocumentV1.summary(): BackupSummary {
    return BackupSummary(
        exportedAtEpochMillis = exportedAtEpochMillis,
        vehicleCount = vehicles.size,
        reminderCount = vehicles.sumOf { it.reminders.size },
    )
}

private fun BackupDocumentV1.normalized(): BackupDocumentV1 {
    return copy(
        vehicles = vehicles.map { vehicle ->
            vehicle.copy(
                name = vehicle.name.trim(),
                bluetoothIdHash = vehicle.bluetoothIdHash.lowercase(),
                reminders = vehicle.reminders.map { reminder ->
                    reminder.copy(label = reminder.label.trim())
                },
            )
        },
    )
}

fun ReminderTrigger.toBackupTrigger(): BackupTriggerV1 = BackupTriggerV1.valueOf(name)

fun BackupTriggerV1.toReminderTrigger(): ReminderTrigger = ReminderTrigger.valueOf(name)

fun ReminderRecurrence.toBackupRecurrence(): BackupRecurrenceV1 = BackupRecurrenceV1.valueOf(name)

fun BackupRecurrenceV1.toReminderRecurrence(): ReminderRecurrence = ReminderRecurrence.valueOf(name)
