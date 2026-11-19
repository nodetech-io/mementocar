// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationReadiness(
    val canPost: Boolean,
    val reason: NotificationBlockReason? = null,
)

enum class NotificationBlockReason {
    PermissionDenied,
    AppNotificationsDisabled,
    ChannelDisabled,
}

@Singleton
class SystemDiagnosticsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bluetoothDeviceProvider: BluetoothDeviceProvider,
    private val reminderNotifier: ReminderNotifier,
) {
    fun snapshot(): SystemDiagnosticsSnapshot {
        val notificationReadiness = reminderNotifier.notificationReadiness()
        return SystemDiagnosticsSnapshot(
            hasBluetoothPermission = bluetoothDeviceProvider.hasConnectPermission(),
            isBluetoothAvailable = bluetoothDeviceProvider.isBluetoothAvailable(),
            isBluetoothEnabled = bluetoothDeviceProvider.isBluetoothEnabled(),
            notificationReadiness = notificationReadiness,
            hasMicrophonePermission = hasPermission(Manifest.permission.RECORD_AUDIO),
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

data class SystemDiagnosticsSnapshot(
    val hasBluetoothPermission: Boolean,
    val isBluetoothAvailable: Boolean,
    val isBluetoothEnabled: Boolean,
    val notificationReadiness: NotificationReadiness,
    val hasMicrophonePermission: Boolean,
)
