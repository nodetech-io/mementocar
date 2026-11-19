// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ActiveDays
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Rule
import org.junit.Test

class MementoCarAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dayToggleHasMinimumTouchTargetAndToggleSemantics() {
        composeRule.setContent {
            MementoCarTheme {
                var selected by remember { mutableStateOf(false) }
                DayToggle(
                    modifier = Modifier,
                    label = "M",
                    selected = selected,
                    onClick = { selected = !selected },
                )
            }
        }
        composeRule.onNodeWithText("M")
            .assertHeightIsAtLeast(48.dp)
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun allDaysHaveNamedTouchTargetsInNarrowLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MementoCarTheme {
                Box(Modifier.width(280.dp)) {
                    ActiveDaysSelector(selectedMask = ActiveDays.All, onSelected = {})
                }
            }
        }
        activeDayOptions.forEach { day ->
            composeRule.onNodeWithContentDescription(context.getString(day.fullLabelRes))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assertIsOn()
        }
    }
}
