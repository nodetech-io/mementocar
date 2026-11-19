// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSnackbarDurationTest {
    @Test
    fun successAndInfoAreShort() {
        assertEquals(SnackbarDuration.Short, messageSnackbarDuration(MessageKind.Success))
        assertEquals(SnackbarDuration.Short, messageSnackbarDuration(MessageKind.Info))
    }

    @Test
    fun warningAndErrorAreLong() {
        assertEquals(SnackbarDuration.Long, messageSnackbarDuration(MessageKind.Warning))
        assertEquals(SnackbarDuration.Long, messageSnackbarDuration(MessageKind.Error))
    }

    @Test
    fun visualsUseTheDurationOfTheirKind() {
        MessageKind.entries.forEach { kind ->
            assertEquals(
                messageSnackbarDuration(kind),
                MessageSnackbarVisuals(UiMessage("text", kind)).duration,
            )
        }
    }
}
