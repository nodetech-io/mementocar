// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderOperationCoordinatorTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun operationsRunExclusivelyUntilAllSideEffectsFinish() = runTest {
        val coordinator = ReminderOperationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var secondEntered = false

        launch {
            coordinator.runExclusive {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        launch {
            coordinator.runExclusive {
                secondEntered = true
            }
        }
        runCurrent()
        assertFalse(secondEntered)

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertTrue(secondEntered)
    }
}
