// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.BackupDocumentV1
import io.nodetech.mementocar.data.BackupSummary
import io.nodetech.mementocar.data.BackupValidationError
import io.nodetech.mementocar.data.BackupValidationException
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.DataLimits
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderEventItemCompletion
import io.nodetech.mementocar.data.ReminderEventItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderEventEntity
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.data.VehicleEntity
import io.nodetech.mementocar.data.summary
import io.nodetech.mementocar.domain.SystemReadinessLevel
import io.nodetech.mementocar.domain.SystemReadinessInput
import io.nodetech.mementocar.domain.SystemReadinessReason
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.domain.SystemSetupInput
import io.nodetech.mementocar.domain.HandsFreeVoiceReminderAction
import io.nodetech.mementocar.domain.VoiceCommand
import io.nodetech.mementocar.domain.VoiceCommandParser
import io.nodetech.mementocar.domain.VoiceItemCandidate
import io.nodetech.mementocar.domain.VoiceItemMatch
import io.nodetech.mementocar.domain.latestOpenReminderEvent
import io.nodetech.mementocar.domain.voiceSnoozeTarget
import io.nodetech.mementocar.domain.matchVoiceItem
import io.nodetech.mementocar.domain.dueReminderItems
import io.nodetech.mementocar.domain.evaluateSystemReadiness
import io.nodetech.mementocar.domain.handsFreeVoiceReminderAction
import io.nodetech.mementocar.domain.nextSystemSetupAction
import io.nodetech.mementocar.platform.BluetoothDeviceProvider
import io.nodetech.mementocar.platform.BackupFileError
import io.nodetech.mementocar.platform.BackupFileException
import io.nodetech.mementocar.platform.BackupManager
import io.nodetech.mementocar.platform.ConnectionDiagnosticEvent
import io.nodetech.mementocar.platform.ConnectionDiagnosticResult
import io.nodetech.mementocar.platform.ConnectionDiagnosticsState
import io.nodetech.mementocar.platform.ConnectionDiagnosticsStore
import io.nodetech.mementocar.platform.NotificationBlockReason
import io.nodetech.mementocar.platform.NotificationDelaySettings
import io.nodetech.mementocar.platform.PairedBluetoothDevice
import io.nodetech.mementocar.platform.ReminderDeletionManager
import io.nodetech.mementocar.platform.ReminderNotificationItem
import io.nodetech.mementocar.platform.ReminderNotifier
import io.nodetech.mementocar.platform.ReminderOperationCoordinator
import io.nodetech.mementocar.platform.ReminderSnoozer
import io.nodetech.mementocar.platform.SystemDiagnosticsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class VoiceFeedbackUiState(val id: Long, val text: String)

data class MementoCarUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val pairedDevices: List<PairedBluetoothDevice> = emptyList(),
    val selectedVehicleId: Long? = null,
    val checklist: List<ChecklistItemEntity> = emptyList(),
    val openReminderEvents: List<OpenReminderEventUiState> = emptyList(),
    val voiceReminderDraft: VoiceReminderDraftUiState? = null,
    val bluetoothEventNotificationDelayMinutes: Int = NotificationDelaySettings.DEFAULT_DELAY_MINUTES,
    val diagnostics: DiagnosticsUiState = DiagnosticsUiState(),
    val backupImportSummary: BackupSummary? = null,
    val backupOperationInProgress: Boolean = false,
    val message: UiMessage? = null,
)

/** Error = something failed; Warning = the user must act or retry (permissions, missing car). */
enum class MessageKind { Success, Info, Warning, Error }

data class UiMessage(val text: String, val kind: MessageKind)

enum class TestAlertsBlockedReason { NoVehicle, NotificationsBlocked }

internal fun testAlertsBlockedReason(hasVehicle: Boolean, canPostNotifications: Boolean): TestAlertsBlockedReason? =
    when {
        !hasVehicle -> TestAlertsBlockedReason.NoVehicle
        !canPostNotifications -> TestAlertsBlockedReason.NotificationsBlocked
        else -> null
    }

data class DiagnosticsUiState(
    val level: DiagnosticsLevelUiState = DiagnosticsLevelUiState.Blocked,
    val title: String = "",
    val detail: String = "",
    val setupAction: SystemSetupAction = SystemSetupAction.ConfigureBluetoothVehicle,
    val checks: List<DiagnosticCheckUiState> = emptyList(),
    val latestEvent: LatestDiagnosticEventUiState? = null,
    val testAlertsBlockedReason: TestAlertsBlockedReason? = TestAlertsBlockedReason.NoVehicle,
) {
    val canTestAlerts: Boolean get() = testAlertsBlockedReason == null
}

data class DiagnosticCheckUiState(
    val label: String,
    val detail: String,
    val level: DiagnosticsLevelUiState,
)

data class LatestDiagnosticEventUiState(
    val title: String,
    val detail: String,
    val occurredAtMillis: Long,
)

enum class DiagnosticsLevelUiState {
    Ready,
    Attention,
    Blocked,
}

data class VoiceReminderDraftUiState(
    val draftId: Long,
    val label: String,
    val trigger: ReminderTrigger,
    val recurrence: ReminderRecurrence = ReminderRecurrence.Once,
    val activeDaysMask: Int = ActiveDays.All,
    val critical: Boolean = false,
)

data class OpenReminderEventUiState(
    val event: ReminderEventEntity,
    val items: List<OpenReminderItemUiState>,
)

/** A voice "complete all" waiting for an explicit yes/no for [eventId], which has [count] pending items. */
data class VoiceCompleteAllUiState(val eventId: Long, val count: Int)

