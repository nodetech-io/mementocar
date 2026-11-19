// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.domain

import io.nodetech.mementocar.data.ReminderEventEntity
import io.nodetech.mementocar.data.ReminderStatus

/** An open alert item that a spoken "mark <label>" command may refer to. */
data class VoiceItemCandidate(
    val eventId: Long,
    val itemId: Long,
    val label: String,
    val eventTriggeredAtMillis: Long,
)

sealed interface VoiceItemMatch {
    data class Single(val candidate: VoiceItemCandidate) : VoiceItemMatch
    data class Ambiguous(val candidates: List<VoiceItemCandidate>) : VoiceItemMatch
    data object NoMatch : VoiceItemMatch
}

private val ContractionPrefix = Regex("^[ldn]'(.+)$")
private val NonWordSeparators = Regex("[^\\p{L}\\p{N}'·]+")
private const val MinPrefixLength = 4

private val StopwordKeys: Set<String> = listOf(
    // es
    "el", "la", "los", "las", "un", "una", "unos", "unas", "mi", "mis", "de", "del", "al", "lo",
    // ca
    "els", "les", "uns", "unes", "meu", "meva", "meus", "meves", "dels", "als", "en", "na",
    // en
    "the", "a", "an", "my", "of",
).map(::matchKey).toSet()

/**
 * Folded content tokens of a label: lowercase, accents/middle dot/apostrophes dropped, leading l'/d'/n'
 * contractions stripped before folding ("l'ordinador" -> "ordinador") and stopwords removed.
 */
internal fun voiceItemTokens(text: String): List<String> =
    normalizeVoiceText(text)
        .split(NonWordSeparators)
        .filter { it.isNotEmpty() }
        .map { token -> ContractionPrefix.find(token)?.groupValues?.get(1) ?: token }
        .map(::matchKey)
        .filter { it.isNotEmpty() && it !in StopwordKeys }

/**
 * Light singular/plural stem for es/ca/en: drops a plural "-es"/"-s" and then a final vowel, so
 * "motxilla"/"motxilles", "ullera"/"ulleres" and "llave"/"llaves" share a stem. Short stems are not used.
 */
internal fun voiceItemStem(token: String): String {
    val singular = when {
        token.length > 5 && token.endsWith("es") -> token.dropLast(2)
        token.length > 4 && token.endsWith("s") -> token.dropLast(1)
        else -> token
    }
    return if (singular.length > MinPrefixLength && singular.last() in "aeo") singular.dropLast(1) else singular
}

private fun tokensAgree(spoken: String, candidate: String): Boolean {
    if (spoken == candidate) return true
    if (spoken.length < MinPrefixLength || candidate.length < MinPrefixLength) return false
    if (candidate.startsWith(spoken) || spoken.startsWith(candidate)) return true
    val spokenStem = voiceItemStem(spoken)
    return spokenStem.length >= MinPrefixLength && spokenStem == voiceItemStem(candidate)
}

/**
 * Resolves the spoken label of a MarkItem command against open alert items. Every spoken content token must
 * agree with a candidate token (equal, or one a prefix of the other when both have at least 4 letters). When
 * several candidates agree, those whose whole token set equals the spoken one win. Candidates sharing an itemId
 * (the same checklist item in several open events) collapse to the one from the most recent event.
 */
fun matchVoiceItem(spoken: String, candidates: List<VoiceItemCandidate>): VoiceItemMatch {
    val spokenTokens = voiceItemTokens(spoken)
    if (spokenTokens.isEmpty()) return VoiceItemMatch.NoMatch
    val spokenSet = spokenTokens.toSet()
    val tokenised = candidates
        .map { it to voiceItemTokens(it.label) }
        .filter { (_, tokens) -> tokens.isNotEmpty() }
        .filter { (_, tokens) -> spokenSet.all { s -> tokens.any { c -> tokensAgree(s, c) } } }
    if (tokenised.isEmpty()) return VoiceItemMatch.NoMatch
    val exact = tokenised.filter { (_, tokens) -> tokens.toSet() == spokenSet }
    val pool = (exact.ifEmpty { tokenised }).map { it.first }
    val unique = pool
        .sortedWith(compareByDescending<VoiceItemCandidate> { it.eventTriggeredAtMillis }.thenByDescending { it.eventId })
        .distinctBy { it.itemId }
    return if (unique.size == 1) VoiceItemMatch.Single(unique.first()) else VoiceItemMatch.Ambiguous(unique)
}

/** Most recent open (pending or snoozed) event of the vehicle, or null. Ties on time resolve to the newest id. */
fun latestOpenReminderEvent(events: List<ReminderEventEntity>, vehicleId: Long): ReminderEventEntity? =
    events
        .filter { it.vehicleId == vehicleId && (it.status == ReminderStatus.Pending || it.status == ReminderStatus.Snoozed) }
        .maxWithOrNull(compareBy<ReminderEventEntity> { it.disconnectedAtMillis }.thenBy { it.id })

/** Completing everything by voice needs an explicit, unambiguous yes: the best hypothesis confirms and none cancels. */
internal fun isVoiceCompleteAllConfirmed(hypotheses: List<String>): Boolean {
    val responses = hypotheses.map(::parseVoiceConfirmation)
    return responses.firstOrNull() == VoiceConfirmation.Confirm && VoiceConfirmation.Cancel !in responses
}

/** Event a spoken "snooze" applies to: the latest alert still ringing (Pending), else the latest snoozed one. */
fun voiceSnoozeTarget(events: List<ReminderEventEntity>, vehicleId: Long): ReminderEventEntity? =
    latestOpenReminderEvent(events.filter { it.status == ReminderStatus.Pending }, vehicleId)
        ?: latestOpenReminderEvent(events, vehicleId)
