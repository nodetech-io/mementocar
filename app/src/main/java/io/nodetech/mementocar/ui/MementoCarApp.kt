// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.annotation.StringRes
import io.nodetech.mementocar.domain.VoiceCommand
import io.nodetech.mementocar.domain.VoiceCommandParser
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.R
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import java.time.LocalDate

private fun android.content.Context.notificationSettingsIntent(): Intent {
    return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
}

/** Public repository with the corresponding source of this build (GPL-3.0-or-later). */
internal const val SOURCE_CODE_URL = "https://github.com/nodetech-io/mementocar"

private fun privacyPolicyIntent(): Intent {
    return Intent(Intent.ACTION_VIEW, "https://nodetech.io/mementocar/privacy/".toUri())
}

// Only requested on API levels that define them (see needsBluetoothPermission / needsNotificationPermission).
@SuppressLint("InlinedApi")
private const val BLUETOOTH_CONNECT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

@SuppressLint("InlinedApi")
private const val POST_NOTIFICATIONS_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

@Composable
internal fun MementoCarApp(
    viewModel: MementoCarViewModel = hiltViewModel(),
    requestedReminderEventId: Long? = null,
    onReminderEventRequestHandled: () -> Unit = {},
    requestedVoiceReminderStart: Boolean = false,
    onVoiceReminderStartHandled: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPage by rememberSaveable { mutableStateOf(AppPage.Reminders) }
    var editingReminderSnapshot by rememberSaveable(stateSaver = ReminderEditorSaver) {
        mutableStateOf<ChecklistItemEntity?>(null)
    }
    val editingReminderId = editingReminderSnapshot?.id
    var initialSetupHandled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val layoutDirection = LocalLayoutDirection.current
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::prepareBackupImport)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The "no paired devices" warning only makes sense for the Bluetooth permission flow,
        // not after granting notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refreshBluetoothDevices()
        } else {
            viewModel.refreshPairedDevicesSilently()
        }
    }
    var permissionRationale by rememberSaveable { mutableStateOf<PermissionRationale?>(null) }
    var bluetoothPermissionAsked by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    val isGranted = { permission: String ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val needsBluetoothPermission = {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isGranted(Manifest.permission.BLUETOOTH_CONNECT)
    }
    val needsNotificationPermission = {
        Build.VERSION.SDK_INT >= 33 && !isGranted(Manifest.permission.POST_NOTIFICATIONS)
    }
    val openAppSettings = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
            )
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_notification_settings), MessageKind.Error)
        }
    }
    // Runs after the user accepted the explanation dialog. If Android will no longer show its own
    // dialog (denied before and "don't ask again"), send the user to the app settings instead.
    val launchPermissionRequest = { rationale: PermissionRationale ->
        val permission = when (rationale) {
            PermissionRationale.Bluetooth -> BLUETOOTH_CONNECT_PERMISSION
            PermissionRationale.Notifications -> POST_NOTIFICATIONS_PERMISSION
        }
        val asked = when (rationale) {
            PermissionRationale.Bluetooth -> bluetoothPermissionAsked
            PermissionRationale.Notifications -> notificationPermissionAsked
        }
        val activity = context as? Activity
        if (asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            openAppSettings()
        } else {
            when (rationale) {
                PermissionRationale.Bluetooth -> bluetoothPermissionAsked = true
                PermissionRationale.Notifications -> notificationPermissionAsked = true
            }
            permissionLauncher.launch(arrayOf(permission))
        }
    }
    // Asks for one missing permission at a time, always after explaining it.
    val requestMissingPermission = {
        when {
            needsBluetoothPermission() -> permissionRationale = PermissionRationale.Bluetooth
            needsNotificationPermission() -> permissionRationale = PermissionRationale.Notifications
        }
    }
    val openBluetoothSettings = {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_bluetooth_settings), MessageKind.Error)
        }
    }
    val openNotificationSettings = {
        try {
            context.startActivity(context.notificationSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_notification_settings), MessageKind.Error)
        }
    }
    val openPrivacyPolicy = {
        try {
            context.startActivity(privacyPolicyIntent())
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_privacy_policy), MessageKind.Error)
        }
    }
    val openSourceCode = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_CODE_URL.toUri()))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_source_code), MessageKind.Error)
        }
    }
    val openNodetech = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                "https://nodetech.io/?utm_source=mementocar&utm_medium=app&utm_campaign=about".toUri()))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(resources.getString(R.string.message_could_not_open_nodetech), MessageKind.Error)
        }
    }
    // Android 13+ has a per-app language screen backed by res/xml/locales_config.xml; older versions follow the system.
    val openLanguageSettings: (() -> Unit)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()),
                )
            } catch (_: ActivityNotFoundException) {
                viewModel.showMessage(
                    resources.getString(R.string.message_could_not_open_language_settings),
                    MessageKind.Error,
                )
            }
        }
    } else {
        null
    }
    val handleSetupAction = { action: SystemSetupAction ->
        when (action) {
            SystemSetupAction.ConfigureBluetoothVehicle -> {
                selectedPage = AppPage.Settings
                if (needsBluetoothPermission()) {
                    permissionRationale = PermissionRationale.Bluetooth
                } else {
                    viewModel.refreshBluetoothDevices()
                }
            }
            SystemSetupAction.RequestPermissions -> requestMissingPermission()
            SystemSetupAction.BluetoothUnavailable -> selectedPage = AppPage.Verification
            SystemSetupAction.OpenBluetoothSettings -> openBluetoothSettings()
            SystemSetupAction.OpenNotificationSettings -> openNotificationSettings()
            SystemSetupAction.CreateFirstReminder -> {
                viewModel.clearVoiceReminderDraft()
                editingReminderSnapshot = null
                selectedPage = AppPage.NewReminder
            }
            SystemSetupAction.RunFirstTest -> viewModel.testDisconnectAlert()
            SystemSetupAction.Ready -> selectedPage = AppPage.Verification
        }
        Unit
    }
    var stopFeedback: () -> Unit = {}
    val voiceFeedback by viewModel.voiceFeedback.collectAsStateWithLifecycle()
    val voiceCompleteAll by viewModel.voiceCompleteAllConfirmation.collectAsStateWithLifecycle()
    var pendingVoiceGeneration by rememberSaveable { mutableLongStateOf(0L) }
    var pendingVoiceText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVoiceVehicleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingVoiceVehicleName by rememberSaveable { mutableStateOf("") }
    val voiceInput = rememberVoiceReminderInput(
        onReviewResult = { hypotheses ->
            if (viewModel.prepareVoiceReminderDraft(hypotheses)) {
                editingReminderSnapshot = null
                selectedPage = AppPage.NewReminder
            }
        },
        onHandsFreeResult = result@{ hypotheses ->
            if (pendingVoiceText != null) return@result
            editingReminderSnapshot = null
            viewModel.clearVoiceReminderDraft()
            val vehicle = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }
            val command = VoiceCommandParser().parseBest(hypotheses, implicitAdd = true)
            when {
                vehicle == null -> viewModel.announceVoiceMessage(
                    resources.getString(R.string.message_configure_car_first),
                    MessageKind.Warning,
                )
                command is VoiceCommand.Unknown ->
                    viewModel.announceVoiceMessage(
                        resources.getString(R.string.vm_say_remind_me_followed_by_reminder),
                        MessageKind.Info,
                    )
                command !is VoiceCommand.AddItem ->
                    viewModel.runVoiceReminderCommand(command, vehicle.id, speak = true)
                else -> {
                    pendingVoiceVehicleId = vehicle.id
                    pendingVoiceVehicleName = vehicle.name
                    pendingVoiceGeneration++
                    // Keep the exact hypothesis parseBest chose; the dialog re-parses this text.
                    pendingVoiceText = hypotheses.first { VoiceCommandParser().parse(it, implicitAdd = true) == command }
                }
            }
        },
        onMessage = { viewModel.showMessage(it, MessageKind.Info) },
        onFailure = { viewModel.announceVoiceMessage(it, MessageKind.Error) },
    )
    if (!voiceInput.isListening && pendingVoiceText == null && voiceCompleteAll == null) {
        voiceFeedback?.let { feedback ->
            key(feedback.id) {
                stopFeedback = rememberSpokenMessage(
                    prompt = feedback.text,
                    onComplete = { viewModel.clearVoiceFeedback(feedback.id) },
                    onUnavailable = { viewModel.clearVoiceFeedback(feedback.id) },
                )
            }
        }
    }
    val startVoiceForReview = {
        if (pendingVoiceText != null) {
            viewModel.showMessage(resources.getString(R.string.voice_confirmation_retry), MessageKind.Info)
        } else {
            stopFeedback()
            voiceFeedback?.let { viewModel.clearVoiceFeedback(it.id) }
            voiceInput.startForReview()
        }
    }
    val startVoiceForHandsFree = {
        if (pendingVoiceText != null) {
            viewModel.showMessage(resources.getString(R.string.voice_confirmation_retry), MessageKind.Info)
        } else {
            stopFeedback()
            voiceFeedback?.let { viewModel.clearVoiceFeedback(it.id) }
            voiceInput.startForHandsFree()
        }
    }
    val selectedVehicleName = state.vehicles
        .firstOrNull { it.id == state.selectedVehicleId }
        ?.name
    val visibleOpenReminderEvents = state.openReminderEvents
        .filter { it.event.vehicleId == state.selectedVehicleId }
    val selectedVehicleAlertCount = visibleOpenReminderEvents.size
    val targetVehicleIdsFor = { target: ReminderTarget ->
        when (target) {
            ReminderTarget.SelectedVehicle -> listOfNotNull(
                editingReminderSnapshot?.vehicleId ?: state.selectedVehicleId,
            )
            ReminderTarget.AllVehicles -> state.vehicles.map { it.id }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val navigateToReminders = {
        viewModel.clearVoiceReminderDraft()
        editingReminderSnapshot = null
        selectedPage = AppPage.Reminders
    }

    BackHandler(enabled = selectedPage != AppPage.Reminders && selectedPage != AppPage.NewReminder) {
        navigateToReminders()
    }

    LaunchedEffect(Unit) {
        if (!initialSetupHandled) {
            initialSetupHandled = true
            if (requestedVoiceReminderStart) {
                viewModel.refreshDiagnostics()
            } else {
                // No permission prompts on launch: each one is requested in context, with an explanation.
                viewModel.refreshPairedDevicesSilently()
            }
        }
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        if (activity == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // Also picks up devices paired in system settings while the app was in background.
                    viewModel.refreshPairedDevicesSilently()
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(requestedReminderEventId, selectedPage == AppPage.NewReminder) {
        if (selectedPage == AppPage.NewReminder) return@LaunchedEffect
        requestedReminderEventId?.let { eventId ->
            selectedPage = AppPage.Alerts
            viewModel.openReminderEvent(eventId)
            onReminderEventRequestHandled()
        }
    }

    LaunchedEffect(requestedVoiceReminderStart, selectedPage == AppPage.NewReminder) {
        if (selectedPage == AppPage.NewReminder) return@LaunchedEffect
        if (requestedVoiceReminderStart) {
            selectedPage = AppPage.Reminders
            startVoiceForHandsFree()
            onVoiceReminderStartHandled()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(MessageSnackbarVisuals(message))
            viewModel.clearMessage(message)
        }
    }

    MementoCarTheme {
        state.backupImportSummary?.let { summary ->
            BackupImportDialog(
                summary = summary,
                inProgress = state.backupOperationInProgress,
                onConfirm = viewModel::confirmBackupImport,
                onDismiss = viewModel::dismissBackupImport,
            )
        }
        voiceCompleteAll?.let { pending ->
            key(pending.eventId) {
                VoiceCompleteAllConfirmationDialog(
                    count = pending.count,
                    onConfirm = { viewModel.confirmVoiceCompleteAll(speak = true) },
                    onCancel = { viewModel.cancelVoiceCompleteAll(speak = true) },
                )
            }
        }
        pendingVoiceText?.let { text ->
            val generation = pendingVoiceGeneration
            val vehicleId = pendingVoiceVehicleId
            val command = VoiceCommandParser().parse(text, implicitAdd = true) as? VoiceCommand.AddItem
            if (command != null) {
                VoiceReminderConfirmationDialog(
                    initialCommand = command,
                    vehicleName = pendingVoiceVehicleName,
                    onConfirm = { confirmedCommand ->
                        // Consume before launching the write: duplicate callbacks cannot save twice.
                        if (pendingVoiceText != null && pendingVoiceGeneration == generation) {
                            pendingVoiceText = null
                            viewModel.saveHandsFreeVoiceReminder(confirmedCommand, vehicleId) {
                                selectedPage = AppPage.Reminders
                            }
                        }
                    },
                    onCancel = {
                        if (pendingVoiceGeneration == generation) {
                            pendingVoiceText = null
                            viewModel.announceVoiceMessage(resources.getString(R.string.voice_cancelled), MessageKind.Info)
                        }
                    },
                )
            }
        }
        permissionRationale?.let { rationale ->
            PermissionRationaleDialog(
                rationale = rationale,
                onContinue = {
                    permissionRationale = null
                    launchPermissionRequest(rationale)
                },
                onDismiss = { permissionRationale = null },
            )
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                if (selectedPage == AppPage.NewReminder) {
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                } else {
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                },
            ),
            bottomBar = {
                if (selectedPage != AppPage.NewReminder) {
                    Column {
                        // Nothing can be saved without a car, so don't offer the actions yet.
                        if (selectedPage == AppPage.Reminders && state.vehicles.isNotEmpty()) {
                            ReminderActions(
                                isListeningForVoice = voiceInput.isListening,
                                onCreate = {
                                    viewModel.clearVoiceReminderDraft()
                                    editingReminderSnapshot = null
                                    selectedPage = AppPage.NewReminder
                                },
                                onCreateByVoice = startVoiceForReview,
                            )
                        }
                        AppBottomNavigation(
                            selectedPage = selectedPage,
                            onSelected = { selectedPage = it },
                            reminderCount = state.checklist.size,
                            alertCount = selectedVehicleAlertCount,
                        )
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                ) { snackbarData ->
                    val visuals = snackbarData.visuals
                    DismissibleMessageBanner(
                        message = visuals.message,
                        kind = (visuals as? MessageSnackbarVisuals)?.kind ?: MessageKind.Info,
                        onDismiss = snackbarData::dismiss,
                    )
                }
            },
        ) { innerPadding ->
            val contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            )
            Box(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MementoPageContent(
                        page = selectedPage,
                        editingReminderId = editingReminderId,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        contentPadding = contentPadding,
                    ) { displayedPage ->
                        if (displayedPage != AppPage.NewReminder) {
                            item {
                                AppHeader(
                                    vehicleName = selectedVehicleName,
                                    diagnostics = state.diagnostics,
                                )
                            }
                        }

                        when (displayedPage) {
                            AppPage.Reminders -> {
                                item(key = "reminders_header") {
                                    ChecklistHeader(
                                        itemCount = state.checklist.size,
                                        setupAction = state.diagnostics.setupAction,
                                        onSetupAction = handleSetupAction,
                                        onExample = { example ->
                                            viewModel.prepareExampleDraft(
                                                label = resources.getString(example.labelRes),
                                                trigger = example.trigger,
                                                recurrence = example.recurrence,
                                                activeDaysMask = example.activeDaysMask,
                                                critical = example.critical,
                                            )
                                            editingReminderSnapshot = null
                                            selectedPage = AppPage.NewReminder
                                        },
                                    )
                                }
                                items(state.checklist, key = { "reminder_${it.id}" }) { reminder ->
                                    ListCard {
                                        ReminderRow(item = reminder, onEdit = {
                                            viewModel.clearVoiceReminderDraft()
                                            editingReminderSnapshot = reminder
                                            selectedPage = AppPage.NewReminder
                                        })
                                    }
                                }
                            }

                            AppPage.NewReminder -> {
                                val editingReminder = editingReminderSnapshot
                                item {
                                    NewReminderSection(
                                        editingItem = editingReminder,
                                        externalExitRequested = requestedReminderEventId != null || requestedVoiceReminderStart,
                                        onExternalExitCancelled = {
                                            if (requestedReminderEventId != null) onReminderEventRequestHandled()
                                            if (requestedVoiceReminderStart) onVoiceReminderStartHandled()
                                        },
                                        voiceDraft = state.voiceReminderDraft,
                                        onVoiceDraftConsumed = viewModel::clearVoiceReminderDraft,
                                        onVoiceInput = startVoiceForReview,
                                        isListeningForVoice = voiceInput.isListening,
                                        hasVehicles = state.vehicles.isNotEmpty(),
                                        onConfigureVehicle = {
                                            viewModel.clearVoiceReminderDraft()
                                            editingReminderSnapshot = null
                                            handleSetupAction(SystemSetupAction.ConfigureBluetoothVehicle)
                                        },
                                        onBack = {
                                            navigateToReminders()
                                        },
                                        onAdd = { label, trigger, recurrence, activeDaysMask, target, critical, eligibleFromMillis ->
                                            val targetVehicleIds = targetVehicleIdsFor(target)
                                            if (targetVehicleIds.isEmpty()) {
                                                viewModel.showMessage(resources.getString(R.string.message_configure_car_first), MessageKind.Warning)
                                            } else {
                                                viewModel.addChecklistItems(
                                                    vehicleIds = targetVehicleIds,
                                                    label = label,
                                                    trigger = trigger,
                                                    recurrence = recurrence,
                                                    activeDaysMask = activeDaysMask,
                                                    critical = critical,
                                                    eligibleFromMillis = eligibleFromMillis,
                                                    onAdded = {
                                                        viewModel.clearVoiceReminderDraft()
                                                        editingReminderSnapshot = null
                                                        selectedPage = AppPage.Reminders
                                                        if (needsNotificationPermission()) {
                                                            permissionRationale = PermissionRationale.Notifications
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                        onUpdate = { itemId, label, trigger, recurrence, activeDaysMask, target, critical, eligibleFromMillis ->
                                            val targetVehicleIds = targetVehicleIdsFor(target)
                                            viewModel.updateChecklistItem(
                                                itemId = itemId,
                                                vehicleIds = targetVehicleIds,
                                                label = label,
                                                trigger = trigger,
                                                recurrence = recurrence,
                                                activeDaysMask = activeDaysMask,
                                                critical = critical,
                                                eligibleFromMillis = eligibleFromMillis,
                                                onUpdated = {
                                                    viewModel.clearVoiceReminderDraft()
                                                    editingReminderSnapshot = null
                                                    selectedPage = AppPage.Reminders
                                                },
                                            )
                                        },
                                        onDelete = { itemId ->
                                            viewModel.removeChecklistItem(
                                                itemId = itemId,
                                                onRemoved = {
                                                    viewModel.clearVoiceReminderDraft()
                                                    editingReminderSnapshot = null
                                                    selectedPage = AppPage.Reminders
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            AppPage.Alerts -> {
                                item {
                                    AlertsSection(
                                        events = visibleOpenReminderEvents,
                                        onComplete = viewModel::completeReminder,
                                        onCompleteItem = viewModel::completeReminderItem,
                                    )
                                }
                            }

                            AppPage.Verification -> {
                                item {
                                    VerificationSection(
                                        diagnostics = state.diagnostics,
                                        onSetupAction = handleSetupAction,
                                        onTestConnect = viewModel::testConnectAlert,
                                        onTestDisconnect = viewModel::testDisconnectAlert,
                                        onOpenBluetoothSettings = openBluetoothSettings,
                                        onOpenNotificationSettings = openNotificationSettings,
                                    )
                                }
                            }

                            AppPage.Settings -> {
                                item {
                                    VehiclesSection(
                                        vehicles = state.vehicles,
                                        selectedVehicleId = state.selectedVehicleId,
                                        onSelect = viewModel::selectVehicle,
                                        onRemove = viewModel::removeVehicle,
                                    )
                                }
                                item {
                                    AddCarSection(
                                        devices = state.pairedDevices,
                                        onUse = { device ->
                                            viewModel.addVehicle(device)
                                            navigateToReminders()
                                        },
                                        needsPermission = needsBluetoothPermission(),
                                        onGrantPermission = { permissionRationale = PermissionRationale.Bluetooth },
                                        onRefresh = {
                                            if (needsBluetoothPermission()) {
                                                permissionRationale = PermissionRationale.Bluetooth
                                            } else {
                                                viewModel.refreshBluetoothDevices()
                                            }
                                        },
                                        onOpenBluetoothSettings = openBluetoothSettings,
                                    )
                                }
                                item {
                                    AdvancedSettingsSection {
                                        NotificationDelaySection(
                                            selectedMinutes = state.bluetoothEventNotificationDelayMinutes,
                                            onSelected = viewModel::setBluetoothEventNotificationDelayMinutes,
                                        )
                                        DataManagementSection(
                                            inProgress = state.backupOperationInProgress,
                                            onExport = {
                                                exportBackupLauncher.launch(
                                                    "memento-car-backup-${LocalDate.now()}.json",
                                                )
                                            },
                                            onImport = {
                                                importBackupLauncher.launch(arrayOf("application/json", "text/json"))
                                            },
                                        )
                                    }
                                }
                                item {
                                    AboutSection(
                                        onOpenLanguageSettings = openLanguageSettings,
                                        onOpenSourceCode = openSourceCode,
                                        onOpenNodetech = openNodetech,
                                        onOpenPrivacyPolicy = openPrivacyPolicy,
                                    )
                                }
                            }
                        }
                    }
                }
                // Content scrolls edge to edge; keep it from showing through the status bar.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

internal enum class AppPage(@param:StringRes val labelRes: Int) {
    Reminders(R.string.reminders_title),
    NewReminder(R.string.new_reminder_title),
    Alerts(R.string.alerts_title),
    Verification(R.string.verification_title),
    Settings(R.string.settings_title),
}

internal val bottomNavigationPages = listOf(
    AppPage.Reminders,
    AppPage.Alerts,
    AppPage.Verification,
    AppPage.Settings,
)

internal enum class ReminderTarget {
    SelectedVehicle,
    AllVehicles,
}

internal class MessageSnackbarVisuals(uiMessage: UiMessage) : SnackbarVisuals {
    override val message: String = uiMessage.text
    val kind: MessageKind = uiMessage.kind
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = messageSnackbarDuration(uiMessage.kind)
}

/** Warnings and errors stay longer on screen because they are more important to read. */
internal fun messageSnackbarDuration(kind: MessageKind): SnackbarDuration = when (kind) {
    MessageKind.Success, MessageKind.Info -> SnackbarDuration.Short
    MessageKind.Warning, MessageKind.Error -> SnackbarDuration.Long
}
