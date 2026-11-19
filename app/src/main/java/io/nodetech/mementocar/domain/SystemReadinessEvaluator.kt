// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

data class SystemReadinessInput(
    val hasVehicle: Boolean,
    val vehicleHasBluetooth: Boolean,
    val hasBluetoothPermission: Boolean,
    val isBluetoothAvailable: Boolean,
    val isBluetoothEnabled: Boolean,
    val canPostNotifications: Boolean,
    val hasReminders: Boolean,
    val hasSuccessfulDiagnosticEvent: Boolean,
)

enum class SystemReadinessLevel {
    Ready,
    Attention,
    Blocked,
}

data class SystemReadiness(
    val level: SystemReadinessLevel,
    val reason: SystemReadinessReason,
)

enum class SystemReadinessReason {
    MissingVehicle,
    VehicleMissingBluetooth,
    MissingBluetoothPermission,
    BluetoothUnavailable,
    BluetoothDisabled,
    NotificationsBlocked,
    MissingReminders,
    MissingSuccessfulDiagnosticEvent,
    Ready,
}

fun evaluateSystemReadiness(input: SystemReadinessInput): SystemReadiness {
    return when {
        !input.isBluetoothAvailable -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.BluetoothUnavailable)
        !input.hasVehicle -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.MissingVehicle)
        !input.vehicleHasBluetooth -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.VehicleMissingBluetooth)
        !input.hasBluetoothPermission -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.MissingBluetoothPermission)
        !input.isBluetoothEnabled -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.BluetoothDisabled)
        !input.canPostNotifications -> SystemReadiness(SystemReadinessLevel.Blocked, SystemReadinessReason.NotificationsBlocked)
        !input.hasReminders -> SystemReadiness(SystemReadinessLevel.Attention, SystemReadinessReason.MissingReminders)
        !input.hasSuccessfulDiagnosticEvent -> SystemReadiness(
            SystemReadinessLevel.Attention,
            SystemReadinessReason.MissingSuccessfulDiagnosticEvent,
        )
        else -> SystemReadiness(SystemReadinessLevel.Ready, SystemReadinessReason.Ready)
    }
}