data class OpenReminderItemUiState(
    val id: Long,
    val label: String,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MementoCarViewModel @Inject constructor(
    private val context: AppTextResolver,
    private val repository: MementoCarRepository,
    private val bluetoothDeviceProvider: BluetoothDeviceProvider,
    private val reminderNotifier: ReminderNotifier,
    private val notificationDelaySettings: NotificationDelaySettings,
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val diagnosticsStore: ConnectionDiagnosticsStore,
    private val backupManager: BackupManager,
    private val reminderDeletionManager: ReminderDeletionManager,
    private val operationCoordinator: ReminderOperationCoordinator,
    private val reminderSnoozer: ReminderSnoozer,
) : ViewModel() {
    private val selectedVehicleId = MutableStateFlow<Long?>(null)
    private val pairedDevices = MutableStateFlow<List<PairedBluetoothDevice>>(emptyList())
    private val message = MutableStateFlow<UiMessage?>(null)
    private val spokenFeedback = MutableStateFlow<VoiceFeedbackUiState?>(null)
    val voiceFeedback: StateFlow<VoiceFeedbackUiState?> = spokenFeedback
    private var voiceFeedbackSequence = 0L

    fun announceVoiceMessage(text: String, kind: MessageKind) {
        message.value = UiMessage(text, kind)
        spokenFeedback.value = VoiceFeedbackUiState(++voiceFeedbackSequence, text)
    }

    private val completeAllConfirmation = MutableStateFlow<VoiceCompleteAllUiState?>(null)
    val voiceCompleteAllConfirmation: StateFlow<VoiceCompleteAllUiState?> = completeAllConfirmation

    fun clearVoiceFeedback(id: Long) {
        if (spokenFeedback.value?.id == id) spokenFeedback.value = null
    }

    private val voiceReminderDraft = MutableStateFlow<VoiceReminderDraftUiState?>(null)
    private val backupImportSummary = MutableStateFlow<BackupSummary?>(null)
    private val backupOperationInProgress = MutableStateFlow(false)
    private var pendingBackupImport: BackupDocumentV1? = null
    private val diagnosticsRefresh = MutableStateFlow(0)
    private val reminderDisplayRefresh = MutableStateFlow(0)
    private val configurationMutationLock = Mutex()
    private val voiceCommandParser = VoiceCommandParser()
    private fun s(@StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }
    private fun p(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return context.resources.getQuantityString(resId, quantity, *formatArgs)
    }
    private val selectedChecklist = observeSelectedChecklist(
        selectedVehicleId = selectedVehicleId,
        observeChecklist = repository::observeChecklist,
    )
    private val openReminderEvents = combine(
        repository.observeOpenReminderEvents(),
        repository.observeReminderEventItems(),
        reminderDisplayRefresh,
    ) { events, eventItems, _ ->
        openReminderEvents(events, eventItems)
    }

    init {
        viewModelScope.launch {
            repository.observeVehicles()
                .map { vehicles -> vehicles.map { it.id } }
                .distinctUntilChanged()
                .collect { vehicleIds ->
                    val current = selectedVehicleId.value
                    when {
                        vehicleIds.isEmpty() -> clearVehicleSelection()
                        current == null || current !in vehicleIds -> selectVehicle(vehicleIds.first())
                    }
                }
        }
    }

    val uiState: StateFlow<MementoCarUiState> = combine(
        repository.observeVehicles(),
        pairedDevices,
        selectedVehicleId,
        selectedChecklist,
        openReminderEvents,
    ) { vehicles, devices, selected, checklistItems, events ->
        val effectiveSelected = selected ?: vehicles.firstOrNull()?.id
        MementoCarUiState(
            vehicles = vehicles,
            pairedDevices = devices,
            selectedVehicleId = effectiveSelected,
            checklist = checklistItems,
            openReminderEvents = events,
        )
    }.combine(voiceReminderDraft) { state, draft ->
        state.copy(voiceReminderDraft = draft)
    }.combine(notificationDelaySettings.bluetoothEventDelayMinutes) { state, bluetoothEventDelayMinutes ->
        state.copy(bluetoothEventNotificationDelayMinutes = bluetoothEventDelayMinutes)
    }.combine(diagnosticsStore.diagnostics) { state, diagnostics ->
        state.copy(diagnostics = diagnosticsState(state, diagnostics))
    }.combine(diagnosticsRefresh) { state, _ ->
        state.copy(diagnostics = diagnosticsState(state, diagnosticsStore.diagnostics.value))
    }.combine(message) { state, currentMessage ->
        state.copy(message = currentMessage)
    }.combine(backupImportSummary) { state, summary ->
        state.copy(backupImportSummary = summary)
    }.combine(backupOperationInProgress) { state, inProgress ->
        state.copy(backupOperationInProgress = inProgress)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MementoCarUiState(),
    )

    private suspend fun openReminderEvents(
        events: List<ReminderEventEntity>,
        eventItems: List<ReminderEventItemEntity>,
    ): List<OpenReminderEventUiState> {
        val itemsByEvent = eventItems.groupBy { it.eventId }
        return events.map { event ->
            OpenReminderEventUiState(
                event = event,
                items = pendingOpenReminderItems(
                    event = event,
                    persistedItems = itemsByEvent[event.id].orEmpty(),
                ),
            )
        }
    }

    private suspend fun pendingOpenReminderItems(
        event: ReminderEventEntity,
        persistedItems: List<ReminderEventItemEntity>,
    ): List<OpenReminderItemUiState> {
        if (persistedItems.isNotEmpty()) {
            return persistedItems
                .filter { !it.completed }
                .sortedBy { it.sortOrder }
                .map { item ->
                    OpenReminderItemUiState(
                        id = item.itemId,
                        label = item.labelSnapshot,
                    )
                }
        }
        val shownIds = reminderNotifier.shownReminderIds(event.id)
        val completedIds = reminderNotifier.completedReminderIds(event.id)
        val pendingIds = shownIds.minus(completedIds).toList()
        if (pendingIds.isEmpty()) return emptyList()
        val order = pendingIds.withIndex().associate { it.value to it.index }
        return repository.checklistItemsByIds(pendingIds)
            .sortedBy { order[it.id] ?: Int.MAX_VALUE }
            .map { item ->
                OpenReminderItemUiState(
                    id = item.id,
                    label = item.label,
                )
            }
    }

    /** Reloads the paired device list and diagnostics without posting any message. */
    fun refreshPairedDevicesSilently() {
        pairedDevices.value = bluetoothDeviceProvider.pairedDevices()
        refreshDiagnostics()
    }

    fun refreshBluetoothDevices(): Boolean {
        refreshPairedDevicesSilently()
        return if (pairedDevices.value.isEmpty()) {
            postMessage(MessageKind.Warning, s(R.string.vm_no_paired_devices_or_bluetooth_permission))
            true
        } else {
            false
        }
    }

    fun showMessage(text: String, kind: MessageKind) {
        message.value = UiMessage(text, kind)
    }

    private fun postMessage(kind: MessageKind, text: String) {
        message.value = UiMessage(text, kind)
    }

    fun clearMessage(shown: UiMessage) {
        if (message.value == shown) {
            message.value = null
        }
    }

    fun setBluetoothEventNotificationDelayMinutes(minutes: Int) {
        notificationDelaySettings.setBluetoothEventDelayMinutes(minutes)
        postMessage(MessageKind.Success, if (minutes == 0) {
            s(R.string.vm_notification_delay_immediate)
        } else {
            s(R.string.vm_notification_delay_minutes, minutes)
        })
    }

    fun exportBackup(uri: Uri) {
        if (backupOperationInProgress.value) return
        viewModelScope.launch {
            backupOperationInProgress.value = true
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { postMessage(MessageKind.Success, s(R.string.vm_backup_exported)) }
                .onFailure { error -> postMessage(MessageKind.Error, backupErrorMessage(error, exporting = true)) }
            backupOperationInProgress.value = false
        }
    }

    fun prepareBackupImport(uri: Uri) {
        if (backupOperationInProgress.value) return
        viewModelScope.launch {
            backupOperationInProgress.value = true
            runCatching { backupManager.readFrom(uri) }
                .onSuccess { document ->
                    pendingBackupImport = document
                    backupImportSummary.value = document.summary()
                }
                .onFailure { error ->
                    pendingBackupImport = null
                    backupImportSummary.value = null
                    postMessage(MessageKind.Error, backupErrorMessage(error, exporting = false))
                }
            backupOperationInProgress.value = false
        }
    }

    fun dismissBackupImport() {
        if (backupOperationInProgress.value) return
        pendingBackupImport = null
        backupImportSummary.value = null
    }

    fun confirmBackupImport() {
        if (backupOperationInProgress.value) return
        val document = pendingBackupImport ?: return
        viewModelScope.launch {
            backupOperationInProgress.value = true
            runCatching { backupManager.import(document) }
                .onSuccess { result ->
                    pendingBackupImport = null
                    backupImportSummary.value = null
                    clearVehicleSelection()
                    refreshBluetoothDevices()
                    refreshDiagnostics()
                    if (result.warnings.isEmpty()) {
                        postMessage(MessageKind.Success, s(R.string.vm_backup_imported))
                    } else {
                        postMessage(MessageKind.Warning, s(R.string.vm_backup_imported_with_warnings))
                    }
                }
                .onFailure { error ->
                    postMessage(MessageKind.Error, backupErrorMessage(error, exporting = false))
                }
            backupOperationInProgress.value = false
        }
    }

    private fun backupErrorMessage(error: Throwable, exporting: Boolean): String {
        return when (error) {
            is BackupFileException -> when (error.error) {
                BackupFileError.TooLarge -> s(R.string.vm_backup_too_large)
                BackupFileError.CannotRead -> s(R.string.vm_backup_could_not_read)
                BackupFileError.CannotWrite -> s(R.string.vm_backup_could_not_write)
            }
            is BackupValidationException -> when (error.error) {
                BackupValidationError.UnsupportedVersion -> s(R.string.vm_backup_unsupported_version)
                else -> s(R.string.vm_backup_invalid_file)
            }
            else -> if (exporting) {
                s(R.string.vm_backup_could_not_write)
            } else {
                s(R.string.vm_backup_could_not_import)
            }
        }
    }

    fun selectVehicle(vehicleId: Long) {
        selectedVehicleId.value = vehicleId
        refreshDiagnostics()
    }

    private fun clearVehicleSelection() {
        selectedVehicleId.value = null
        refreshDiagnostics()
    }

    fun addVehicle(device: PairedBluetoothDevice) {
        viewModelScope.launch {
            configurationMutationLock.withLock {
                val id = repository.addVehicle(device.name, device.address)
                selectVehicle(id)
                postMessage(MessageKind.Success, s(R.string.vm_car_added, device.name))
            }
        }
    }

    fun removeVehicle(vehicleId: Long) {
        viewModelScope.launch {
            runCatching {
                reminderDeletionManager.deleteVehicle(vehicleId)
            }.onSuccess {
                if (selectedVehicleId.value == vehicleId) clearVehicleSelection()
                postMessage(MessageKind.Success, s(R.string.vm_car_deleted))
            }.onFailure {
                postMessage(MessageKind.Error, s(R.string.vm_could_not_delete_car))
            }
        }
    }

    fun addChecklistItems(
        vehicleIds: List<Long>,
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        critical: Boolean,
        eligibleFromMillis: Long? = null,
        onFailed: () -> Unit = {},
        onAdded: () -> Unit = {},
    ) {
        viewModelScope.launch {
            configurationMutationLock.withLock {
                runCatching {
                    val targetVehicleIds = vehicleIds.distinct()
                    if (targetVehicleIds.isEmpty()) {
                        error("No vehicle selected")
                    }
                    repository.addChecklistItems(
                        vehicleIds = targetVehicleIds,
                        label = label,
                        trigger = trigger,
                        recurrence = recurrence,
                        activeDaysMask = activeDaysMask,
                        eligibleFromMillis = eligibleFromMillis,
                        critical = critical,
                    )
                }.onSuccess {
                    val cleanLabel = label.trim()
                    postMessage(MessageKind.Success, if (it == 1) {
                        s(R.string.vm_reminder_added, cleanLabel)
                    } else {
                        p(R.plurals.vm_reminder_added_to_cars, it, it, cleanLabel)
                    })
                    onAdded()
                }.onFailure {
                    if (vehicleIds.isEmpty()) {
                        postMessage(MessageKind.Warning, s(R.string.message_configure_car_first))
                    } else {
                        postMessage(MessageKind.Error, s(R.string.vm_could_not_add_reminder))
                    }
                    onFailed()
                }
            }
        }
    }

    fun updateChecklistItem(
        itemId: Long,
        vehicleIds: List<Long>,
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        critical: Boolean,
        eligibleFromMillis: Long? = null,
        onUpdated: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val targetVehicleIds = vehicleIds.distinct()
                repository.updateChecklistItem(
                    itemId = itemId,
                    vehicleIds = targetVehicleIds,
                    label = label,
                    trigger = trigger,
                    recurrence = recurrence,
                    activeDaysMask = activeDaysMask,
                    eligibleFromMillis = eligibleFromMillis,
                    critical = critical,
                )
            }.onSuccess { updatedCount ->
                if (updatedCount <= 0) {
                    postMessage(MessageKind.Error, s(R.string.vm_could_not_update_reminder))
                    return@onSuccess
                }
                val cleanLabel = label.trim()
                postMessage(MessageKind.Success, if (updatedCount == 1) {
                    s(R.string.vm_reminder_updated, cleanLabel)
                } else {
                    p(R.plurals.vm_reminder_updated_in_cars, updatedCount, updatedCount, cleanLabel)
                })
                onUpdated()
            }.onFailure {
                postMessage(MessageKind.Error, s(R.string.vm_could_not_update_reminder))
            }
        }
    }

    fun removeChecklistItem(itemId: Long, onRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                reminderDeletionManager.deleteChecklistItem(itemId)
            }.onSuccess { deleted ->
                if (!deleted) {
                    postMessage(MessageKind.Error, s(R.string.vm_could_not_delete_reminder))
                    return@onSuccess
                }
                postMessage(MessageKind.Success, s(R.string.vm_reminder_deleted))
                onRemoved()
            }.onFailure {
                postMessage(MessageKind.Error, s(R.string.vm_could_not_delete_reminder))
            }
        }
    }

    fun completeReminder(eventId: Long) {
        viewModelScope.launch {
            operationCoordinator.runExclusive { completeEventLocked(eventId) }
            postMessage(MessageKind.Success, s(R.string.vm_reminder_completed))
        }
    }

    /** Must run inside [operationCoordinator]. */
    private suspend fun completeEventLocked(eventId: Long) {
        repository.completeReminderEvent(
            eventId = eventId,
            completedItemIds = reminderNotifier.shownReminderIds(eventId),
        )
        reminderNotifier.dismissReminder(eventId)
        reminderDisplayRefresh.value = reminderDisplayRefresh.value + 1
    }

    fun completeReminderItem(eventId: Long, itemId: Long) {
        viewModelScope.launch {
            val eventCompleted = operationCoordinator.runExclusive { completeItemLocked(eventId, itemId) }
            postMessage(
                MessageKind.Success,
                s(if (eventCompleted) R.string.vm_reminder_completed else R.string.vm_item_completed),
            )
        }
    }

    /** Must run inside [operationCoordinator]. Returns true when completing the item also completed the event. */
    private suspend fun completeItemLocked(eventId: Long, itemId: Long): Boolean {
        when (repository.completeReminderEventItem(eventId, itemId)) {
            ReminderEventItemCompletion.ItemCompleted -> {
                reminderNotifier.dismissReminder(eventId, itemId)
                reminderDisplayRefresh.value = reminderDisplayRefresh.value + 1
                return false
            }
            ReminderEventItemCompletion.EventCompleted -> {
                reminderNotifier.dismissReminder(eventId)
                reminderDisplayRefresh.value = reminderDisplayRefresh.value + 1
                return true
            }
            ReminderEventItemCompletion.NoPersistedItem -> Unit
        }
        reminderNotifier.markReminderCompleted(eventId, itemId)
        repository.deactivateCompletedOneTimeChecklistItems(listOf(itemId))
        reminderNotifier.dismissReminder(eventId, itemId)
        val shownIds = reminderNotifier.shownReminderIds(eventId)
        val completedIds = reminderNotifier.completedReminderIds(eventId)
        val eventCompleted = shownIds.isNotEmpty() && shownIds.all { it in completedIds }
        if (eventCompleted) {
            repository.completeReminderEvent(eventId, completedIds)
            reminderNotifier.dismissReminder(eventId)
        }
        reminderDisplayRefresh.value = reminderDisplayRefresh.value + 1
        return eventCompleted
    }

    /**
     * Runs a non-add voice command (mark / snooze / complete all) for [vehicleId]. With [speak] the result is also
     * read aloud (hands-free mode); otherwise it is only shown.
     */
    fun runVoiceReminderCommand(command: VoiceCommand, vehicleId: Long?, speak: Boolean) {
        when (val action = handsFreeVoiceReminderAction(command, vehicleId)) {
            is HandsFreeVoiceReminderAction.MarkItem -> viewModelScope.launch {
                markItemByVoice(action.command.label, vehicleId!!, speak)
            }
            HandsFreeVoiceReminderAction.Snooze -> viewModelScope.launch { snoozeByVoice(vehicleId!!, speak) }
            HandsFreeVoiceReminderAction.CompleteAll -> viewModelScope.launch {
                requestCompleteAllByVoice(vehicleId!!, speak)
            }
            HandsFreeVoiceReminderAction.MissingVehicle ->
                voiceFeedback(speak, MessageKind.Warning, s(R.string.message_configure_car_first))
            is HandsFreeVoiceReminderAction.AddReminder,
            HandsFreeVoiceReminderAction.UnsupportedCommand ->
                voiceFeedback(speak, MessageKind.Info, s(R.string.vm_say_remind_me_followed_by_reminder))
        }
    }

    private fun voiceFeedback(speak: Boolean, kind: MessageKind, text: String) {
        if (speak) announceVoiceMessage(text, kind) else postMessage(kind, text)
    }

    private suspend fun openEventsForVehicle(vehicleId: Long): List<OpenReminderEventUiState> =
        openReminderEvents(
            repository.observeOpenReminderEvents().first(),
            repository.observeReminderEventItems().first(),
        ).filter { it.event.vehicleId == vehicleId && it.event.status in openReminderStatuses }

    private suspend fun markItemByVoice(label: String, vehicleId: Long, speak: Boolean) {
        val events = openEventsForVehicle(vehicleId)
        if (events.isEmpty()) {
            voiceFeedback(speak, MessageKind.Info, s(R.string.voice_no_open_alerts))
            return
        }
        val candidates = events.flatMap { openEvent ->
            openEvent.items.map { item ->
                VoiceItemCandidate(openEvent.event.id, item.id, item.label, openEvent.event.disconnectedAtMillis)
            }
        }
        when (val match = matchVoiceItem(label, candidates)) {
            is VoiceItemMatch.Single -> {
                val candidate = match.candidate
                // Matching ran outside the lock: re-check that the event and item are still pending before completing.
                val completed = operationCoordinator.runExclusive {
                    val event = repository.reminderEventById(candidate.eventId)
                    val stillPending = event != null && event.status in openReminderStatuses &&
                        openEventsForVehicle(vehicleId).any { open ->
                            open.event.id == candidate.eventId && open.items.any { it.id == candidate.itemId }
                        }
                    if (stillPending) completeItemLocked(candidate.eventId, candidate.itemId)
                    stillPending
                }
                if (completed) {
                    voiceFeedback(speak, MessageKind.Success, s(R.string.voice_marked, candidate.label))
                } else {
                    voiceFeedback(speak, MessageKind.Info, s(R.string.vm_alert_no_longer_pending))
                }
            }
            is VoiceItemMatch.Ambiguous -> {
                val labels = match.candidates.map { it.label }.distinct().take(MAX_VOICE_AMBIGUOUS_LABELS)
                voiceFeedback(speak, MessageKind.Info, s(R.string.voice_mark_which, labels.joinToString(", ")))
            }
            VoiceItemMatch.NoMatch ->
                voiceFeedback(speak, MessageKind.Warning, s(R.string.voice_mark_not_found, label))
        }
    }

    private suspend fun snoozeByVoice(vehicleId: Long, speak: Boolean) {
        val openEvents = openEventsForVehicle(vehicleId).map { it.event }
        val event = voiceSnoozeTarget(openEvents, vehicleId)
        if (event == null) {
            voiceFeedback(speak, MessageKind.Info, s(R.string.voice_no_open_alerts))
            return
        }
        // itemId = -1 snoozes the whole event, like the notification's snooze button.
        if (reminderSnoozer.snooze(event.id, -1L)) {
            voiceFeedback(speak, MessageKind.Success, s(R.string.voice_snoozed))
        } else {
            voiceFeedback(speak, MessageKind.Warning, s(R.string.voice_snooze_failed))
        }
    }

    private suspend fun requestCompleteAllByVoice(vehicleId: Long, speak: Boolean) {
        val withItems = openEventsForVehicle(vehicleId).filter { it.items.isNotEmpty() }
        val event = latestOpenReminderEvent(withItems.map { it.event }, vehicleId)
        if (event == null) {
            voiceFeedback(speak, MessageKind.Info, s(R.string.voice_no_open_alerts))
            return
        }
        // Never immediate: the UI asks for a yes/no first.
        completeAllConfirmation.value = VoiceCompleteAllUiState(
            eventId = event.id,
            count = withItems.first { it.event.id == event.id }.items.size,
        )
    }

    fun confirmVoiceCompleteAll(speak: Boolean) {
        val pending = completeAllConfirmation.value ?: return
        completeAllConfirmation.value = null
        viewModelScope.launch {
            val completed = operationCoordinator.runExclusive {
                val event = repository.reminderEventById(pending.eventId)
                val open = event != null && event.status in openReminderStatuses
                if (open) completeEventLocked(pending.eventId)
                open
            }
            if (completed) {
                voiceFeedback(speak, MessageKind.Success, s(R.string.vm_reminder_completed))
            } else {
                voiceFeedback(speak, MessageKind.Info, s(R.string.vm_alert_no_longer_pending))
            }
        }
    }

    fun cancelVoiceCompleteAll(speak: Boolean) {
        if (completeAllConfirmation.value == null) return
        completeAllConfirmation.value = null
        voiceFeedback(speak, MessageKind.Info, s(R.string.voice_complete_all_cancelled))
    }

    fun openReminderEvent(eventId: Long) {
        viewModelScope.launch {
            val event = repository.reminderEventById(eventId)
            if (event == null || event.status !in openReminderStatuses) {
                postMessage(MessageKind.Info, s(R.string.vm_alert_no_longer_pending))
                return@launch
            }
            selectVehicle(event.vehicleId)
        }
    }

    fun prepareVoiceReminderDraft(hypotheses: List<String>): Boolean {
        val text = hypotheses.firstOrNull().orEmpty()
        val command = voiceCommandParser.parseBest(hypotheses, implicitAdd = true)
        return when (command) {
            is VoiceCommand.AddItem -> {
                voiceReminderDraft.value = VoiceReminderDraftUiState(
                    draftId = System.currentTimeMillis(),
                    label = command.label.toReminderLabel(),
                    trigger = command.trigger,
                )
                postMessage(MessageKind.Info, s(R.string.vm_review_and_save, command.label.toReminderLabel()))
                true
            }
            is VoiceCommand.Unknown -> {
                postMessage(MessageKind.Warning, s(R.string.vm_did_not_understand, text))
                false
            }
            is VoiceCommand.CompleteAll,
            is VoiceCommand.MarkItem,
            is VoiceCommand.Snooze -> {
                runVoiceReminderCommand(command, uiState.value.selectedVehicleId ?: selectedVehicleId.value, speak = false)
                false
            }
        }
    }

    fun saveHandsFreeVoiceReminder(
        command: VoiceCommand.AddItem,
        targetVehicleId: Long?,
        onSaved: () -> Unit = {},
    ) {
        when (val action = handsFreeVoiceReminderAction(command, targetVehicleId)) {
            is HandsFreeVoiceReminderAction.AddReminder -> {
                addChecklistItems(
                    vehicleIds = listOfNotNull(targetVehicleId),
                    label = action.command.label.toReminderLabel(),
                    trigger = action.command.trigger,
                    recurrence = ReminderRecurrence.Once,
                    activeDaysMask = ActiveDays.All,
                    critical = false,
                    onFailed = { announceVoiceMessage(s(R.string.voice_save_failed), MessageKind.Error) },
                    onAdded = {
                        announceVoiceMessage(s(R.string.voice_saved), MessageKind.Success)
                        onSaved()
                    },
                )
            }
            HandsFreeVoiceReminderAction.MissingVehicle -> {
                announceVoiceMessage(s(R.string.message_configure_car_first), MessageKind.Warning)
            }
            is HandsFreeVoiceReminderAction.MarkItem,
            HandsFreeVoiceReminderAction.Snooze,
            HandsFreeVoiceReminderAction.CompleteAll,
            HandsFreeVoiceReminderAction.UnsupportedCommand -> {
                announceVoiceMessage(s(R.string.vm_say_remind_me_followed_by_reminder), MessageKind.Info)
            }
        }
    }

    /** Pre-fills the new-reminder editor with a suggested reminder; the user still reviews and saves it. */
    fun prepareExampleDraft(
        label: String,
        trigger: ReminderTrigger,
        recurrence: ReminderRecurrence,
        activeDaysMask: Int,
        critical: Boolean,
    ) {
        voiceReminderDraft.value = VoiceReminderDraftUiState(
            draftId = System.currentTimeMillis(),
            label = label.take(DataLimits.ReminderLabelMaxChars),
            trigger = trigger,
            recurrence = recurrence,
            activeDaysMask = activeDaysMask,
            critical = critical,
        )
    }

    fun clearVoiceReminderDraft() {
        voiceReminderDraft.value = null
    }

    fun refreshDiagnostics() {
        diagnosticsRefresh.value = diagnosticsRefresh.value + 1
    }

    fun testConnectAlert() {
        testAlert(ReminderTrigger.Connect)
    }

    fun testDisconnectAlert() {
        testAlert(ReminderTrigger.Disconnect)
    }

    private fun testAlert(trigger: ReminderTrigger) {
        val state = uiState.value
        val vehicle = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }
        if (vehicle == null) {
            postMessage(MessageKind.Warning, s(R.string.vm_add_or_select_car_test_alerts))
            return
        }
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            val dueItems = dueReminderItems(
                checklist = repository.activeChecklist(vehicle.id, trigger),
                nowMillis = nowMillis,
            )
            if (dueItems.isEmpty()) {
                diagnosticsStore.record(
                    vehicleId = vehicle.id,
                    trigger = trigger,
                    result = ConnectionDiagnosticResult.NoDueReminders,
                    occurredAtMillis = nowMillis,
                )
                refreshDiagnostics()
                postMessage(MessageKind.Info, s(R.string.vm_no_applicable_reminders_for_test))
                return@launch
            }
            val shown = reminderNotifier.showReminderPreview(
                vehicleName = vehicle.name,
                trigger = trigger,
                reminderItems = dueItems.map { ReminderNotificationItem(it.id, it.label) },
            )
            diagnosticsStore.record(
                vehicleId = vehicle.id,
                trigger = trigger,
                result = if (shown) ConnectionDiagnosticResult.TestShown else ConnectionDiagnosticResult.TestBlocked,
                occurredAtMillis = nowMillis,
            )
            refreshDiagnostics()
            if (shown) {
                postMessage(
                    MessageKind.Success,
                    p(
                        R.plurals.vm_test_sent_applicable_reminders,
                        dueItems.size,
                        dueItems.size,
                    ),
                )
            } else {
                postMessage(MessageKind.Error, s(R.string.vm_could_not_show_test_review_notifications))
            }
        }
    }

    private fun diagnosticsState(
        state: MementoCarUiState,
        diagnostics: Map<Long, ConnectionDiagnosticsState>,
    ): DiagnosticsUiState {
        val vehicle = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }
        val snapshot = diagnosticsProvider.snapshot()
        val vehicleHasBluetooth = vehicle?.let(repository::vehicleHasBluetooth) == true
        val connectionDiagnostics = vehicle?.let { diagnostics[it.id] }
        val latestEvent = connectionDiagnostics?.latestEvent
        val latestManualTest = connectionDiagnostics?.latestManualTest
        val latestAutomaticSuccess = connectionDiagnostics?.latestAutomaticSuccess
        val hasVehicle = vehicle != null
        val hasReminders = state.checklist.isNotEmpty()
        val hasAutomaticSuccess = latestAutomaticSuccess != null
        val readinessInput = SystemReadinessInput(
            hasVehicle = hasVehicle,
            vehicleHasBluetooth = vehicleHasBluetooth,
            hasBluetoothPermission = snapshot.hasBluetoothPermission,
            isBluetoothAvailable = snapshot.isBluetoothAvailable,
            isBluetoothEnabled = snapshot.isBluetoothEnabled,
            canPostNotifications = snapshot.notificationReadiness.canPost,
            hasReminders = hasReminders,
            hasSuccessfulDiagnosticEvent = hasAutomaticSuccess,
        )
        val setupAction = nextSystemSetupAction(
            SystemSetupInput(
                hasVehicle = hasVehicle,
                vehicleHasBluetooth = vehicleHasBluetooth,
                hasBluetoothPermission = snapshot.hasBluetoothPermission,
                isBluetoothAvailable = snapshot.isBluetoothAvailable,
                isBluetoothEnabled = snapshot.isBluetoothEnabled,
                canPostNotifications = snapshot.notificationReadiness.canPost,
                notificationPermissionDenied = snapshot.notificationReadiness.reason ==
                    NotificationBlockReason.PermissionDenied,
                hasReminders = hasReminders,
                hasSuccessfulDiagnosticEvent = hasAutomaticSuccess,
            ),
        )
        val readiness = evaluateSystemReadiness(readinessInput)
        return DiagnosticsUiState(
            level = readiness.level.toUiLevel(),
            title = readiness.level.title(context),
            detail = readiness.reason.label(context),
            setupAction = setupAction,
            checks = listOf(
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_car),
                    detail = vehicle?.name ?: s(R.string.diagnostic_no_car_selected),
                    level = if (vehicle != null) DiagnosticsLevelUiState.Ready else DiagnosticsLevelUiState.Blocked,
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_car_bluetooth),
                    detail = if (vehicleHasBluetooth) {
                        s(R.string.diagnostic_linked_to_device)
                    } else {
                        s(R.string.diagnostic_no_bluetooth_car)
                    },
                    level = if (vehicleHasBluetooth) DiagnosticsLevelUiState.Ready else DiagnosticsLevelUiState.Blocked,
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_bluetooth_permission),
                    detail = if (snapshot.hasBluetoothPermission) s(R.string.diagnostic_granted) else s(R.string.diagnostic_pending),
                    level = if (snapshot.hasBluetoothPermission) DiagnosticsLevelUiState.Ready else DiagnosticsLevelUiState.Blocked,
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.bluetooth_section),
                    detail = when {
                        !snapshot.isBluetoothAvailable -> s(R.string.diagnostic_unavailable_on_device)
                        snapshot.isBluetoothEnabled -> s(R.string.diagnostic_enabled)
                        else -> s(R.string.diagnostic_off)
                    },
                    level = if (snapshot.isBluetoothEnabled) DiagnosticsLevelUiState.Ready else DiagnosticsLevelUiState.Blocked,
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.notifications),
                    detail = if (snapshot.notificationReadiness.canPost) {
                        s(R.string.diagnostic_active)
                    } else {
                        snapshot.notificationReadiness.reason?.label(context) ?: s(R.string.diagnostic_blocked)
                    },
                    level = if (snapshot.notificationReadiness.canPost) {
                        DiagnosticsLevelUiState.Ready
                    } else {
                        DiagnosticsLevelUiState.Blocked
                    },
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.reminders_title),
                    detail = if (state.checklist.isEmpty()) {
                        s(R.string.diagnostic_no_reminders_for_car)
                    } else {
                        p(R.plurals.diagnostic_configured_count, state.checklist.size, state.checklist.size)
                    },
                    level = if (state.checklist.isEmpty()) DiagnosticsLevelUiState.Attention else DiagnosticsLevelUiState.Ready,
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_notification_test),
                    detail = latestManualTest.notificationTestDetail(context),
                    level = latestManualTest.notificationTestLevel(),
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_automatic_alert),
                    detail = latestAutomaticSuccess.automaticSuccessDetail(context),
                    level = if (latestAutomaticSuccess == null) {
                        DiagnosticsLevelUiState.Attention
                    } else {
                        DiagnosticsLevelUiState.Ready
                    },
                ),
                DiagnosticCheckUiState(
                    label = s(R.string.diagnostic_microphone),
                    detail = if (snapshot.hasMicrophonePermission) {
                        s(R.string.diagnostic_available_for_dictation)
                    } else {
                        s(R.string.diagnostic_only_affects_voice_dictation)
                    },
                    level = if (snapshot.hasMicrophonePermission) DiagnosticsLevelUiState.Ready else DiagnosticsLevelUiState.Attention,
                ),
            ),
            latestEvent = latestEvent?.toUiState(context),
            testAlertsBlockedReason = testAlertsBlockedReason(vehicle != null, snapshot.notificationReadiness.canPost),
        )
    }

    private companion object {
        val openReminderStatuses = setOf(ReminderStatus.Pending, ReminderStatus.Snoozed)
        const val MAX_VOICE_AMBIGUOUS_LABELS = 3
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeSelectedChecklist(
    selectedVehicleId: Flow<Long?>,
    observeChecklist: (Long) -> Flow<List<ChecklistItemEntity>>,
): Flow<List<ChecklistItemEntity>> {
    return selectedVehicleId.flatMapLatest { vehicleId ->
        if (vehicleId == null) flowOf(emptyList()) else observeChecklist(vehicleId)
    }
}

private fun SystemReadinessLevel.toUiLevel(): DiagnosticsLevelUiState {
    return when (this) {
        SystemReadinessLevel.Ready -> DiagnosticsLevelUiState.Ready
        SystemReadinessLevel.Attention -> DiagnosticsLevelUiState.Attention
        SystemReadinessLevel.Blocked -> DiagnosticsLevelUiState.Blocked
    }
}

private fun SystemReadinessLevel.title(context: AppTextResolver): String {
    return when (this) {
        SystemReadinessLevel.Ready -> context.getString(R.string.readiness_ready_title)
        SystemReadinessLevel.Attention -> context.getString(R.string.readiness_attention_title)
        SystemReadinessLevel.Blocked -> context.getString(R.string.readiness_blocked_title)
    }
}

private fun SystemReadinessReason.label(context: AppTextResolver): String {
    return when (this) {
        SystemReadinessReason.MissingVehicle -> context.getString(R.string.readiness_missing_vehicle)
        SystemReadinessReason.VehicleMissingBluetooth -> context.getString(R.string.readiness_vehicle_missing_bluetooth)
        SystemReadinessReason.MissingBluetoothPermission -> context.getString(R.string.readiness_missing_bluetooth_permission)
        SystemReadinessReason.BluetoothUnavailable -> context.getString(R.string.readiness_bluetooth_unavailable)
        SystemReadinessReason.BluetoothDisabled -> context.getString(R.string.readiness_bluetooth_disabled)
        SystemReadinessReason.NotificationsBlocked -> context.getString(R.string.readiness_notifications_blocked)
        SystemReadinessReason.MissingReminders -> context.getString(R.string.readiness_missing_reminders)
        SystemReadinessReason.MissingSuccessfulDiagnosticEvent -> context.getString(R.string.readiness_missing_successful_diagnostic)
        SystemReadinessReason.Ready -> context.getString(R.string.readiness_ready_detail)
    }
}

private fun io.nodetech.mementocar.platform.NotificationBlockReason.label(context: AppTextResolver): String {
    return when (this) {
        io.nodetech.mementocar.platform.NotificationBlockReason.PermissionDenied -> context.getString(R.string.notification_block_permission_pending)
        io.nodetech.mementocar.platform.NotificationBlockReason.AppNotificationsDisabled -> context.getString(R.string.notification_block_disabled_android)
        io.nodetech.mementocar.platform.NotificationBlockReason.ChannelDisabled -> context.getString(R.string.notification_block_channel_disabled)
    }
}

private fun ConnectionDiagnosticEvent.toUiState(context: AppTextResolver): LatestDiagnosticEventUiState {
    return LatestDiagnosticEventUiState(
        title = context.getString(R.string.diagnostic_event_title, trigger.shortLabel(context), result.label(context)),
        detail = result.detail(context),
        occurredAtMillis = occurredAtMillis,
    )
}

private fun ReminderTrigger.shortLabel(context: AppTextResolver): String {
    return when (this) {
        ReminderTrigger.Connect -> context.getString(R.string.trigger_connect)
        ReminderTrigger.Disconnect -> context.getString(R.string.trigger_disconnect)
    }
}

private fun ConnectionDiagnosticResult.label(context: AppTextResolver): String {
    return when (this) {
        ConnectionDiagnosticResult.ReminderShown -> context.getString(R.string.diagnostic_result_reminder_shown)
        ConnectionDiagnosticResult.ReminderScheduled -> context.getString(R.string.diagnostic_result_reminder_scheduled)
        ConnectionDiagnosticResult.NoDueReminders -> context.getString(R.string.diagnostic_result_no_due_reminders)
        ConnectionDiagnosticResult.DuplicateIgnored -> context.getString(R.string.diagnostic_result_duplicate_ignored)
        ConnectionDiagnosticResult.NotificationsBlocked -> context.getString(R.string.diagnostic_result_notifications_blocked)
        ConnectionDiagnosticResult.TestShown -> context.getString(R.string.diagnostic_result_test_shown)
        ConnectionDiagnosticResult.TestBlocked -> context.getString(R.string.diagnostic_result_test_blocked)
    }
}

private fun ConnectionDiagnosticResult.detail(androidContext: AppTextResolver): String {
    return when (this) {
        ConnectionDiagnosticResult.ReminderShown -> androidContext.getString(R.string.diagnostic_detail_reminder_shown)
        ConnectionDiagnosticResult.ReminderScheduled -> androidContext.getString(R.string.diagnostic_detail_reminder_scheduled)
        ConnectionDiagnosticResult.NoDueReminders -> androidContext.getString(R.string.diagnostic_detail_no_due_reminders)
        ConnectionDiagnosticResult.DuplicateIgnored -> androidContext.getString(R.string.diagnostic_detail_duplicate_ignored)
        ConnectionDiagnosticResult.NotificationsBlocked -> androidContext.getString(R.string.diagnostic_detail_notifications_blocked)
        ConnectionDiagnosticResult.TestShown -> androidContext.getString(R.string.diagnostic_detail_test_shown)
        ConnectionDiagnosticResult.TestBlocked -> androidContext.getString(R.string.diagnostic_detail_test_blocked)
    }
}

private fun ConnectionDiagnosticEvent?.notificationTestDetail(context: AppTextResolver): String {
    return when (this?.result) {
        ConnectionDiagnosticResult.TestShown -> context.getString(
            R.string.diagnostic_manual_test_success_at,
            occurredAtMillis.shortDateTime(),
        )
        ConnectionDiagnosticResult.TestBlocked -> context.getString(
            R.string.diagnostic_manual_test_blocked_at,
            occurredAtMillis.shortDateTime(),
        )
        null -> context.getString(R.string.diagnostic_no_recent_manual_test)
        ConnectionDiagnosticResult.ReminderShown,
        ConnectionDiagnosticResult.ReminderScheduled,
        ConnectionDiagnosticResult.NoDueReminders,
        ConnectionDiagnosticResult.DuplicateIgnored,
        ConnectionDiagnosticResult.NotificationsBlocked -> context.getString(R.string.diagnostic_no_recent_manual_test)
    }
}

private fun ConnectionDiagnosticEvent?.notificationTestLevel(): DiagnosticsLevelUiState {
    return when (this?.result) {
        ConnectionDiagnosticResult.TestShown -> DiagnosticsLevelUiState.Ready
        ConnectionDiagnosticResult.TestBlocked -> DiagnosticsLevelUiState.Blocked
        null,
        ConnectionDiagnosticResult.ReminderShown,
        ConnectionDiagnosticResult.ReminderScheduled,
        ConnectionDiagnosticResult.NoDueReminders,
        ConnectionDiagnosticResult.DuplicateIgnored,
        ConnectionDiagnosticResult.NotificationsBlocked -> DiagnosticsLevelUiState.Attention
    }
}

private fun ConnectionDiagnosticEvent?.automaticSuccessDetail(context: AppTextResolver): String {
    return when (this?.result) {
        ConnectionDiagnosticResult.ReminderShown -> context.getString(
            R.string.diagnostic_last_real_alert_shown_at,
            occurredAtMillis.shortDateTime(),
        )
        ConnectionDiagnosticResult.ReminderScheduled -> context.getString(
            R.string.diagnostic_last_real_alert_scheduled_at,
            occurredAtMillis.shortDateTime(),
        )
        null -> context.getString(R.string.diagnostic_no_automatic_alert_confirmed)
        ConnectionDiagnosticResult.NoDueReminders,
        ConnectionDiagnosticResult.DuplicateIgnored,
        ConnectionDiagnosticResult.NotificationsBlocked,
        ConnectionDiagnosticResult.TestShown,
        ConnectionDiagnosticResult.TestBlocked -> context.getString(R.string.diagnostic_no_automatic_alert_confirmed)
    }
}

private fun Long.shortDateTime(): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
}

internal fun String.toReminderLabel(): String = trim().take(DataLimits.ReminderLabelMaxChars)
