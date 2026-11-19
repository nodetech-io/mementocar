// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemSetupActionTest {
    @Test
    fun configuresBluetoothVehicleWhenThereIsNoVehicle() {
        val action = nextSystemSetupAction(readyInput().copy(hasVehicle = false))

        assertEquals(SystemSetupAction.ConfigureBluetoothVehicle, action)
    }

    @Test
    fun configuresBluetoothVehicleWhenVehicleHasNoBluetooth() {
        val action = nextSystemSetupAction(readyInput().copy(vehicleHasBluetooth = false))

        assertEquals(SystemSetupAction.ConfigureBluetoothVehicle, action)
    }

    @Test
    fun requestsPermissionsWhenBluetoothPermissionIsMissing() {
        val action = nextSystemSetupAction(readyInput().copy(hasBluetoothPermission = false))

        assertEquals(SystemSetupAction.RequestPermissions, action)
    }

    @Test
    fun opensBluetoothSettingsWhenBluetoothIsOff() {
        val action = nextSystemSetupAction(readyInput().copy(isBluetoothEnabled = false))

        assertEquals(SystemSetupAction.OpenBluetoothSettings, action)
    }

    @Test
    fun showsUnavailableStateWhenDeviceHasNoBluetooth() {
        val action = nextSystemSetupAction(
            readyInput().copy(
                hasVehicle = false,
                vehicleHasBluetooth = false,
                isBluetoothAvailable = false,
                isBluetoothEnabled = false,
            ),
        )

        assertEquals(SystemSetupAction.BluetoothUnavailable, action)
    }

    @Test
    fun requestsPermissionsWhenNotificationRuntimePermissionIsDenied() {
        val action = nextSystemSetupAction(
            readyInput().copy(
                canPostNotifications = false,
                notificationPermissionDenied = true,
            ),
        )

        assertEquals(SystemSetupAction.RequestPermissions, action)
    }

    @Test
    fun opensNotificationSettingsWhenNotificationsAreBlockedBySettings() {
        val action = nextSystemSetupAction(readyInput().copy(canPostNotifications = false))

        assertEquals(SystemSetupAction.OpenNotificationSettings, action)
    }

    @Test
    fun createsFirstReminderWhenThereAreNoReminders() {
        val action = nextSystemSetupAction(readyInput().copy(hasReminders = false))

        assertEquals(SystemSetupAction.CreateFirstReminder, action)
    }

    @Test
    fun runsFirstTestWhenNoSuccessfulDiagnosticEventHasBeenSeen() {
        val action = nextSystemSetupAction(readyInput().copy(hasSuccessfulDiagnosticEvent = false))

        assertEquals(SystemSetupAction.RunFirstTest, action)
    }

    @Test
    fun readyWhenAllSetupChecksPass() {
        val action = nextSystemSetupAction(readyInput())

        assertEquals(SystemSetupAction.Ready, action)
    }

    @Test
    fun asksForFirstReminderBeforeNotificationPermission() {
        val action = nextSystemSetupAction(
            readyInput().copy(
                hasReminders = false,
                canPostNotifications = false,
                notificationPermissionDenied = true,
            ),
        )

        assertEquals(SystemSetupAction.CreateFirstReminder, action)
    }

    private fun readyInput(): SystemSetupInput {
        return SystemSetupInput(
            hasVehicle = true,
            vehicleHasBluetooth = true,
            hasBluetoothPermission = true,
            isBluetoothAvailable = true,
            isBluetoothEnabled = true,
            canPostNotifications = true,
            notificationPermissionDenied = false,
            hasReminders = true,
            hasSuccessfulDiagnosticEvent = true,
        )
    }
}
