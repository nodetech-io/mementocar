// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.nodetech.mementocar.R
import io.nodetech.mementocar.ui.theme.MementoCarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The "About" section links to the source code, the maker and the privacy policy. */
class AboutSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sourceCodeRowIsAButtonThatOpensOnlyTheSourceCode() {
        var sourceClicks by mutableIntStateOf(0)
        var otherClicks by mutableIntStateOf(0)
        composeRule.setContent {
            MementoCarTheme {
                Surface {
                    Column {
                        AboutSection(
                            onOpenLanguageSettings = null,
                            onOpenSourceCode = { sourceClicks++ },
                            onOpenNodetech = { otherClicks++ },
                            onOpenPrivacyPolicy = { otherClicks++ },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.about_source_code_detail)).assertExists()
        composeRule.onNode(
            hasText(context.getString(R.string.about_source_code)) and hasClickAction(),
        )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, sourceClicks)
            assertEquals(0, otherClicks)
        }
    }
}
