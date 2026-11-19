// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.nodetech.mementocar.R
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MementoCarUiRobustnessTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun externalNavigationRequiresDiscardAndCanBeCancelled() {
        var requested by mutableStateOf(false)
        var exited = false
        compose.setContent {
            MementoCarTheme {
                Editor(
                    requested = requested,
                    onRequestCancelled = { requested = false },
                    onBack = { exited = true },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Pending draft")
        compose.runOnIdle { requested = true }
        compose.onNodeWithText(context.getString(R.string.discard_changes_title)).assertIsDisplayed()
        compose.runOnIdle { assertFalse(exited) }
        compose.onNodeWithText(context.getString(R.string.keep_editing)).performClick()
        compose.runOnIdle {
            assertFalse(requested)
            assertFalse(exited)
            requested = true
        }
        compose.onNodeWithText(context.getString(R.string.discard_changes)).performClick()
        compose.runOnIdle { assertTrue(exited) }
    }

    @Test
    fun externalNavigationLeavesUnchangedEditorWithoutPrompt() {
        var requested by mutableStateOf(false)
        var exited = false
        compose.setContent {
            MementoCarTheme { Editor(requested = requested, onBack = { exited = true }) }
        }
        compose.runOnIdle { requested = true }
        compose.waitUntil { exited }
        compose.onNodeWithText(context.getString(R.string.discard_changes_title)).assertDoesNotExist()
    }

    @Test
    fun closedEditorDoesNotReuseDraftOrScrollAndListKeepsItsPosition() {
        var page by mutableStateOf(AppPage.Reminders)
        compose.setContent {
            MementoCarTheme {
                MementoPageContent(page, null, Modifier.fillMaxSize()) { displayedPage ->
                    if (displayedPage == AppPage.Reminders) {
                        items(50) { Text("Reminder $it", modifier = Modifier.height(80.dp)) }
                    } else {
                        item { Editor(onBack = { page = AppPage.Reminders }) }
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        compose.onNodeWithText("Reminder 30").assertIsDisplayed()
        compose.runOnIdle { page = AppPage.NewReminder }
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Discard me")
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.discard_changes)).performClick()
        compose.onNodeWithText("Reminder 30").assertIsDisplayed()
        compose.runOnIdle { page = AppPage.NewReminder }
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("")),
        )
    }

    @Test
    fun expiredScheduleReopensAdvancedOptionsAndShowsError() {
        var now = System.currentTimeMillis()
        var saved = false
        compose.setContent {
            MementoCarTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Editor(now = { now }, onSaved = { saved = true })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Scheduled reminder")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText(context.getString(R.string.show_advanced_options)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.future_trip_next_trip)).performScrollTo().performClick()
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(android.R.string.ok)).perform(click())
        compose.onNodeWithText(context.getString(R.string.hide_advanced_options)).performScrollTo().performClick()
        compose.runOnIdle { now = Long.MAX_VALUE }
        compose.onNodeWithText(context.getString(R.string.save)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.future_trip_future_date_error)).assertIsDisplayed()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test
    fun nativeThemeAndSystemBarIconsFollowNightMode() {
        for (night in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_MementoCar)
            val attributes = themed.obtainStyledAttributes(intArrayOf(
                android.R.attr.isLightTheme,
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar,
            ))
            try {
                repeat(3) { assertEquals("attribute $it, night=$night", !night, attributes.getBoolean(it, night)) }
            } finally {
                attributes.recycle()
            }
        }
    }

    @Test
    fun editingBaselineAndChangedTextSurviveRestorationWithoutDatabase() {
        var databaseItem: io.nodetech.mementocar.data.ChecklistItemEntity? = io.nodetech.mementocar.data.ChecklistItemEntity(
            id = 42, vehicleId = 7, label = "Original", sortOrder = 3,
            critical = true, eligibleFromMillis = System.currentTimeMillis() + 86400000L,
        )
        val expected = databaseItem
        var restoredItem: io.nodetech.mementocar.data.ChecklistItemEntity? = null
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            val item by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = ReminderEditorSaver) {
                mutableStateOf(databaseItem)
            }
            restoredItem = item
            MementoCarTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NewReminderSection(
                        editingItem = item,
                        onVoiceDraftConsumed = {}, onVoiceInput = {}, isListeningForVoice = false,
                        onBack = {},
                        onAdd = { _, _, _, _, _, _, _ -> },
                        onUpdate = { _, _, _, _, _, _, _, _ -> }, onDelete = {},
                    )
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Unsaved edit")
        databaseItem = null
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(expected, restoredItem) }
        compose.onNode(hasSetTextAction()).assertTextContains("Unsaved edit")
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.discard_changes_title)).assertIsDisplayed()
    }

    @Test
    fun navigationCountsRemainAccessibleWithCompactLabels() {
        compose.setContent {
            MementoCarTheme { AppBottomNavigation(AppPage.Reminders, {}, 12, 3) }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.reminders_title))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                context.resources.getQuantityString(R.plurals.reminders_count, 12, 12),
            ))
        compose.onNodeWithContentDescription(context.getString(R.string.alerts_title))
            .assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                context.resources.getQuantityString(R.plurals.open_alerts_count, 3, 3),
            ))
    }

    @Test
    fun switchingThemeUpdatesSemanticColorsAndPreservesTextContrast() {
        var dark by mutableStateOf(false)
        var pairs = emptyList<Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>()
        compose.setContent {
            MementoCarTheme(darkTheme = dark) {
                val colors = androidx.compose.material3.MaterialTheme.colorScheme
                pairs = listOf(
                    colors.onSurface to colors.surface,
                    colors.onSurfaceVariant to colors.surfaceVariant,
                    colors.onBackground to colors.background,
                    colors.onPrimary to colors.primary,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    io.nodetech.mementocar.ui.theme.MementoSuccess to io.nodetech.mementocar.ui.theme.MementoSuccessContainer,
                    io.nodetech.mementocar.ui.theme.MementoWarning to io.nodetech.mementocar.ui.theme.MementoWarningContainer,
                    io.nodetech.mementocar.ui.theme.MementoDanger to io.nodetech.mementocar.ui.theme.MementoDangerContainer,
                )
                Text("Theme")
            }
        }
        var lightPairs = pairs
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            compose.runOnIdle {
                for ((foreground, background) in pairs) {
                    val low = minOf(foreground.luminance(), background.luminance())
                    val high = maxOf(foreground.luminance(), background.luminance())
                    assertTrue("Insufficient contrast in night=$night", (high + 0.05f) / (low + 0.05f) >= 4.5f)
                }
                if (!night) lightPairs = pairs else {
                    assertTrue(pairs.zip(lightPairs).all { (darkPair, lightPair) -> darkPair != lightPair })
                }
            }
        }
    }

    @Composable
    private fun Editor(
        requested: Boolean = false,
        onRequestCancelled: () -> Unit = {},
        onBack: () -> Unit = {},
        now: () -> Long = System::currentTimeMillis,
        onSaved: () -> Unit = {},
    ) {
        NewReminderSection(
            externalExitRequested = requested, onExternalExitCancelled = onRequestCancelled,
            currentTimeMillis = now,
            onVoiceDraftConsumed = {}, onVoiceInput = {}, isListeningForVoice = false,
            onBack = onBack,
            onAdd = { _, _, _, _, _, _, _ -> onSaved() },
            onUpdate = { _, _, _, _, _, _, _, _ -> onSaved() }, onDelete = {},
        )
    }
}
