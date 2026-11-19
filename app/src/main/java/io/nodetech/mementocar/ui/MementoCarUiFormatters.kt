// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.ui.theme.MementoDanger
import io.nodetech.mementocar.ui.theme.MementoDangerContainer
import io.nodetech.mementocar.ui.theme.MementoSuccess
import io.nodetech.mementocar.ui.theme.MementoSuccessContainer
import io.nodetech.mementocar.ui.theme.MementoWarning
import io.nodetech.mementocar.ui.theme.MementoWarningContainer
import java.text.DateFormat
import java.util.Date

internal fun Long.uiShortDateTime(): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(this))
}

@Composable
internal fun ReminderStatus.label(): String {
    return when (this) {
        ReminderStatus.Pending -> stringResource(R.string.status_pending)
        ReminderStatus.Snoozed -> stringResource(R.string.status_snoozed)
        ReminderStatus.Completed -> stringResource(R.string.status_completed)
        ReminderStatus.Dismissed -> stringResource(R.string.status_dismissed)
    }
}

@Composable
internal fun ReminderTrigger.label(): String {
    return when (this) {
        ReminderTrigger.Connect -> stringResource(R.string.trigger_connect)
        ReminderTrigger.Disconnect -> stringResource(R.string.trigger_disconnect)
    }
}

internal fun ReminderTrigger.icon(): ImageVector {
    return when (this) {
        ReminderTrigger.Connect -> Icons.AutoMirrored.Outlined.Login
        ReminderTrigger.Disconnect -> Icons.AutoMirrored.Outlined.Logout
    }
}

internal fun AppPage.icon(): ImageVector {
    return when (this) {
        AppPage.Reminders,
        AppPage.NewReminder -> Icons.AutoMirrored.Outlined.FormatListBulleted
        AppPage.Alerts -> Icons.Outlined.ErrorOutline
        AppPage.Verification -> Icons.Outlined.Check
        AppPage.Settings -> Icons.Outlined.Settings
    }
}

@Composable
internal fun SystemSetupAction.icon(): ImageVector {
    return when (this) {
        SystemSetupAction.ConfigureBluetoothVehicle -> ImageVector.vectorResource(R.drawable.ic_notification_car)
        SystemSetupAction.RequestPermissions -> Icons.Outlined.Settings
        SystemSetupAction.BluetoothUnavailable -> Icons.Outlined.ErrorOutline
        SystemSetupAction.OpenBluetoothSettings -> Icons.Outlined.Bluetooth
        SystemSetupAction.OpenNotificationSettings -> Icons.Outlined.Settings
        SystemSetupAction.CreateFirstReminder -> Icons.Outlined.Add
        SystemSetupAction.RunFirstTest -> Icons.Outlined.Check
        SystemSetupAction.Ready -> Icons.Outlined.Check
    }
}

@Composable
internal fun SystemSetupAction.title(): String {
    return when (this) {
        SystemSetupAction.ConfigureBluetoothVehicle -> stringResource(R.string.setup_configure_bluetooth_vehicle_title)
        SystemSetupAction.RequestPermissions -> stringResource(R.string.setup_request_permissions_title)
        SystemSetupAction.BluetoothUnavailable -> stringResource(R.string.setup_bluetooth_unavailable_title)
        SystemSetupAction.OpenBluetoothSettings -> stringResource(R.string.setup_open_bluetooth_settings_title)
        SystemSetupAction.OpenNotificationSettings -> stringResource(R.string.setup_open_notification_settings_title)
        SystemSetupAction.CreateFirstReminder -> stringResource(R.string.setup_create_first_reminder_title)
        SystemSetupAction.RunFirstTest -> stringResource(R.string.setup_run_first_test_title)
        SystemSetupAction.Ready -> stringResource(R.string.setup_ready_title)
    }
}

@Composable
internal fun SystemSetupAction.detail(): String {
    return when (this) {
        SystemSetupAction.ConfigureBluetoothVehicle -> stringResource(R.string.setup_configure_bluetooth_vehicle_detail)
        SystemSetupAction.RequestPermissions -> stringResource(R.string.setup_request_permissions_detail)
        SystemSetupAction.BluetoothUnavailable -> stringResource(R.string.setup_bluetooth_unavailable_detail)
        SystemSetupAction.OpenBluetoothSettings -> stringResource(R.string.setup_open_bluetooth_settings_detail)
        SystemSetupAction.OpenNotificationSettings -> stringResource(R.string.setup_open_notification_settings_detail)
        SystemSetupAction.CreateFirstReminder -> stringResource(R.string.setup_create_first_reminder_detail)
        SystemSetupAction.RunFirstTest -> stringResource(R.string.setup_run_first_test_detail)
        SystemSetupAction.Ready -> stringResource(R.string.setup_ready_detail)
    }
}

