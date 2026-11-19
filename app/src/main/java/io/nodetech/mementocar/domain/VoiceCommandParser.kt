// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderTrigger
import java.text.Normalizer
import java.util.Locale

sealed interface VoiceCommand {
    data class AddItem(
        val label: String,
        val trigger: ReminderTrigger = ReminderTrigger.Disconnect,
    ) : VoiceCommand
    data class MarkItem(val label: String) : VoiceCommand
    data object CompleteAll : VoiceCommand
    data object Snooze : VoiceCommand
    data class Unknown(val raw: String) : VoiceCommand
}

class VoiceCommandParser {
    /**
     * @param implicitAdd opt-in for reminder-creation contexts (Dictar button, hands-free shortcut): text that is not
     * an explicit command is taken as the reminder text instead of Unknown. Explicit commands keep priority.
     */
    fun parse(input: String, implicitAdd: Boolean = false): VoiceCommand {
        val normalized = normalizeVoiceText(input)
        if (normalized.isBlank()) return VoiceCommand.Unknown(input)
        val words = normalized.split(' ').filter { it.isNotEmpty() }
        val keys = words.map(::matchKey)
        if (CompleteAllPhrases.any { it.keys == keys }) return VoiceCommand.CompleteAll
        if (SnoozePhrases.any { it.keys == keys }) return VoiceCommand.Snooze
        AddPrefixes.firstOrNull { keys.startsWithKeys(it.keys) }?.let { return parseAddValue(normalized, words, it) }
        MarkPrefixes.firstOrNull { keys.startsWithKeys(it.keys) }?.let { return parseMarkValue(normalized, words, it) }
        val isBarePrefix = (AddPrefixes + MarkPrefixes).any { it.keys == keys }
        if (implicitAdd && !isBarePrefix) {
            val (label, trigger) = normalized.withTrigger()
            if (label.isNotBlank()) return VoiceCommand.AddItem(label, trigger)
        }
        return VoiceCommand.Unknown(input)
    }

    /**
     * Picks the best of several recognizer hypotheses: the first one that is an add command, otherwise the first
     * one that is any known command, otherwise Unknown carrying the first (most probable) hypothesis.
     * With [implicitAdd], before giving up the first hypothesis that yields a non-blank label is taken as a free-text add.
     */
    fun parseBest(hypotheses: List<String>, implicitAdd: Boolean = false): VoiceCommand {
        val parsed = hypotheses.map { parse(it) }
        return parsed.firstOrNull { it is VoiceCommand.AddItem }
            ?: parsed.firstOrNull { it !is VoiceCommand.Unknown }
            ?: (if (implicitAdd) {
                hypotheses.firstNotNullOfOrNull { parse(it, implicitAdd = true) as? VoiceCommand.AddItem }
            } else {
                null
            })
            ?: VoiceCommand.Unknown(hypotheses.firstOrNull().orEmpty())
    }

    private fun List<String>.startsWithKeys(prefix: List<String>): Boolean =
        size > prefix.size && take(prefix.size) == prefix

    private fun parseAddValue(normalized: String, words: List<String>, prefix: Phrase): VoiceCommand {
        val rawValue = words.drop(prefix.keys.size).joinToString(" ").trim()
        if (rawValue.isBlank()) return VoiceCommand.Unknown(normalized)
        val (label, trigger) = rawValue.withTrigger()
        return if (label.isBlank()) {
            VoiceCommand.Unknown(normalized)
        } else {
            VoiceCommand.AddItem(label, trigger)
        }
    }

    private fun parseMarkValue(normalized: String, words: List<String>, prefix: Phrase): VoiceCommand {
        val value = words.drop(prefix.keys.size).joinToString(" ").trim()
        return if (value.isBlank()) VoiceCommand.Unknown(normalized) else VoiceCommand.MarkItem(value)
    }

    private fun String.withTrigger(): Pair<String, ReminderTrigger> {
        val words = split(' ').filter { it.isNotEmpty() }
        val keys = words.map(::matchKey)
        val match = Triggers.firstOrNull { (phrase, _) ->
            keys.size >= phrase.keys.size && keys.takeLast(phrase.keys.size) == phrase.keys
        } ?: return this to ReminderTrigger.Disconnect
        val label = words.dropLast(match.first.keys.size).joinToString(" ").trim()
        return label to match.second
    }
}

internal class Phrase(val text: String) {
    val keys: List<String> = text.split(' ').map(::matchKey)
    val key: String = keys.joinToString(" ")
}

/**
 * Catalan geminated L ("l·l") is often dictated or typed with a dot, bullet or hyphenation point instead of
 * the middle dot, or with the precomposed ŀ (U+0140, "l" + middle dot: "coŀlegi" == "col·legi").
 * Only separators between letters are rewritten, so edge punctuation trimming is unaffected.
 */
