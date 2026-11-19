// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

/** Main-thread coordinator for asynchronous TTS initialization and screen lifecycle. */
internal class VoiceConfirmationPlayback(
    alreadyStarted: Boolean,
    private val speak: () -> Boolean,
    private val stopOutput: () -> Unit,
    private val onStarted: () -> Unit,
    private val onComplete: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private var ready = false
    private var resumed = false
    private var started = alreadyStarted
    private var terminal = alreadyStarted

    fun onReady(success: Boolean) {
        if (terminal) return
        if (!success) {
            onError()
            return
        }
        ready = true
        startIfReady()
    }

    fun onResume() {
        resumed = true
        startIfReady()
    }

    fun onPause() {
        resumed = false
        // An interrupted prompt must never start listening later in the background.
        if (started) stop()
    }

    private fun startIfReady() {
        if (terminal || started || !ready || !resumed) return
        started = true
        onStarted()
        if (!runCatching(speak).getOrDefault(false)) onError()
    }

    fun onDone() {
        if (terminal || !started || !resumed) return
        terminal = true
        onComplete()
    }

    fun onError() {
        if (terminal) return
        stop()
        onUnavailable()
    }

    fun stop() {
        terminal = true
        runCatching(stopOutput)
    }
}
