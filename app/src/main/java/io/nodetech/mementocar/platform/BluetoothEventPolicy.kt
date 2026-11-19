// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.bluetooth.BluetoothDevice
import io.nodetech.mementocar.data.ReminderTrigger

/**
 * ACL is the device link, whereas A2DP and Headset are independent audio profiles.
 * A profile can disconnect while the car is still connected. Use only ACL events
 * so partial profile changes cannot trigger exits or cancel delayed entry reminders.
 * This also works after process recreation without an in-memory profile history.
 */
internal fun bluetoothReminderTrigger(action: String?): ReminderTrigger? =
    when (action) {
        BluetoothDevice.ACTION_ACL_CONNECTED -> ReminderTrigger.Connect
        BluetoothDevice.ACTION_ACL_DISCONNECTED -> ReminderTrigger.Disconnect
        else -> null
    }
