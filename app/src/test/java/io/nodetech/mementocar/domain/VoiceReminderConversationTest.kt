// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.DataLimits
import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceReminderConversationTest {
    private fun initial() = VoiceReminderConversation(VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect))

    @Test fun changingTriggerRequiresANewConfirmation() {
        val changed = initial().reply("Al entrar.")
        assertEquals(ReminderTrigger.Connect, changed.command.trigger)
        assertEquals(VoiceConversationAction.Prompt, changed.action)
        assertEquals(VoiceConversationAction.Save, changed.reply("guardar").action)
        assertEquals(ReminderTrigger.Disconnect, changed.reply("on exit").command.trigger)
    }

    @Test fun replacingTextPreservesTheTriggerAndCannotSaveImmediately() {
        val editing = initial().reply("al entrar").reply("cambiar texto")
        assertEquals(true, editing.replacingText)
        val changed = editing.reply("Recoger las llaves.")
        assertEquals(VoiceCommand.AddItem("Recoger las llaves", ReminderTrigger.Connect), changed.command)
        assertEquals(false, changed.replacingText)
        assertEquals(VoiceConversationAction.Prompt, changed.action)
        assertEquals(VoiceConversationAction.Save, changed.reply("save").action)
    }

    @Test fun repeatingDuringReplacementRepeatsThePromptWithoutConsumingAFailure() {
        val state = initial().reply("change text").reply("repeat")
        assertEquals(true, state.replacingText)
        assertEquals(0, state.failures)
        assertEquals(VoiceConversationAction.Prompt, state.action)
    }

    @Test fun saveWhileReplacingDoesNotSaveTheOldText() {
        val editing = initial().reply("change text").reply("save")
        assertEquals(VoiceConversationAction.Prompt, editing.action)
        assertEquals(true, editing.replacingText)
        assertEquals("mochila", editing.command.label)
    }

    @Test fun repeatKeepsTheDraftWithoutSaving() {
        val repeated = initial().reply("Repeat!")
        assertEquals(initial().command, repeated.command)
        assertEquals(VoiceConversationAction.Prompt, repeated.action)
    }

    @Test fun threeUnrecognizedAnswersOrRecognitionFailuresCancel() {
        var state = initial().reply("no sé").recognitionFailed()
        assertEquals(VoiceConversationAction.Prompt, state.action)
        state = state.reply("tal vez")
        assertEquals(VoiceConversationAction.Cancel, state.action)
        assertEquals(VoiceConversationAction.Cancel, state.reply("guardar").action)
    }

    @Test fun repeatingIsBoundedEvenWithoutRecognitionErrors() {
        var state = initial()
        repeat(VoiceReminderConversation.MAX_TURNS) { state = state.reply("repetir") }
        assertEquals(VoiceConversationAction.Cancel, state.action)
    }

    @Test fun catalanConfirmationFlowChangesTriggerAndSaves() {
        val changed = initial().reply("en entrar")
        assertEquals(ReminderTrigger.Connect, changed.command.trigger)
        assertEquals(VoiceConversationAction.Prompt, changed.action)
        assertEquals(VoiceConversationAction.Save, changed.reply("d’acord").action)
        assertEquals(ReminderTrigger.Disconnect, changed.reply("quan surti").command.trigger)
    }

    @Test fun catalanCancelAndReplaceTextFlows() {
        assertEquals(VoiceConversationAction.Cancel, initial().reply("cancel·lar").action)
        assertEquals(VoiceConversationAction.Cancel, initial().reply("cancel.lar").action)
        val editing = initial().reply("canviar text")
        assertEquals(true, editing.replacingText)
        val changed = editing.reply("Recollir les claus.")
        assertEquals(VoiceCommand.AddItem("Recollir les claus", ReminderTrigger.Disconnect), changed.command)
        assertEquals(VoiceConversationAction.Prompt, changed.action)
        assertEquals(VoiceConversationAction.Save, changed.reply("desar").action)
    }

    @Test fun cancellingWhileEditingNeverSaves() {
        assertEquals(VoiceConversationAction.Cancel, initial().reply("change text").reply("cancel").action)
    }

    @Test fun replacementRejectsOverlongOrEmptyTextWithoutChangingTheDraft() {
        val editing = initial().reply("cambiar texto")
        listOf("...", "x".repeat(DataLimits.ReminderLabelMaxChars + 1)).forEach {
            val invalid = editing.reply(it)
            assertEquals(editing.command, invalid.command)
            assertEquals(true, invalid.replacingText)
            assertEquals(1, invalid.failures)
        }
    }

    @Test fun validReplacementAtTheTurnLimitCancelsRatherThanExtendingTheSession() {
        val editing = initial().copy(turns = VoiceReminderConversation.MAX_TURNS - 1, replacingText = true)
        assertEquals(VoiceConversationAction.Cancel, editing.reply("llaves").action)
    }

    @Test
    fun firstKnownHypothesisWins() {
        assertEquals(VoiceConversationAction.Save, initial().reply(listOf("guardar", "cancelar")).action)
        assertEquals(VoiceConversationAction.Cancel, initial().reply(listOf("cancelar", "guardar")).action)
    }

    @Test
    fun agreeingKnownAlternativesResolveAnUnknownFirstHypothesis() {
        val saved = initial().reply(listOf("guardó ya", "save", "sí"))
        assertEquals(VoiceConversationAction.Save, saved.action)
        assertEquals(0, saved.failures)
        assertEquals(1, saved.turns)
        assertEquals(ReminderTrigger.Connect, initial().reply(listOf("hola", "al entrar", "when entering")).command.trigger)
    }

    @Test
    fun disagreeingOrUnknownHypothesesAreAFailedTurn() {
        listOf(listOf("hola", "guardar", "cancelar"), listOf("hola", "mundo")).forEach { hypotheses ->
            val state = initial().reply(hypotheses)
            assertEquals(VoiceConversationAction.Prompt, state.action)
            assertEquals(1, state.failures)
            assertEquals(1, state.turns)
        }
    }

    @Test
    fun replacingTextOnlyUsesTheFirstHypothesis() {
        val editing = initial().reply("cambiar texto")
        val changed = editing.reply(listOf("Recoger las llaves.", "cancelar", "guardar"))
        assertEquals("Recoger las llaves", changed.command.label)
        assertEquals(VoiceConversationAction.Prompt, changed.action)
        assertEquals(0, changed.failures)
        // An alternative never becomes the label, and never cancels.
        val failed = editing.reply(listOf("guardar", "Recoger las llaves", "cancelar"))
        assertEquals(initial().command.label, failed.command.label)
        assertEquals(1, failed.failures)
        assertEquals(VoiceConversationAction.Prompt, failed.action)
        assertEquals(VoiceConversationAction.Cancel, editing.reply(listOf("cancelar", "llaves")).action)
        assertEquals(1, editing.reply(listOf("", "llaves")).failures)
    }
}
