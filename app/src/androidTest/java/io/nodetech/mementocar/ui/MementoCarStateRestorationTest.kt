// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.nodetech.mementocar.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Rule
import org.junit.Test

class MementoCarStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newReminderDraftSurvivesSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MementoCarTheme {
                NewReminderSection(
                    onVoiceDraftConsumed = {},
                    onVoiceInput = {},
                    isListeningForVoice = false,
                    onBack = {},
                    onAdd = { _, _, _, _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _, _, _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("Buy oil")
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Buy oil")
    }

    @Test
    fun editedDraftRequiresExplicitDiscardAndCanKeepEditing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var exited = false
        composeRule.setContent {
            MementoCarTheme {
                NewReminderSection(
                    onVoiceDraftConsumed = {}, onVoiceInput = {}, isListeningForVoice = false,
                    onBack = { exited = true },
                    onAdd = { _, _, _, _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _, _, _, _ -> }, onDelete = {},
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Buy oil")
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        composeRule.runOnIdle { assertFalse(exited) }
        composeRule.onNodeWithText(context.getString(R.string.keep_editing)).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Buy oil")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        composeRule.onNodeWithText(context.getString(R.string.discard_changes)).performClick()
        composeRule.runOnIdle { assertTrue(exited) }
    }
}
