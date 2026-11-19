// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Rule
import org.junit.Test

class FutureTripStatusInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reminderRowBecomesReadyWhenEligibilityTimeIsReached() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val readyText = context.getString(R.string.future_trip_ready)
        val eligibleFromMillis = System.currentTimeMillis() + 2_000L

        composeRule.setContent {
            MementoCarTheme {
                ReminderRow(
                    item = ChecklistItemEntity(
                        vehicleId = 1L,
                        label = "Maleta",
                        sortOrder = 0,
                        recurrence = ReminderRecurrence.Once,
                        eligibleFromMillis = eligibleFromMillis,
                    ),
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText(readyText).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText(readyText).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(readyText).assertExists()
    }
}
