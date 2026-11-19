// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

data class SystemSetupInput(
    val hasVehicle: Boolean,
    val vehicleHasBluetooth: Boolean,
    val hasBluetoothPermission: Boolean,
    val isBluetoothAvailable: Boolean,
    val isBluetoothEnabled: Boolean,
    val canPostNotifications: Boolean,
    val notificationPermissionDenied: Boolean,
    val hasReminders: Boolean,
    val hasSuccessfulDiagnosticEvent: Boolean,
)

enum class SystemSetupAction {
    ConfigureBluetoothVehicle,
    RequestPermissions,
    BluetoothUnavailable,
    OpenBluetoothSettings,
    OpenNotificationSettings,
    CreateFirstReminder,
    RunFirstTest,
    Ready,
}

fun nextSystemSetupAction(input: SystemSetupInput): SystemSetupAction {
    return when {
        !input.isBluetoothAvailable -> SystemSetupAction.BluetoothUnavailable
        !input.hasVehicle -> SystemSetupAction.ConfigureBluetoothVehicle
        !input.vehicleHasBluetooth -> SystemSetupAction.ConfigureBluetoothVehicle
        !input.hasBluetoothPermission -> SystemSetupAction.RequestPermissions
        !input.isBluetoothEnabled -> SystemSetupAction.OpenBluetoothSettings
        // The first reminder comes before the notification permission so the
        // permission is requested in context, right after the user saves it.
        !input.hasReminders -> SystemSetupAction.CreateFirstReminder
        input.notificationPermissionDenied -> SystemSetupAction.RequestPermissions
        !input.canPostNotifications -> SystemSetupAction.OpenNotificationSettings
        !input.hasSuccessfulDiagnosticEvent -> SystemSetupAction.RunFirstTest
        else -> SystemSetupAction.Ready
    }
}
