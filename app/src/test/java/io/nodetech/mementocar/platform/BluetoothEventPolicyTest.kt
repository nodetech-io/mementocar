// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothEventPolicyTest {
    @Test
    fun aclEventsTriggerDeviceConnectionChanges() {
        assertEquals(ReminderTrigger.Connect, bluetoothReminderTrigger(BluetoothDevice.ACTION_ACL_CONNECTED))
        assertEquals(ReminderTrigger.Disconnect, bluetoothReminderTrigger(BluetoothDevice.ACTION_ACL_DISCONNECTED))
    }

    @Test
    fun profileChangesNeverTriggerReminders() {
        for (action in profileActions) {
            // Applies to connected, disconnected, transitional and missing profile states.
            assertNull(bluetoothReminderTrigger(action))
        }
    }

    @Test
    fun partialAudioDisconnectDoesNotCreateAnExit() {
        val actions = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
        )

        assertEquals(listOf(ReminderTrigger.Connect), actions.mapNotNull(::bluetoothReminderTrigger))
    }

    @Test
    fun fullDisconnectCreatesOnlyOneExitDespiteProfileBroadcasts() {
        val actions = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
        )

        assertEquals(
            listOf(ReminderTrigger.Connect, ReminderTrigger.Disconnect),
            actions.mapNotNull(::bluetoothReminderTrigger),
        )
    }

    @Test
    fun profileBroadcastAfterProcessRecreationIsNotMistakenForDeviceDisconnect() {
        // No earlier ACL/profile history is required to reject partial disconnections.
        assertNull(bluetoothReminderTrigger(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
    }

    @Test
    fun ignoresUnrelatedActions() {
        for (action in listOf(null, "unknown", BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)) {
            assertNull(bluetoothReminderTrigger(action))
        }
    }

    private val profileActions = listOf(
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
    )
}