@Composable
internal fun SystemSetupAction.buttonLabel(): String {
    return when (this) {
        SystemSetupAction.ConfigureBluetoothVehicle -> stringResource(R.string.setup_configure_bluetooth_vehicle_button)
        SystemSetupAction.RequestPermissions -> stringResource(R.string.setup_request_permissions_button)
        SystemSetupAction.BluetoothUnavailable -> stringResource(R.string.setup_bluetooth_unavailable_button)
        SystemSetupAction.OpenBluetoothSettings -> stringResource(R.string.setup_open_bluetooth_settings_button)
        SystemSetupAction.OpenNotificationSettings -> stringResource(R.string.setup_open_notification_settings_button)
        SystemSetupAction.CreateFirstReminder -> stringResource(R.string.setup_create_first_reminder_button)
        SystemSetupAction.RunFirstTest -> stringResource(R.string.setup_run_first_test_button)
        SystemSetupAction.Ready -> stringResource(R.string.setup_ready_button)
    }
}

@Composable
internal fun ReminderRecurrence.label(): String {
    return when (this) {
        ReminderRecurrence.Once -> stringResource(R.string.recurrence_once)
        ReminderRecurrence.EveryTrigger -> stringResource(R.string.recurrence_every_trigger)
        ReminderRecurrence.Daily -> stringResource(R.string.recurrence_daily)
        ReminderRecurrence.Weekly -> stringResource(R.string.recurrence_weekly)
        ReminderRecurrence.Monthly -> stringResource(R.string.recurrence_monthly)
    }
}

internal fun Int.notificationDelayShortLabel(): String {
    return if (this == 0) "0 min" else "$this min"
}

@Composable
internal fun Int.notificationDelayDetail(): String {
    return if (this == 0) {
        stringResource(R.string.notification_delay_immediate_detail)
    } else {
        stringResource(R.string.notification_delay_minutes_detail, this)
    }
}

internal val notificationDelayOptions = listOf(0, 1, 5, 10, 30)

internal data class ActiveDayOption(
    @param:StringRes val labelRes: Int,
    @param:StringRes val fullLabelRes: Int,
    val mask: Int,
)

internal val activeDayOptions = listOf(
    ActiveDayOption(R.string.day_monday_short, R.string.day_monday, ActiveDays.Monday),
    ActiveDayOption(R.string.day_tuesday_short, R.string.day_tuesday, ActiveDays.Tuesday),
    ActiveDayOption(R.string.day_wednesday_short, R.string.day_wednesday, ActiveDays.Wednesday),
    ActiveDayOption(R.string.day_thursday_short, R.string.day_thursday, ActiveDays.Thursday),
    ActiveDayOption(R.string.day_friday_short, R.string.day_friday, ActiveDays.Friday),
    ActiveDayOption(R.string.day_saturday_short, R.string.day_saturday, ActiveDays.Saturday),
    ActiveDayOption(R.string.day_sunday_short, R.string.day_sunday, ActiveDays.Sunday),
)

@Composable
internal fun Int.label(): String {
    return when (this) {
        ActiveDays.All -> stringResource(R.string.all_days)
        ActiveDays.Weekdays -> stringResource(R.string.weekdays)
        ActiveDays.Weekend -> stringResource(R.string.weekend)
        else -> customDaysLabel()
    }
}

@Composable
internal fun DiagnosticsLevelUiState.statusColor(): Color {
    return when (this) {
        DiagnosticsLevelUiState.Ready -> MementoSuccess
        DiagnosticsLevelUiState.Attention -> MementoWarning
        DiagnosticsLevelUiState.Blocked -> MementoDanger
    }
}

@Composable
internal fun DiagnosticsLevelUiState.statusBackground(): Color {
    return when (this) {
        DiagnosticsLevelUiState.Ready -> MementoSuccessContainer
        DiagnosticsLevelUiState.Attention -> MementoWarningContainer
        DiagnosticsLevelUiState.Blocked -> MementoDangerContainer
    }
}

@Composable
internal fun DiagnosticsLevelUiState.shortLabel(): String {
    return when (this) {
        DiagnosticsLevelUiState.Ready -> stringResource(R.string.diagnostics_ready_short)
        DiagnosticsLevelUiState.Attention -> stringResource(R.string.diagnostics_attention_short)
        DiagnosticsLevelUiState.Blocked -> stringResource(R.string.diagnostics_blocked_short)
    }
}

internal fun DiagnosticsLevelUiState.icon(): ImageVector {
    return when (this) {
        DiagnosticsLevelUiState.Ready -> Icons.Outlined.Check
        DiagnosticsLevelUiState.Attention -> Icons.Outlined.ErrorOutline
        DiagnosticsLevelUiState.Blocked -> Icons.Outlined.ErrorOutline
    }
}

@Composable
internal fun Int.customDaysLabel(): String {
    val selected = activeDayOptions
        .filter { this and it.mask != 0 }
        .map { stringResource(it.labelRes) }
        .joinToString(", ")
    return selected.ifBlank { stringResource(R.string.no_active_days) }
}
