// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PairedBluetoothDevice(
    val name: String,
    val address: String,
)

@Singleton
class BluetoothDeviceProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val bluetoothAdapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean {
        if (!hasConnectPermission()) return false
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedBluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return try {
            bluetoothAdapter
                ?.bondedDevices
                ?.mapNotNull { device ->
                    val address = device.address ?: return@mapNotNull null
                    PairedBluetoothDevice(
                        name = device.name ?: "Bluetooth ${address.takeLast(5)}",
                        address = address,
                    )
                }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
