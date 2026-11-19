// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import io.nodetech.mementocar.data.ChecklistItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MementoCarViewModelFlowTest {
    @Test
    fun emitsEmptyChecklistWithoutSelectedVehicle() = runTest {
        val selectedVehicleId = MutableStateFlow<Long?>(null)

        val checklist = observeSelectedChecklist(selectedVehicleId) {
            error("Checklist must not be observed without a selected vehicle")
        }.first()

        assertEquals(emptyList<ChecklistItemEntity>(), checklist)
    }

    @Test
    fun clearsChecklistWhenVehicleSelectionIsRemoved() = runTest {
        val selectedVehicleId = MutableStateFlow<Long?>(1L)
        val item = ChecklistItemEntity(
            id = 10L,
            vehicleId = 1L,
            label = "Wallet",
            sortOrder = 0,
        )
        val checklistFlow = observeSelectedChecklist(selectedVehicleId) { flowOf(listOf(item)) }

        assertEquals(listOf(item), checklistFlow.first())

        selectedVehicleId.value = null

        assertEquals(emptyList<ChecklistItemEntity>(), checklistFlow.first())
    }
}
