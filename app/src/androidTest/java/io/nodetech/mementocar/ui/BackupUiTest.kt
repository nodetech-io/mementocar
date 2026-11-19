// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.BackupSummary
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class BackupUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importDialogShowsCountsAndReplacementWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var dismissed = false
        composeRule.setContent {
            MementoCarTheme {
                BackupImportDialog(
                    summary = BackupSummary(
                        exportedAtEpochMillis = 1_700_000_000_000L,
                        vehicleCount = 2,
                        reminderCount = 7,
                    ),
                    inProgress = false,
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        val vehicles = context.resources.getQuantityString(R.plurals.backup_vehicle_count, 2, 2)
        val reminders = context.resources.getQuantityString(R.plurals.backup_reminder_count, 7, 7)
        composeRule.onNodeWithText(
            context.getString(R.string.backup_contents_summary, vehicles, reminders),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.import_and_replace)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun dataActionsRemainIndependentAndClickable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var exported = false
        var imported = false
        composeRule.setContent {
            MementoCarTheme {
                DataManagementSection(
                    inProgress = false,
                    onExport = { exported = true },
                    onImport = { imported = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.export_backup)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.import_backup)).performClick()
        composeRule.runOnIdle {
            assertTrue(exported)
            assertTrue(imported)
        }
    }
}
