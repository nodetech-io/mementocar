// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.nodetech.mementocar.domain.BluetoothDisconnectHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothDisconnectReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: BluetoothDisconnectHandler

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val trigger = bluetoothReminderTrigger(action) ?: return
        if (!hasConnectPermission(context)) return
        val address = try {
            // Permission can be revoked between the check and reading the device address.
            bluetoothDeviceFrom(intent)?.address
        } catch (_: SecurityException) {
            null
        } ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handler.handleEvent(address, trigger)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun hasConnectPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun bluetoothDeviceFrom(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        }
    }
}
