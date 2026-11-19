// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import io.nodetech.mementocar.data.ReminderTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionDiagnosticEvent(
    val vehicleId: Long,
    val trigger: ReminderTrigger,
    val result: ConnectionDiagnosticResult,
    val occurredAtMillis: Long,
)

enum class ConnectionDiagnosticResult {
    ReminderShown,
    ReminderScheduled,
    NoDueReminders,
    DuplicateIgnored,
    NotificationsBlocked,
    TestShown,
    TestBlocked,
}

@Singleton
class ConnectionDiagnosticsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("connection_diagnostics", Context.MODE_PRIVATE)
    private val diagnosticsLock = Any()
    private val _diagnostics = MutableStateFlow(loadDiagnostics())

    val diagnostics: StateFlow<Map<Long, ConnectionDiagnosticsState>> = _diagnostics.asStateFlow()

    fun remove(vehicleId: Long) {
        synchronized(diagnosticsLock) {
            preferences.edit(commit = true) {
                remove(key(vehicleId))
            }
            _diagnostics.value = _diagnostics.value - vehicleId
        }
    }

    @SuppressLint("UseKtx")
    fun clear(): Boolean {
        return synchronized(diagnosticsLock) {
            val persisted = preferences.edit().clear().commit()
            _diagnostics.value = emptyMap()
            persisted
        }
    }

    fun record(
        vehicleId: Long,
        trigger: ReminderTrigger,
        result: ConnectionDiagnosticResult,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ) {
        val event = ConnectionDiagnosticEvent(
            vehicleId = vehicleId,
            trigger = trigger,
            result = result,
            occurredAtMillis = occurredAtMillis,
        )
        synchronized(diagnosticsLock) {
            val currentDiagnostics = _diagnostics.value
            val state = (currentDiagnostics[vehicleId] ?: ConnectionDiagnosticsState()).withEvent(event)
            preferences.edit {
                putString(key(vehicleId), state.toPreferenceValue())
            }
            _diagnostics.value = currentDiagnostics + (vehicleId to state)
        }
    }

    private fun loadDiagnostics(): Map<Long, ConnectionDiagnosticsState> {
        return preferences.all.mapNotNull { (key, value) ->
            val vehicleId = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@mapNotNull null
            val diagnostics = (value as? String)?.decodeConnectionDiagnosticsState(vehicleId) ?: return@mapNotNull null
            vehicleId to diagnostics
        }.toMap()
    }

    private fun key(vehicleId: Long): String = "$KEY_PREFIX$vehicleId"

    private companion object {
        const val KEY_PREFIX = "vehicle_"
    }
}

internal fun ConnectionDiagnosticsState.toPreferenceValue(): String {
    return buildString {
        append("{\"version\":\"")
        append(DIAGNOSTICS_FORMAT_VERSION)
        append("\",\"latestEvent\":")
        append(latestEvent.toJsonValue())
        append(",\"latestManualTest\":")
        append(latestManualTest.toJsonValue())
        append(",\"latestAutomaticSuccess\":")
        append(latestAutomaticSuccess.toJsonValue())
        append("}")
    }
}

internal fun String.decodeConnectionDiagnosticsState(vehicleId: Long): ConnectionDiagnosticsState? {
    val fields = parseJsonObjectFields(trim()) ?: return null
    if (fields["version"]?.asString != DIAGNOSTICS_FORMAT_VERSION) return null
    return ConnectionDiagnosticsState(
        latestEvent = fields["latestEvent"]?.asObject?.toDiagnosticEvent(vehicleId),
        latestManualTest = fields["latestManualTest"]?.asObject?.toDiagnosticEvent(vehicleId),
        latestAutomaticSuccess = fields["latestAutomaticSuccess"]?.asObject?.toDiagnosticEvent(vehicleId),
    )
}

private fun ConnectionDiagnosticEvent?.toJsonValue(): String {
    if (this == null) return "null"
    return buildString {
        append("{\"trigger\":")
        append(trigger.name.jsonString())
        append(",\"result\":")
        append(result.name.jsonString())
        append(",\"occurredAtMillis\":")
        append(occurredAtMillis)
        append("}")
    }
}

