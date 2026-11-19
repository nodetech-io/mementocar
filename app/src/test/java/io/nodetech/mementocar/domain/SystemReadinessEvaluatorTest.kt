// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemReadinessEvaluatorTest {
    @Test
    fun blocksWhenThereIsNoVehicle() {
        val readiness = evaluateSystemReadiness(readyInput().copy(hasVehicle = false))

        assertEquals(SystemReadinessLevel.Blocked, readiness.level)
    }

    @Test
    fun blocksWhenVehicleHasNoBluetooth() {
        val readiness = evaluateSystemReadiness(readyInput().copy(vehicleHasBluetooth = false))

        assertEquals(SystemReadinessLevel.Blocked, readiness.level)
        assertEquals(SystemReadinessReason.VehicleMissingBluetooth, readiness.reason)
    }

    @Test
    fun blocksWhenDeviceHasNoBluetoothAdapter() {
        val readiness = evaluateSystemReadiness(
            readyInput().copy(
                hasVehicle = false,
                vehicleHasBluetooth = false,
                isBluetoothAvailable = false,
                isBluetoothEnabled = false,
            ),
        )

        assertEquals(SystemReadinessLevel.Blocked, readiness.level)
        assertEquals(SystemReadinessReason.BluetoothUnavailable, readiness.reason)
    }

    @Test
    fun blocksWhenBluetoothPermissionIsMissing() {
        val readiness = evaluateSystemReadiness(readyInput().copy(hasBluetoothPermission = false))

        assertEquals(SystemReadinessLevel.Blocked, readiness.level)
    }

    @Test
    fun blocksWhenNotificationsCannotBePosted() {
        val readiness = evaluateSystemReadiness(readyInput().copy(canPostNotifications = false))

        assertEquals(SystemReadinessLevel.Blocked, readiness.level)
    }

    @Test
    fun attentionWhenThereAreNoReminders() {
        val readiness = evaluateSystemReadiness(readyInput().copy(hasReminders = false))

        assertEquals(SystemReadinessLevel.Attention, readiness.level)
    }

    @Test
    fun attentionWhenNoSuccessfulDiagnosticEventHasBeenSeen() {
        val readiness = evaluateSystemReadiness(readyInput().copy(hasSuccessfulDiagnosticEvent = false))

        assertEquals(SystemReadinessLevel.Attention, readiness.level)
    }

    @Test
    fun readyWhenCriticalChecksPassAndThereIsAnEvent() {
        val readiness = evaluateSystemReadiness(readyInput())

        assertEquals(SystemReadinessLevel.Ready, readiness.level)
    }

    private fun readyInput(): SystemReadinessInput {
        return SystemReadinessInput(
            hasVehicle = true,
            vehicleHasBluetooth = true,
            hasBluetoothPermission = true,
            isBluetoothAvailable = true,
            isBluetoothEnabled = true,
            canPostNotifications = true,
            hasReminders = true,
            hasSuccessfulDiagnosticEvent = true,
        )
    }
}
