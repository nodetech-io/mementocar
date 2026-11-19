// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ReminderOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}