private fun Map<String, JsonValue>.toDiagnosticEvent(vehicleId: Long): ConnectionDiagnosticEvent? {
    val triggerName = this["trigger"]?.asString ?: return null
    val resultName = this["result"]?.asString ?: return null
    val occurredAtMillis = this["occurredAtMillis"]?.asLong ?: return null
    val trigger = ReminderTrigger.entries.firstOrNull { it.name == triggerName } ?: return null
    val result = ConnectionDiagnosticResult.entries.firstOrNull { it.name == resultName } ?: return null
    return ConnectionDiagnosticEvent(
        vehicleId = vehicleId,
        trigger = trigger,
        result = result,
        occurredAtMillis = occurredAtMillis,
    )
}

private sealed interface JsonValue {
    val asString: String? get() = null
    val asLong: Long? get() = null
    val asObject: Map<String, JsonValue>? get() = null
    val asArray: List<JsonValue>? get() = null
}

private data class JsonStringValue(override val asString: String) : JsonValue
private data class JsonLongValue(override val asLong: Long) : JsonValue
private data class JsonObjectValue(override val asObject: Map<String, JsonValue>) : JsonValue
private data class JsonArrayValue(override val asArray: List<JsonValue>) : JsonValue
private data object JsonNullValue : JsonValue

private fun parseJsonObjectFields(value: String): Map<String, JsonValue>? {
    val parser = DiagnosticsJsonParser(value)
    return parser.parseObjectValue()?.also {
        if (!parser.isConsumed()) return null
    }
}

private class DiagnosticsJsonParser(private val source: String) {
    private var index = 0

    fun parseObjectValue(): Map<String, JsonValue>? {
        skipWhitespace()
        if (!consume('{')) return null
        val fields = mutableMapOf<String, JsonValue>()
        skipWhitespace()
        if (consume('}')) return fields
        while (true) {
            val key = parseString() ?: return null
            skipWhitespace()
            if (!consume(':')) return null
            val value = parseValue() ?: return null
            fields[key] = value
            skipWhitespace()
            if (consume('}')) return fields
            if (!consume(',')) return null
        }
    }

    fun isConsumed(): Boolean {
        skipWhitespace()
        return index == source.length
    }

    private fun parseValue(): JsonValue? {
        skipWhitespace()
        return when (source.getOrNull(index)) {
            '"' -> parseString()?.let(::JsonStringValue)
            '{' -> parseObjectValue()?.let(::JsonObjectValue)
            '[' -> parseArray()?.let(::JsonArrayValue)
            'n' -> if (consumeLiteral("null")) JsonNullValue else null
            '-', in '0'..'9' -> parseLong()?.let(::JsonLongValue)
            else -> null
        }
    }

    private fun parseArray(): List<JsonValue>? {
        if (!consume('[')) return null
        val values = mutableListOf<JsonValue>()
        skipWhitespace()
        if (consume(']')) return values
        while (true) {
            values += parseValue() ?: return null
            skipWhitespace()
            if (consume(']')) return values
            if (!consume(',')) return null
        }
    }

    private fun parseString(): String? {
        if (!consume('"')) return null
        val result = StringBuilder()
        while (index < source.length) {
            when (val char = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    val escaped = source.getOrNull(index++) ?: return null
                    result.append(
                        when (escaped) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> return null
                        },
                    )
                }
                else -> result.append(char)
            }
        }
        return null
    }

    private fun parseLong(): Long? {
        val start = index
        if (source.getOrNull(index) == '-') index++
        while (source.getOrNull(index)?.isDigit() == true) index++
        return source.substring(start, index).toLongOrNull()
    }

    private fun consume(expected: Char): Boolean {
        skipWhitespace()
        if (source.getOrNull(index) != expected) return false
        index++
        return true
    }

    private fun consumeLiteral(expected: String): Boolean {
        if (!source.startsWith(expected, index)) return false
        index += expected.length
        return true
    }

    private fun skipWhitespace() {
        while (source.getOrNull(index)?.isWhitespace() == true) index++
    }
}

private fun String.jsonString(): String {
    return buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

private const val DIAGNOSTICS_FORMAT_VERSION = "v3"
