// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.VehicleEntity
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Usability tweaks: vehicle card actions, day selector grid, status page button and dismissible messages. */
class UsabilityImprovementsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun activeCarHasNoActivateButtonAndDeleteIconsAlignAtTheEnd() {
        setScreen(width = 360.dp, fontScale = 1f) {
            VehiclesSection(
                vehicles = listOf(
                    VehicleEntity(id = 1, name = "Active car", bluetoothIdHash = "a".repeat(64)),
                    VehicleEntity(id = 2, name = "Other car", bluetoothIdHash = "b".repeat(64)),
                ),
                selectedVehicleId = 1L,
                onSelect = {},
                onRemove = {},
            )
        }
        val activate = composeRule.onAllNodesWithTextCompat(context.getString(R.string.activate))
        assertEquals("only the non-active car offers Activate", 1, activate.size)
        val activeDelete = composeRule
            .onNodeWithContentDescription(context.getString(R.string.delete_car_named, "Active car"))
            .getUnclippedBoundsInRoot()
        val otherDelete = composeRule
            .onNodeWithContentDescription(context.getString(R.string.delete_car_named, "Other car"))
            .getUnclippedBoundsInRoot()
        val otherActivate = activate[0].getUnclippedBoundsInRoot()
        assertClose(otherDelete.right, activeDelete.right)
        assertClose(otherDelete.width, activeDelete.width)
        assertTrue("delete sits in the header, above Activate", otherDelete.bottom <= otherActivate.top)
        assertTrue("Activate spans the card", otherActivate.width > otherDelete.width * 4)
    }

    @Test
    fun dayTogglesShareOneRowWhenTheyFit() {
        setScreen(width = 420.dp, fontScale = 1f) {
            var mask by remember { mutableStateOf(0) }
            ActiveDaysSelector(selectedMask = mask, onSelected = { mask = it })
        }
        val bounds = dayBounds()
        assertEquals(1, bounds.map { it.top.value.toInt() }.distinct().size)
        assertEqualWidths(bounds)
        assertTrue("day toggle widths: ${bounds.map { it.width.value }}", bounds.all { it.width.value >= 48f - 0.001f })
    }

    @Test
    fun dayTogglesSplitFourPlusThreeWhenTheyDoNotFit() {
        setScreen(width = 340.dp, fontScale = 1.5f) {
            var mask by remember { mutableStateOf(0) }
            ActiveDaysSelector(selectedMask = mask, onSelected = { mask = it })
        }
        val bounds = dayBounds()
        assertEquals(listOf(4, 3), bounds.groupBy { it.top.value.toInt() }.toSortedMap().values.map { it.size })
        assertEqualWidths(bounds)
        // Subtracting coordinates converted from pixels can yield 47.999992 dp for a 48 dp target.
        assertTrue("day toggle heights: ${bounds.map { it.height.value }}", bounds.all { it.height.value >= 48f - 0.001f })
        // Second row starts at the same x as the first one (start aligned).
        assertClose(bounds[0].left, bounds[4].left)
    }

    @Test
    fun dayTogglesStillToggle() {
        setScreen(width = 340.dp, fontScale = 1.5f) {
            var mask by remember { mutableStateOf(0) }
            ActiveDaysSelector(selectedMask = mask, onSelected = { mask = it })
        }
        val sunday = composeRule.onNodeWithContentDescription(context.getString(R.string.day_sunday))
        sunday.assertIsOff()
        sunday.performClick()
        sunday.assertIsOn()
        sunday.performClick()
        sunday.assertIsOff()
    }

    @Test
    fun verificationHasNoRefreshButtonAndShowsSetupAction() {
        var action: SystemSetupAction? = null
        setScreen(width = 360.dp, fontScale = 1f) {
            VerificationSection(
                diagnostics = DiagnosticsUiState(
                    title = "Status title",
                    setupAction = SystemSetupAction.OpenBluetoothSettings,
                ),
                onSetupAction = { action = it },
                onTestConnect = {},
                onTestDisconnect = {},
                onOpenBluetoothSettings = {},
                onOpenNotificationSettings = {},
            )
        }
        val label = context.getString(R.string.setup_open_bluetooth_settings_button)
        composeRule.onNodeWithText(label).performClick()
        assertEquals(SystemSetupAction.OpenBluetoothSettings, action)
        assertTrue(composeRule.onAllNodesWithTextCompat("Actualizar").isEmpty())
        assertTrue(composeRule.onAllNodesWithTextCompat("Refresh").isEmpty())
    }

    @Test
    fun verificationShowsNoSetupButtonWhenReady() {
        setScreen(width = 360.dp, fontScale = 1f) {
            VerificationSection(
                diagnostics = DiagnosticsUiState(title = "Status title", setupAction = SystemSetupAction.Ready),
                onSetupAction = {},
                onTestConnect = {},
                onTestDisconnect = {},
                onOpenBluetoothSettings = {},
                onOpenNotificationSettings = {},
            )
        }
        assertTrue(composeRule.onAllNodesWithTextCompat("Actualizar").isEmpty())
    }

    @Test
    fun messageDismissesOnClick() {
        showMessage(MessageKind.Error)
        composeRule.onNodeWithText("Hello message").assertExists()
        composeRule.onNodeWithText("Hello message").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCompat("Hello message").isEmpty() }
    }

    @Test
    fun messageDismissesOnSwipe() {
        showMessage(MessageKind.Warning)
        composeRule.onNodeWithText("Hello message").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCompat("Hello message").isEmpty() }
    }

    @Test
    fun nextMessageStaysVisibleAfterSwipeAndDismissesInTheOtherDirection() {
        showMessage(MessageKind.Error, "Hello message", "Second message")
        composeRule.onNodeWithText("Hello message").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCompat("Second message").isNotEmpty() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second message").assertIsDisplayed()
        composeRule.onNodeWithText("Second message").performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCompat("Second message").isEmpty() }
    }

    private fun showMessage(kind: MessageKind, vararg messages: String = arrayOf("Hello message")) {
        val hostState = SnackbarHostState()
        composeRule.setContent {
            MementoCarTheme {
                val scope = rememberCoroutineScope()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    scope.launch {
                        messages.forEach { hostState.showSnackbar(MessageSnackbarVisuals(UiMessage(it, kind))) }
                    }
                }
                Box(Modifier.padding(16.dp)) {
                    SnackbarHost(hostState) { data ->
                        DismissibleMessageBanner(
                            message = data.visuals.message,
                            kind = kind,
                            onDismiss = data::dismiss,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCompat(messages.first()).isNotEmpty() }
    }

    private fun dayBounds(): List<DpRect> = activeDayOptions.map {
        composeRule.onNodeWithContentDescription(context.getString(it.fullLabelRes)).getUnclippedBoundsInRoot()
    }

    private fun assertEqualWidths(bounds: List<DpRect>) = bounds.forEach {
        assertEquals("widths: ${bounds.map { b -> b.width.value }}", bounds[0].width.value, it.width.value, 1f)
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

    private fun assertClose(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 1f)

    private val DpRect.width: Dp get() = right - left
    private val DpRect.height: Dp get() = bottom - top
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCompat(text: String):
    List<SemanticsNodeInteraction> {
    val nodes = onAllNodesWithText(text)
    return nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).indices.map { nodes[it] }
}
