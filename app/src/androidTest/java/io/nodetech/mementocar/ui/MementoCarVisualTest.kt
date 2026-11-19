// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

private data class VisualSample(val carName: String, val reminderLabel: String)

class MementoCarVisualTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun remindersLightTheme() = renderReminders(dark = false)
    @Test fun remindersDarkTheme() = renderReminders(dark = true)
    @Test fun remindersLargeText() = renderReminders(dark = false, fontScale = 1.6f)

    private fun renderReminders(dark: Boolean, fontScale: Float = 1f) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sample = when (context.resources.configuration.locales[0].language) {
            "es" -> VisualSample("Mi coche", "Recoger la mochila")
            "ca" -> VisualSample("El meu cotxe", "Agafar la motxilla")
            else -> VisualSample("City car", "Pick up my backpack")
        }
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MementoCarTheme(darkTheme = dark) {
                    Scaffold(
                        modifier = Modifier.width(320.dp).fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            Column {
                                ReminderActions(false, {}, {})
                                AppBottomNavigation(AppPage.Reminders, {}, 1, 0)
                            }
                        },
                    ) { padding ->
                        Column(
                            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppHeader(sample.carName, DiagnosticsUiState(level = DiagnosticsLevelUiState.Ready))
                            ChecklistHeader(1, SystemSetupAction.Ready, {})
                            ListCard {
                                ReminderRow(
                                    ChecklistItemEntity(
                                        id = 1, vehicleId = 1, label = sample.reminderLabel, sortOrder = 0,
                                        recurrence = ReminderRecurrence.EveryTrigger, critical = true,
                                    ),
                                    onEdit = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.new_item)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.dictate)).assertIsDisplayed()
        composeRule.waitForIdle()
        val output = File(context.getExternalFilesDir(null), "ui-${if (dark) "dark" else "light"}-$fontScale.png")
        output.outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
