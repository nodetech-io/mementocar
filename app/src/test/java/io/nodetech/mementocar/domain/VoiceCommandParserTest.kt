// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private val parser = VoiceCommandParser()

    @Test
    fun preservesEntryTriggerWithDictationPunctuationAndWhitespace() {
        listOf(
            "  Recuérdame   pagar parking al entrar.  ",
            "¡Recuérdame pagar parking al entrar!",
            "recuérdame pagar parking al\nentrar…",
        ).forEach { input ->
            assertEquals(VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect), parser.parse(input))
        }
        assertEquals(
            VoiceCommand.AddItem("open the gate", ReminderTrigger.Connect),
            parser.parse("Remind me to open the gate when entering."),
        )
        assertTrue(parser.parse("Recuérdame al entrar.") is VoiceCommand.Unknown)
    }

    @Test
    fun confirmationRequiresAnExplicitUnambiguousAnswer() {
        listOf("Sí.", "guardar", "YES!", "confirm").forEach {
            assertEquals(VoiceConfirmation.Confirm, parseVoiceConfirmation(it))
        }
        listOf("No.", "cancelar", "Cancel!").forEach {
            assertEquals(VoiceConfirmation.Cancel, parseVoiceConfirmation(it))
        }
        listOf("", "no guardar", "sí pero cambia el coche", "save cancel", "mochila").forEach {
            assertEquals(VoiceConfirmation.Unknown, parseVoiceConfirmation(it))
        }
    }

    @Test
    fun parsesAddItemCommands() {
        assertEquals(
            VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect),
            parser.parse("añadir mochila"),
        )
        assertEquals(
            VoiceCommand.AddItem("medicación", ReminderTrigger.Disconnect),
            parser.parse("recordar medicación"),
        )
        assertEquals(
            VoiceCommand.AddItem("comprar aceite", ReminderTrigger.Disconnect),
            parser.parse("recuérdame comprar aceite al salir"),
        )
        assertEquals(
            VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect),
            parser.parse("recuerdame pagar parking al entrar"),
        )
        assertEquals(
            VoiceCommand.AddItem("cargar gafas de sol", ReminderTrigger.Disconnect),
            parser.parse("recuérdame cargar gafas de sol cuando salga"),
        )
        assertEquals(
            VoiceCommand.AddItem("abrir la barrera", ReminderTrigger.Connect),
            parser.parse("recuérdame abrir la barrera cuando entre"),
        )
    }

    @Test
    fun parsesEnglishAddItemCommands() {
        assertEquals(
            VoiceCommand.AddItem("backpack", ReminderTrigger.Disconnect),
            parser.parse("add backpack"),
        )
        assertEquals(
            VoiceCommand.AddItem("buy oil", ReminderTrigger.Disconnect),
            parser.parse("remind me to buy oil when leaving"),
        )
        assertEquals(
            VoiceCommand.AddItem("open the gate", ReminderTrigger.Connect),
            parser.parse("remind me open the gate when entering"),
        )
    }

    @Test
    fun parsesCompleteAndSnoozeCommands() {
        assertEquals(VoiceCommand.CompleteAll, parser.parse("todo listo"))
        assertEquals(VoiceCommand.Snooze, parser.parse("posponer"))
        assertEquals(VoiceCommand.CompleteAll, parser.parse("all done"))
        assertEquals(VoiceCommand.Snooze, parser.parse("snooze"))
    }

    @Test
    fun parsesEnglishMarkCommands() {
        assertEquals(VoiceCommand.MarkItem("backpack"), parser.parse("mark backpack"))
        assertEquals(VoiceCommand.MarkItem("keys"), parser.parse("i took keys"))
    }

    @Test
    fun parsesCatalanAddItemCommands() {
        assertEquals(VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect), parser.parse("afegir mochila"))
        assertEquals(VoiceCommand.AddItem("mochila", ReminderTrigger.Disconnect), parser.parse("afegeix mochila"))
        assertEquals(VoiceCommand.AddItem("medicació", ReminderTrigger.Disconnect), parser.parse("recordar medicació"))
        assertEquals(
            VoiceCommand.AddItem("comprar oli", ReminderTrigger.Disconnect),
            parser.parse("recorda'm comprar oli en sortir"),
        )
        assertEquals(
            VoiceCommand.AddItem("comprar oli", ReminderTrigger.Disconnect),
            parser.parse("Recorda’m comprar oli quan surti"),
        )
        assertEquals(
            VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect),
            parser.parse("recorda'm pagar parking en entrar"),
        )
        assertEquals(
            VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect),
            parser.parse("afegeix pagar parking quan entri"),
        )
        assertEquals(
            VoiceCommand.AddItem("obrir la barrera", ReminderTrigger.Connect),
            parser.parse("afegir obrir la barrera en entrar"),
        )
        assertTrue(parser.parse("afegir") is VoiceCommand.Unknown)
    }

    @Test
    fun parsesCatalanMarkCommands() {
        assertEquals(VoiceCommand.MarkItem("mochila"), parser.parse("marcar mochila"))
        assertEquals(VoiceCommand.MarkItem("les claus"), parser.parse("marca les claus"))
        assertEquals(VoiceCommand.MarkItem("les claus"), parser.parse("he agafat les claus"))
    }

    @Test
    fun parsesCatalanCompleteAndSnoozeCommands() {
        listOf("tot fet", "tot llest", "completar tot", "marcar tot fet", "Tot llest.").forEach {
            assertEquals(VoiceCommand.CompleteAll, parser.parse(it))
        }
        listOf("ajornar", "ajornar recordatori", "posposar", "Ajornar!").forEach {
            assertEquals(VoiceCommand.Snooze, parser.parse(it))
        }
    }

    @Test
    fun catalanGeminatedLVariantsNormalizeToMiddleDot() {
        listOf("cancel·lar", "cancel.lar", "cancel•lar", "cancel‧lar", "canceŀlar", "CANCEĿLAR", "Cancel·lar!", "CANCEL•LAR").forEach {
            assertEquals("cancel·lar", normalizeVoiceText(it))
        }
    }

    @Test
    fun catalanTypographicApostropheMatchesStraightApostrophe() {
        assertEquals("recorda'm", normalizeVoiceText("Recorda’m"))
        assertEquals(
            VoiceCommand.AddItem("comprar pa", ReminderTrigger.Disconnect),
            parser.parse("Recorda’m comprar pa"),
        )
    }

    @Test
    fun edgePunctuationTrimmingIsNotAffectedByMiddleDotNormalization() {
        assertEquals("lar", normalizeVoiceText(".lar"))
        assertEquals("cancel·lar", normalizeVoiceText("cancel·lar…"))
        assertEquals("v1.2 de l'oli", normalizeVoiceText("v1.2 de l'oli"))
        assertEquals("ŀ", normalizeVoiceText("ŀ"))
    }

    @Test
    fun catalanConfirmationsAreRecognized() {
        listOf("desar", "D'acord.", "d’acord", "Desar!").forEach {
            assertEquals(VoiceConfirmation.Confirm, parseVoiceConfirmation(it))
        }
        listOf("cancel·lar", "cancel·la", "cancel.lar", "cancel•lar", "cancel‧lar", "canceŀlar", "CANCEĿLAR", "cancellar", "Cancel·lar.").forEach {
            assertEquals(VoiceConfirmation.Cancel, parseVoiceConfirmation(it))
        }
        listOf("repeteix", "Repeteix!").forEach {
            assertEquals(VoiceConfirmation.Repeat, parseVoiceConfirmation(it))
        }
        listOf("canviar text", "canvia el text").forEach {
            assertEquals(VoiceConfirmation.ChangeText, parseVoiceConfirmation(it))
        }
        listOf("en entrar", "quan entri").forEach {
            assertEquals(VoiceConfirmation.OnEntry, parseVoiceConfirmation(it))
        }
        listOf("en sortir", "quan surti").forEach {
            assertEquals(VoiceConfirmation.OnExit, parseVoiceConfirmation(it))
        }
        listOf("en", "desa-ho més tard").forEach {
            assertEquals(VoiceConfirmation.Unknown, parseVoiceConfirmation(it))
        }
    }

    @Test
    fun returnsUnknownForUnsupportedCommands() {
        assertTrue(parser.parse("abre la puerta") is VoiceCommand.Unknown)
        assertTrue(parser.parse("recordar") is VoiceCommand.Unknown)
        assertTrue(parser.parse("recuérdame al salir") is VoiceCommand.Unknown)
    }

    @Test
    fun parseBestPrefersAddThenKnownThenFirstHypothesis() {
        assertEquals(
            VoiceCommand.AddItem("llaves"),
            parser.parseBest(listOf("marcar llaves", "recuérdame llaves", "añadir otra")),
        )
        assertEquals(VoiceCommand.MarkItem("llaves"), parser.parseBest(listOf("blah", "marcar llaves", "snooze")))
        assertEquals(VoiceCommand.Unknown("  Blah. "), parser.parseBest(listOf("  Blah. ", "otra cosa")))
        assertEquals(VoiceCommand.Unknown(""), parser.parseBest(emptyList()))
    }

    @Test
    fun keywordsIgnoreAccentsApostrophesAndMiddleDots() {
        assertEquals("recordam", matchKey("recorda'm"))
        assertEquals("recordam", matchKey("recorda’m"))
        assertEquals("dacord", matchKey("d'acord"))
        assertEquals("cancellar", matchKey("cancel·lar"))
        assertEquals("anadir", matchKey("añadir"))
        assertEquals("si", matchKey("sí"))
        assertEquals("recuerdame", matchKey("recuérdame"))
        assertEquals(VoiceCommand.AddItem("llaves"), parser.parse("anadir llaves"))
        assertEquals(VoiceCommand.AddItem("llaves"), parser.parse("recuerdame llaves"))
        assertEquals(VoiceCommand.AddItem("les claus"), parser.parse("recordam les claus"))
        assertEquals(VoiceCommand.MarkItem("llaves"), parser.parse("he cogído llaves"))
        assertEquals(VoiceCommand.CompleteAll, parser.parse("Todo listó"))
        assertEquals(VoiceConfirmation.Confirm, parseVoiceConfirmation("si"))
        assertEquals(VoiceConfirmation.Confirm, parseVoiceConfirmation("dacord"))
        assertEquals(VoiceConfirmation.Confirm, parseVoiceConfirmation("D’acord."))
        assertEquals(VoiceConfirmation.Cancel, parseVoiceConfirmation("cancellar"))
        assertEquals(VoiceConfirmation.Cancel, parseVoiceConfirmation("cancel·la"))
        assertEquals(VoiceConfirmation.OnEntry, parseVoiceConfirmation("al éntrar"))
    }

    @Test
    fun labelKeepsOriginalAccentsApostrophesAndMiddleDots() {
        assertEquals(
            VoiceCommand.AddItem("l'ordinador i el mòbil"),
            parser.parse("afegeix l'ordinador i el mòbil"),
        )
        assertEquals(
            VoiceCommand.AddItem("recollir col·legi", ReminderTrigger.Connect),
            parser.parse("Recorda'm recollir col·legi quan entri."),
        )
        assertEquals(VoiceCommand.AddItem("la càmera"), parser.parse("Añadir la càmera"))
        assertEquals(VoiceCommand.MarkItem("l'abric"), parser.parse("marca l'abric"))
        assertTrue(parser.parse("recorda'm quan entri") is VoiceCommand.Unknown)
    }

    @Test
    fun foldedKeywordPhrasesMapToASingleCommand() {
        val byKey = mutableMapOf<String, String>()
        fun register(kind: String, phrases: List<Phrase>) = phrases.forEach { phrase ->
            val previous = byKey.put(phrase.key, kind)
            assertTrue("${phrase.text} collides: $previous vs $kind", previous == null || previous == kind)
        }
        register("complete", CompleteAllPhrases)
        register("snooze", SnoozePhrases)
        register("add", AddPrefixes)
        register("mark", MarkPrefixes)
        val triggers = mutableMapOf<String, ReminderTrigger>()
        Triggers.forEach { (phrase, trigger) ->
            val previous = triggers.put(phrase.key, trigger)
            assertTrue("${phrase.text} collides", previous == null || previous == trigger)
        }
        val confirmations = mutableMapOf<String, VoiceConfirmation>()
        ConfirmationSynonyms.forEach { (confirmation, synonyms) ->
            synonyms.forEach { synonym ->
                val key = Phrase(synonym).key
                val previous = confirmations.put(key, confirmation)
                assertTrue("$synonym collides", previous == null || previous == confirmation)
                assertEquals(confirmation, parseVoiceConfirmation(synonym))
            }
        }
        // Every full-phrase command still parses to the command it is registered for.
        CompleteAllPhrases.forEach { assertEquals(VoiceCommand.CompleteAll, parser.parse(it.text)) }
        SnoozePhrases.forEach { assertEquals(VoiceCommand.Snooze, parser.parse(it.text)) }
        AddPrefixes.forEach { assertTrue(parser.parse("${it.text} algo") is VoiceCommand.AddItem) }
        MarkPrefixes.forEach { assertTrue(parser.parse("${it.text} algo") is VoiceCommand.MarkItem) }
    }

    @Test
    fun defaultModeStillRejectsFreeText() {
        assertTrue(parser.parse("comprar pan al salir") is VoiceCommand.Unknown)
        assertTrue(parser.parseBest(listOf("comprar pan")) is VoiceCommand.Unknown)
    }

    @Test
    fun implicitAddAcceptsFreeTextInAllLanguages() {
        assertEquals(VoiceCommand.AddItem("comprar pan", ReminderTrigger.Disconnect), parser.parse("Comprar pan", implicitAdd = true))
        assertEquals(VoiceCommand.AddItem("buy milk", ReminderTrigger.Disconnect), parser.parse("buy milk.", implicitAdd = true))
        assertEquals(VoiceCommand.AddItem("comprar oli", ReminderTrigger.Disconnect), parser.parse("comprar oli", implicitAdd = true))
    }

    @Test
    fun implicitAddStripsTrailingTrigger() {
        assertEquals(VoiceCommand.AddItem("comprar pan", ReminderTrigger.Disconnect), parser.parse("comprar pan al salir", implicitAdd = true))
        assertEquals(VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect), parser.parse("pagar parking al entrar.", implicitAdd = true))
        assertEquals(VoiceCommand.AddItem("comprar oli", ReminderTrigger.Disconnect), parser.parse("comprar oli quan surti", implicitAdd = true))
        assertEquals(VoiceCommand.AddItem("take umbrella", ReminderTrigger.Disconnect), parser.parse("take umbrella on exit", implicitAdd = true))
    }

    @Test
    fun implicitAddRejectsBlankAndTriggerOnly() {
        listOf("", "   ", "al salir", "quan entri.", "recuérdame al entrar", "añadir", "marcar").forEach {
            assertTrue(it, parser.parse(it, implicitAdd = true) is VoiceCommand.Unknown)
        }
    }

    @Test
    fun implicitAddKeepsExplicitCommandsPriority() {
        assertEquals(VoiceCommand.Snooze, parser.parse("posponer", implicitAdd = true))
        assertEquals(VoiceCommand.CompleteAll, parser.parse("todo listo", implicitAdd = true))
        assertEquals(VoiceCommand.MarkItem("gafas"), parser.parse("marcar gafas", implicitAdd = true))
        assertEquals(
            VoiceCommand.AddItem("pagar parking", ReminderTrigger.Connect),
            parser.parse("recuérdame pagar parking al entrar", implicitAdd = true),
        )
    }

    @Test
    fun parseBestWithImplicitAddPrefersExplicitThenFirstFreeText() {
        assertEquals(
            VoiceCommand.AddItem("pagar", ReminderTrigger.Disconnect),
            parser.parseBest(listOf("comprar pan", "recuérdame pagar"), implicitAdd = true),
        )
        assertEquals(VoiceCommand.Snooze, parser.parseBest(listOf("comprar pan", "posponer"), implicitAdd = true))
        assertEquals(
            VoiceCommand.AddItem("comprar pan", ReminderTrigger.Disconnect),
            parser.parseBest(listOf("", "comprar pan al salir", "comprar pa"), implicitAdd = true),
        )
        assertTrue(parser.parseBest(listOf("", " "), implicitAdd = true) is VoiceCommand.Unknown)
        assertEquals(
            VoiceCommand.AddItem("comprar pan", ReminderTrigger.Disconnect),
            parser.parseBest(listOf("al salir", "comprar pan al salir"), implicitAdd = true),
        )
    }
}
