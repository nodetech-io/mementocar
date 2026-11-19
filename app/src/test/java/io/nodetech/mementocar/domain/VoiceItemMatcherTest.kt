// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderEventEntity
import io.nodetech.mementocar.data.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceItemMatcherTest {
    private fun item(itemId: Long, label: String, eventId: Long = 1, at: Long = 100) =
        VoiceItemCandidate(eventId, itemId, label, at)

    private fun single(spoken: String, vararg items: VoiceItemCandidate): VoiceItemCandidate {
        val match = matchVoiceItem(spoken, items.toList())
        assertTrue("expected Single for '$spoken' but was $match", match is VoiceItemMatch.Single)
        return (match as VoiceItemMatch.Single).candidate
    }

    @Test
    fun singleExactMatch() {
        val wallet = item(1, "Cartera")
        assertEquals(wallet, single("cartera", wallet, item(2, "Llaves")))
    }

    @Test
    fun noMatchWhenNothingAgrees() {
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("paraguas", listOf(item(1, "Cartera"))))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("cartera", emptyList()))
    }

    @Test
    fun spokenTextMadeOnlyOfStopwordsIsNoMatch() {
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("la de el", listOf(item(1, "La cartera"))))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("", listOf(item(1, "Cartera"))))
    }

    @Test
    fun candidateMadeOnlyOfStopwordsNeverMatches() {
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("cartera", listOf(item(1, "la de"))))
    }

    @Test
    fun accentsAndCaseAreFolded() {
        val phone = item(1, "Teléfono Móvil")
        assertEquals(phone, single("TELEFONO movil", phone))
        assertEquals(phone, single("teléfono", phone))
    }

    @Test
    fun spanishArticlesAreIgnoredOnBothSides() {
        val glasses = item(1, "Mis gafas de sol")
        assertEquals(glasses, single("las gafas", glasses))
        assertEquals(glasses, single("gafas sol", glasses))
    }

    @Test
    fun catalanContractionsMatchWithoutTheArticle() {
        val computer = item(1, "L'ordinador")
        assertEquals(computer, single("l'ordinador", computer))
        assertEquals(computer, single("ordinador", computer))
        assertEquals(computer, single("l’ordinador", computer))
        assertEquals(computer, single("el ordinador", computer))
        val other = item(2, "ordinador")
        assertEquals(other, single("l'ordinador", other))
    }

    @Test
    fun catalanArticlesAndMiddleDot() {
        val college = item(1, "La motxilla del col·legi")
        assertEquals(college, single("la motxilla del col·legi", college))
        assertEquals(college, single("motxilla collegi", college))
        assertEquals(college, single("la meva motxilla", college))
        assertEquals(item(2, "D'aigua"), single("d'aigua", item(2, "aigua")).copy(label = "D'aigua"))
    }

    @Test
    fun englishArticlesAndPossessives() {
        val bag = item(1, "My gym bag")
        assertEquals(bag, single("the gym bag", bag))
        assertEquals(bag, single("bag", bag))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("my", listOf(bag)))
    }

    @Test
    fun longPrefixAndPluralVariantsMatch() {
        val backpacks = item(1, "mochilas")
        assertEquals(backpacks, single("mochila", backpacks))
        val glasses = item(2, "gafa")
        assertEquals(glasses, single("gafas", glasses))
    }

    @Test
    fun shortTokensOnlyMatchExactly() {
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("mon", listOf(item(1, "monedero"))))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("pil", listOf(item(1, "pilas"))))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("ca", listOf(item(1, "casco"))))
    }

    @Test
    fun everySpokenTokenMustAgree() {
        val a = item(1, "Llaves del coche")
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("llaves casa", listOf(a)))
        assertEquals(a, single("llaves coche", a))
    }

    @Test
    fun ambiguousWhenSeveralPartialMatches() {
        val a = item(1, "Llaves del coche")
        val b = item(2, "Llaves de casa")
        val match = matchVoiceItem("llaves", listOf(a, b))
        assertEquals(setOf(a, b), (match as VoiceItemMatch.Ambiguous).candidates.toSet())
    }

    @Test
    fun exactTokenSetBeatsPartialMatches() {
        val exact = item(1, "Llaves")
        val longer = item(2, "Llaves de casa")
        assertEquals(exact, single("las llaves", exact, longer))
    }

    @Test
    fun sameItemInSeveralEventsCollapsesToTheMostRecentEvent() {
        val older = item(5, "Cartera", eventId = 1, at = 10)
        val newer = item(5, "Cartera", eventId = 2, at = 20)
        assertEquals(newer, single("cartera", older, newer))
    }

    @Test
    fun differentItemsWithTheSameLabelAreAmbiguousNewestFirst() {
        val older = item(5, "Cartera", eventId = 1, at = 10)
        val newer = item(6, "Cartera", eventId = 2, at = 20)
        val match = matchVoiceItem("cartera", listOf(older, newer)) as VoiceItemMatch.Ambiguous
        assertEquals(listOf(newer, older), match.candidates)
    }

    @Test
    fun punctuationBetweenWordsDoesNotGlueTokens() {
        val a = item(1, "Llaves, cartera")
        assertEquals(a, single("llaves cartera", a))
        assertEquals(a, single("cartera, llaves", a))
    }

    @Test
    fun parserOutputFeedsTheMatcher() {
        val command = VoiceCommandParser().parse("marca l'ordinador") as VoiceCommand.MarkItem
        val computer = item(1, "Ordinador")
        assertEquals(computer, single(command.label, computer))
    }

    private fun event(id: Long, vehicleId: Long, at: Long, status: ReminderStatus = ReminderStatus.Pending) =
        ReminderEventEntity(id = id, vehicleId = vehicleId, disconnectedAtMillis = at, status = status)

    @Test
    fun latestOpenEventPicksTheNewestOpenOneOfTheVehicle() {
        val events = listOf(
            event(1, 7, at = 10),
            event(2, 7, at = 30, status = ReminderStatus.Snoozed),
            event(3, 7, at = 40, status = ReminderStatus.Completed),
            event(4, 8, at = 50),
            event(5, 7, at = 20, status = ReminderStatus.Dismissed),
        )
        assertEquals(2L, latestOpenReminderEvent(events, 7)?.id)
        assertEquals(4L, latestOpenReminderEvent(events, 8)?.id)
    }

    @Test
    fun latestOpenEventTieBreaksOnId() {
        assertEquals(9L, latestOpenReminderEvent(listOf(event(3, 1, 5), event(9, 1, 5)), 1)?.id)
    }

    @Test
    fun latestOpenEventIsNullWithoutOpenEvents() {
        assertNull(latestOpenReminderEvent(emptyList(), 1))
        assertNull(latestOpenReminderEvent(listOf(event(1, 1, 5, ReminderStatus.Completed)), 1))
        assertNull(latestOpenReminderEvent(listOf(event(1, 2, 5)), 1))
    }

    @Test
    fun completeAllNeedsAnExplicitYes() {
        assertTrue(isVoiceCompleteAllConfirmed(listOf("sí")))
        assertTrue(isVoiceCompleteAllConfirmed(listOf("yes", "guess")))
        assertTrue(isVoiceCompleteAllConfirmed(listOf("confirmar", "no")).not())
        assertFalse(isVoiceCompleteAllConfirmed(listOf("no")))
        assertFalse(isVoiceCompleteAllConfirmed(listOf("quizá")))
        assertFalse(isVoiceCompleteAllConfirmed(listOf("tal vez", "sí")))
        assertFalse(isVoiceCompleteAllConfirmed(emptyList()))
    }

    @Test
    fun singularAndPluralFormsAgree() {
        val backpacks = item(1, "Les motxilles")
        assertEquals(backpacks, single("la motxilla", backpacks))
        val glasses = item(2, "Ulleres de sol")
        assertEquals(glasses, single("ullera de sol", glasses))
        val keys = item(3, "Llaves de casa")
        assertEquals(keys, single("la llave", keys))
    }

    @Test
    fun shortStemsDoNotCollide() {
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("pato", listOf(item(1, "Pata"))))
        assertEquals(VoiceItemMatch.NoMatch, matchVoiceItem("carta", listOf(item(1, "Carteras"))))
    }

    @Test
    fun snoozePrefersLatestRingingAlertOverNewerSnoozedOne() {
        val events = listOf(
            event(1, 1, 10, ReminderStatus.Pending),
            event(2, 1, 20, ReminderStatus.Snoozed),
        )
        assertEquals(1L, voiceSnoozeTarget(events, 1)?.id)
        assertEquals(2L, voiceSnoozeTarget(listOf(event(2, 1, 20, ReminderStatus.Snoozed)), 1)?.id)
        assertNull(voiceSnoozeTarget(listOf(event(3, 1, 5, ReminderStatus.Completed)), 1))
    }
}
