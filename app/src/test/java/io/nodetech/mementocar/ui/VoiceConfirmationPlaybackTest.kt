// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConfirmationPlaybackTest {
    private class Fixture(alreadyStarted: Boolean = false, failSpeak: Boolean = false) {
        var starts = 0
        var listens = 0
        var failures = 0
        var stops = 0
        val playback = VoiceConfirmationPlayback(
            alreadyStarted = alreadyStarted,
            speak = {
                starts++
                if (failSpeak) error("TTS service died")
                true
            },
            stopOutput = { stops++ },
            onStarted = {},
            onComplete = { listens++ },
            onUnavailable = { failures++ },
        )
    }

    @Test fun initializationBeforeResumeStillReadsThePrompt() {
        val f = Fixture()
        f.playback.onReady(true)
        assertEquals(0, f.starts)
        f.playback.onResume()
        assertEquals(1, f.starts)
        f.playback.onDone()
        assertEquals(1, f.listens)
    }

    @Test fun initializationAfterResumeReadsOnlyOnce() {
        val f = Fixture()
        f.playback.onResume()
        f.playback.onReady(true)
        f.playback.onReady(true)
        f.playback.onResume()
        f.playback.onDone()
        f.playback.onDone()
        assertEquals(1, f.starts)
        assertEquals(1, f.listens)
    }

    @Test fun stoppingBeforeInitializationPreventsLatePlayback() {
        val f = Fixture()
        f.playback.stop()
        f.playback.onResume()
        f.playback.onReady(true)
        f.playback.onDone()
        assertEquals(0, f.starts)
        assertEquals(0, f.listens)
    }

    @Test fun leavingTheScreenPreventsLateAutomaticListening() {
        val f = Fixture()
        f.playback.onResume()
        f.playback.onReady(true)
        f.playback.onPause()
        f.playback.onResume()
        f.playback.onDone()
        assertEquals(1, f.starts)
        assertEquals(0, f.listens)
    }

    @Test fun initializationFailureOrTimeoutCannotLaterStartListening() {
        val f = Fixture()
        f.playback.onReady(false)
        f.playback.onReady(true)
        f.playback.onResume()
        f.playback.onDone()
        assertEquals(1, f.failures)
        assertEquals(0, f.starts)
        assertEquals(0, f.listens)
    }

    @Test fun serviceExceptionFallsBackWithoutCrashingOrListening() {
        val f = Fixture(failSpeak = true)
        f.playback.onResume()
        f.playback.onReady(true)
        f.playback.onDone()
        assertEquals(1, f.failures)
        assertEquals(0, f.listens)
    }

    @Test fun restoredPromptNeverRepeatsOrOpensAnotherRecognizer() {
        val f = Fixture(alreadyStarted = true)
        f.playback.onResume()
        f.playback.onReady(true)
        f.playback.onDone()
        assertEquals(0, f.starts)
        assertEquals(0, f.listens)
    }
}
