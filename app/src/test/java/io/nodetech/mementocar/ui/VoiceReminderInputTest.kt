// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceReminderInputTest {
    @Test
    fun restoresVoiceInputModeFromSavedName() {
        assertEquals(VoiceInputMode.Review, voiceInputModeFromSavedName(VoiceInputMode.Review.name))
        assertEquals(VoiceInputMode.HandsFree, voiceInputModeFromSavedName(VoiceInputMode.HandsFree.name))
        assertEquals(VoiceInputMode.Confirmation, voiceInputModeFromSavedName(VoiceInputMode.Confirmation.name))
    }

    @Test
    fun fallsBackToReviewForMissingOrInvalidSavedMode() {
        assertEquals(VoiceInputMode.Review, voiceInputModeFromSavedName(null))
        assertEquals(VoiceInputMode.Review, voiceInputModeFromSavedName("invalid"))
    }

    @Test
    fun usesSupportedAppLocaleForRecognition() {
        assertEquals("en-US", voiceRecognitionLanguageTag(Locale.forLanguageTag("en-US")))
        assertEquals("es-MX", voiceRecognitionLanguageTag(Locale.forLanguageTag("es-MX")))
    }

    @Test
    fun keepsCatalanLocaleForRecognition() {
        assertEquals("ca-ES", voiceRecognitionLanguageTag(Locale.forLanguageTag("ca-ES")))
    }

    @Test
    fun fallsBackToSpanishForUnsupportedLocale() {
        assertEquals("es-ES", voiceRecognitionLanguageTag(Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun cleansRecognitionResults() {
        assertEquals(
            listOf("uno", "dos", "Uno"),
            cleanRecognitionResults(listOf(" uno ", "", "dos", "  ", null, "uno", "Uno")),
        )
        assertEquals(emptyList<String>(), cleanRecognitionResults(null))
        assertEquals(emptyList<String>(), cleanRecognitionResults(listOf(" ", "")))
    }

    @Test
    fun catalanFallsBackToSpanishOnlyWhenKnownUnsupported() {
        val ca = Locale.forLanguageTag("ca-ES")
        assertEquals(RecognitionLanguageDecision("ca-ES", false), recognitionLanguageDecision(ca, null))
        assertEquals(
            RecognitionLanguageDecision("ca-ES", false),
            recognitionLanguageDecision(ca, RecognitionLanguages(listOf("ca-ES"), emptyList())),
        )
        assertEquals(
            RecognitionLanguageDecision("ca-ES", false),
            recognitionLanguageDecision(ca, RecognitionLanguages(emptyList(), listOf("en-US", "ca"))),
        )
        assertEquals(
            RecognitionLanguageDecision("es-ES", true),
            recognitionLanguageDecision(ca, RecognitionLanguages(listOf("es-ES"), listOf("en-US"))),
        )
        // Empty lists mean the recognizer did not report support: keep Catalan instead of guessing.
        assertEquals(RecognitionLanguageDecision("ca-ES", false), recognitionLanguageDecision(ca, RecognitionLanguages(emptyList(), emptyList())))
    }

    @Test
    fun nonCatalanLocalesNeverFallBack() {
        val none = RecognitionLanguages(emptyList(), emptyList())
        assertEquals(RecognitionLanguageDecision("en-US", false), recognitionLanguageDecision(Locale.forLanguageTag("en-US"), none))
        assertEquals(RecognitionLanguageDecision("es-MX", false), recognitionLanguageDecision(Locale.forLanguageTag("es-MX"), none))
        assertEquals(RecognitionLanguageDecision("es-ES", false), recognitionLanguageDecision(Locale.FRANCE, none))
    }

    @Test
    fun languageErrorsAreRecognizedOnlyOnApi31Plus() {
        assertEquals(true, isRecognitionLanguageError(12, 31))
        assertEquals(true, isRecognitionLanguageError(13, 34))
        assertEquals(false, isRecognitionLanguageError(12, 30))
        assertEquals(false, isRecognitionLanguageError(7, 34))
    }
}
