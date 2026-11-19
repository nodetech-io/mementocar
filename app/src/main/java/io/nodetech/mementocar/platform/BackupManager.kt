// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.nodetech.mementocar.data.BackupDocumentCodec
import io.nodetech.mementocar.data.BackupDocumentV1
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.domain.PendingReminderAlarmKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class BackupFileError {
    CannotRead,
    CannotWrite,
    TooLarge,
}

enum class BackupImportWarning {
    AlarmCleanup,
    NotificationCleanup,
    DiagnosticsCleanup,
    DelayPersistence,
}

data class BackupImportResult(
    val warnings: Set<BackupImportWarning> = emptySet(),
)

class BackupFileException(
    val error: BackupFileError,
    cause: Throwable? = null,
) : IOException(error.name, cause)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: MementoCarRepository,
    private val notificationDelaySettings: NotificationDelaySettings,
    private val runtimeCleaner: BackupRuntimeCleaner,
    private val operationCoordinator: ReminderOperationCoordinator,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val document = operationCoordinator.runExclusive {
            repository.createBackupDocument(
                bluetoothEventDelayMinutes = notificationDelaySettings.currentBluetoothEventDelayMinutes(),
                exportedAtEpochMillis = System.currentTimeMillis(),
            )
        }
        val json = BackupDocumentCodec.encode(document)
        if (json.toByteArray(Charsets.UTF_8).size > BackupDocumentCodec.MAX_BYTES) {
            throw BackupFileException(BackupFileError.TooLarge)
        }
        try {
            val stream = contentResolver.openOutputStream(uri, "wt")
                ?: throw BackupFileException(BackupFileError.CannotWrite)
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json)
            }
        } catch (error: BackupFileException) {
            throw error
        } catch (error: IOException) {
            throw BackupFileException(BackupFileError.CannotWrite, error)
        } catch (error: SecurityException) {
            throw BackupFileException(BackupFileError.CannotWrite, error)
        }
    }

    suspend fun readFrom(uri: Uri): BackupDocumentV1 = withContext(Dispatchers.IO) {
        val bytes = try {
            val stream = contentResolver.openInputStream(uri)
                ?: throw BackupFileException(BackupFileError.CannotRead)
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > BackupDocumentCodec.MAX_BYTES) {
                        throw BackupFileException(BackupFileError.TooLarge)
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (error: BackupFileException) {
            throw error
        } catch (error: IOException) {
            throw BackupFileException(BackupFileError.CannotRead, error)
        } catch (error: SecurityException) {
            throw BackupFileException(BackupFileError.CannotRead, error)
        }
        BackupDocumentCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    suspend fun import(document: BackupDocumentV1): BackupImportResult = withContext(Dispatchers.IO) {
        operationCoordinator.runExclusive {
            repository.replaceConfiguration(document)
            withContext(NonCancellable) {
                val warnings = runtimeCleaner.clear().toMutableSet()
                val delayPersisted = runCatching {
                    notificationDelaySettings.restoreBluetoothEventDelayMinutes(
                        document.settings.bluetoothEventDelayMinutes,
                    )
                }.getOrDefault(false)
                if (!delayPersisted) {
                    warnings += BackupImportWarning.DelayPersistence
                }
                BackupImportResult(warnings)
            }
        }
    }
}

@Singleton
class BackupRuntimeCleaner @Inject constructor(
    private val alarmStore: PendingReminderAlarmStore,
    private val delayedReminderScheduler: DelayedReminderScheduler,
    private val snoozeReminderScheduler: SnoozeReminderScheduler,
    private val criticalReminderScheduler: CriticalReminderScheduler,
    private val notifier: ReminderNotifier,
    private val diagnosticsStore: ConnectionDiagnosticsStore,
) {
    fun clear(): Set<BackupImportWarning> {
        val warnings = mutableSetOf<BackupImportWarning>()
        val alarms = runCatching(alarmStore::all).getOrElse {
            warnings += BackupImportWarning.AlarmCleanup
            emptyList()
        }
        alarms.forEach { alarm ->
            val cancelled = runCatching {
                when (alarm.kind) {
                    PendingReminderAlarmKind.Delayed -> delayedReminderScheduler.cancel(alarm.eventId)
                    PendingReminderAlarmKind.Snoozed -> snoozeReminderScheduler.cancel(alarm.eventId, alarm.itemId)
                    PendingReminderAlarmKind.CriticalRepeat -> criticalReminderScheduler.cancel(alarm.eventId)
                }
            }.isSuccess
            if (!cancelled) warnings += BackupImportWarning.AlarmCleanup
        }
        if (!runCatching(alarmStore::clear).getOrDefault(false)) {
            warnings += BackupImportWarning.AlarmCleanup
        }
        if (!runCatching(notifier::dismissAll).getOrDefault(false)) {
            warnings += BackupImportWarning.NotificationCleanup
        }
        if (!runCatching(diagnosticsStore::clear).getOrDefault(false)) {
            warnings += BackupImportWarning.DiagnosticsCleanup
        }
        return warnings
    }
}