private val CatalanGeminatedLSeparator = Regex("(?<=l)[.•‧](?=l)")
private val CatalanPrecomposedL = Regex("ŀ(?=l)")

internal fun normalizeVoiceText(input: String): String = input
    .trim()
    .trim { it in ".,;:!?¿¡…\"“”" }
    .trim()
    .lowercase(Locale.ROOT)
    .replace('’', '\'')
    .replace(CatalanPrecomposedL, "l·")
    .replace(CatalanGeminatedLSeparator, "·")
    .replace(Regex("\\s+"), " ")

internal enum class VoiceConfirmation { Confirm, Cancel, Repeat, ChangeText, OnEntry, OnExit, Unknown }

/**
 * Folds a word for keyword comparison: normalizeVoiceText semantics, then accents (NFD marks), the Catalan middle
 * dot and apostrophes are dropped, so "recorda'm" == "recordam", "cancel·lar" == "cancellar", "sí" == "si".
 */
internal fun matchKey(word: String): String = Normalizer.normalize(normalizeVoiceText(word), Normalizer.Form.NFD)
    .replace(CombiningMarks, "")
    .replace("·", "")
    .replace("'", "")

private val CombiningMarks = Regex("\\p{Mn}")

internal val ConfirmationSynonyms: List<Pair<VoiceConfirmation, List<String>>> = listOf(
    VoiceConfirmation.Confirm to listOf("sí", "confirmar", "guardar", "desar", "d'acord", "yes", "confirm", "save"),
    VoiceConfirmation.Cancel to listOf("no", "cancelar", "cancela", "cancel·lar", "cancel·la", "cancel"),
    VoiceConfirmation.Repeat to listOf("repetir", "repite", "repeteix", "repeat"),
    VoiceConfirmation.ChangeText to listOf("cambiar texto", "cambia el texto", "canviar text", "canvia el text", "change text"),
    VoiceConfirmation.OnEntry to listOf("al entrar", "cuando entre", "en entrar", "quan entri", "on entry", "when entering"),
    VoiceConfirmation.OnExit to listOf("al salir", "cuando salga", "en sortir", "quan surti", "on exit", "when leaving"),
)

private val ConfirmationByKey: Map<String, VoiceConfirmation> = ConfirmationSynonyms
    .flatMap { (confirmation, synonyms) -> synonyms.map { Phrase(it).key to confirmation } }
    .toMap()

internal fun parseVoiceConfirmation(input: String): VoiceConfirmation {
    val normalized = normalizeVoiceText(input)
    if (normalized.isBlank()) return VoiceConfirmation.Unknown
    val key = normalized.split(' ').filter { it.isNotEmpty() }.joinToString(" ", transform = ::matchKey)
    return ConfirmationByKey[key] ?: VoiceConfirmation.Unknown
}

// Declared last: top-level properties initialize in textual order and these use the regexes above.
private fun phrases(vararg texts: String) = texts.map(::Phrase)

internal val CompleteAllPhrases = phrases(
    "todo listo", "marcar todo listo", "completar todo", "all done", "mark all done", "complete all",
    "tot fet", "tot llest", "completar tot", "marcar tot fet",
)
internal val SnoozePhrases = phrases(
    "posponer", "posponer recordatorio", "ajornar", "ajornar recordatori", "posposar", "snooze", "snooze reminder",
)
internal val AddPrefixes = phrases(
    "añadir", "agregar", "afegir", "afegeix", "recordar", "recorda'm", "recuérdame",
    "add", "remember", "remind me to", "remind me",
)
internal val MarkPrefixes = phrases(
    "marcar", "marca", "he cogido", "he agafat", "mark", "i took", "i have taken",
)
internal val Triggers = listOf(
    "cuando salga" to ReminderTrigger.Disconnect,
    "al salir" to ReminderTrigger.Disconnect,
    "quan surti" to ReminderTrigger.Disconnect,
    "en sortir" to ReminderTrigger.Disconnect,
    "when i leave" to ReminderTrigger.Disconnect,
    "when leaving" to ReminderTrigger.Disconnect,
    "on leaving" to ReminderTrigger.Disconnect,
    "on exit" to ReminderTrigger.Disconnect,
    "cuando entre" to ReminderTrigger.Connect,
    "al entrar" to ReminderTrigger.Connect,
    "quan entri" to ReminderTrigger.Connect,
    "en entrar" to ReminderTrigger.Connect,
    "when i enter" to ReminderTrigger.Connect,
    "when entering" to ReminderTrigger.Connect,
    "on entering" to ReminderTrigger.Connect,
    "on entry" to ReminderTrigger.Connect,
).map { (text, trigger) -> Phrase(text) to trigger }
