// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.VehicleEntity
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Buttons placed side by side must share one height, and never break a word mid-way. */
class ButtonRowLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sideBySideButtonsShareHeightWhenOneLabelWraps() {
        var refreshClicks by mutableIntStateOf(0)
        var setupClicks by mutableIntStateOf(0)
        setScreen(width = 300.dp, fontScale = 1.3f) {
            ButtonRow {
                SecondaryButton(modifier = Modifier.weight(1f), text = "Refresh", onClick = { refreshClicks++ })
                PrimaryButton(modifier = Modifier.weight(1f), text = "Set up your car now", onClick = { setupClicks++ })
            }
        }
        val refresh = bounds(composeRule.onNodeWithText("Refresh"))
        val setup = bounds(composeRule.onNodeWithText("Set up your car now"))

        assertSameRow(refresh, setup)
        assertSameHeight(refresh, setup)
        assertTrue("label should wrap to two lines", setup.height > 60.dp)

        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.onNodeWithText("Set up your car now").performClick()
        assertEquals(1, refreshClicks)
        assertEquals(1, setupClicks)
        saveScreenshot("button-row-wrapped")
    }

    @Test
    fun buttonsStackFullWidthInsteadOfBreakingAWord() {
        var bluetoothClicks by mutableIntStateOf(0)
        var notificationClicks by mutableIntStateOf(0)
        setScreen(width = 300.dp, fontScale = 1.3f) {
            ButtonRow(modifier = Modifier.testTag("row")) {
                SecondaryButton(modifier = Modifier.weight(1f), text = "Bluetooth", onClick = { bluetoothClicks++ })
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = "Notificaciones",
                    onClick = { notificationClicks++ },
                )
            }
        }
        val row = bounds(composeRule.onNodeWithTag("row"))
        val bluetooth = bounds(composeRule.onNodeWithText("Bluetooth"))
        val notifications = bounds(composeRule.onNodeWithText("Notificaciones"))

        assertTrue("second button must be below the first", notifications.top >= bluetooth.bottom)
        assertClose(row.width, bluetooth.width)
        assertClose(row.width, notifications.width)

        composeRule.onNodeWithText("Bluetooth").performClick()
        composeRule.onNodeWithText("Notificaciones").performClick()
        assertEquals(1, bluetoothClicks)
        assertEquals(1, notificationClicks)
        saveScreenshot("button-row-stacked")
    }

    @Test
    fun segmentedOptionsShareHeightAndStaySelectable() {
        setScreen(width = 300.dp, fontScale = 2f) {
            var target by remember { mutableStateOf(ReminderTarget.SelectedVehicle) }
            ReminderTargetSelector(selected = target, onSelected = { target = it })
        }
        val thisCar = composeRule.onNodeWithText(context.getString(R.string.this_car))
        val all = composeRule.onNodeWithText(context.getString(R.string.all))

        assertSameRow(bounds(thisCar), bounds(all))
        assertSameHeight(bounds(thisCar), bounds(all))

        all.performClick()
        all.assertIsSelected()
        saveScreenshot("segmented-large-text")
    }

    @Test
    fun vehicleCardDeleteIsAnIconInTheHeaderAndAsksForConfirmation() {
        var selectedId by mutableStateOf<Long?>(2L)
        setScreen(width = 300.dp, fontScale = 1.3f) {
            VehiclesSection(
                vehicles = listOf(VehicleEntity(id = 1, name = "Car", bluetoothIdHash = "a".repeat(64))),
                selectedVehicleId = selectedId,
                onSelect = { selectedId = it },
                onRemove = {},
            )
        }
        val activate = composeRule.onNodeWithText(context.getString(R.string.activate))
        val delete = composeRule.onNodeWithContentDescription(context.getString(R.string.delete_car_named, "Car"))
        composeRule.onNodeWithText(context.getString(R.string.delete)).assertDoesNotExist()

        // The delete icon sits in the car header, above the full-width Activate button.
        assertTrue(bounds(delete).bottom <= bounds(activate).top)
        saveScreenshot("vehicle-card")

        activate.performClick()
        assertEquals(1L, selectedId)
        delete.performClick()
        composeRule.onNodeWithText(context.getString(R.string.delete_car_title)).assertExists()
    }

    private fun setScreen(width: Dp, fontScale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MementoCarTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(width).padding(16.dp)) { content() }
                    }
                }
            }
        }
    }

    private fun bounds(node: SemanticsNodeInteraction): DpRect = node.getUnclippedBoundsInRoot()

    private fun assertSameRow(a: DpRect, b: DpRect) {
        assertClose(a.top, b.top)
        assertTrue("buttons must not overlap horizontally", b.left >= a.right || a.left >= b.right)
    }

    private fun assertSameHeight(a: DpRect, b: DpRect) = assertClose(a.height, b.height)

    private fun assertClose(expected: Dp, actual: Dp) {
        assertEquals(expected.value, actual.value, 1f)
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val output = File(context.getExternalFilesDir(null), "$name.png")
        output.outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val DpRect.width: Dp get() = right - left
    private val DpRect.height: Dp get() = bottom - top
}
