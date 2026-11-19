// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationDelaySettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("notification_delay_settings", Context.MODE_PRIVATE)
    private val _bluetoothEventDelayMinutes = MutableStateFlow(
        preferences.getInt(KEY_BLUETOOTH_EVENT_DELAY_MINUTES, DEFAULT_DELAY_MINUTES),
    )

    val bluetoothEventDelayMinutes: StateFlow<Int> = _bluetoothEventDelayMinutes.asStateFlow()

    fun currentBluetoothEventDelayMinutes(): Int = bluetoothEventDelayMinutes.value

    fun setBluetoothEventDelayMinutes(minutes: Int) {
        val cleanMinutes = minutes.coerceIn(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES)
        preferences.edit {
            putInt(KEY_BLUETOOTH_EVENT_DELAY_MINUTES, cleanMinutes)
        }
        _bluetoothEventDelayMinutes.value = cleanMinutes
    }

    @SuppressLint("UseKtx")
    fun restoreBluetoothEventDelayMinutes(minutes: Int): Boolean {
        val cleanMinutes = minutes.coerceIn(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES)
        val persisted = preferences.edit()
            .putInt(KEY_BLUETOOTH_EVENT_DELAY_MINUTES, cleanMinutes)
            .commit()
        if (persisted) {
            _bluetoothEventDelayMinutes.value = cleanMinutes
        }
        return persisted
    }

    companion object {
        const val DEFAULT_DELAY_MINUTES = 1
        const val MIN_DELAY_MINUTES = 0
        const val MAX_DELAY_MINUTES = 30
        private const val KEY_BLUETOOTH_EVENT_DELAY_MINUTES = "bluetooth_event_delay_minutes"
    }
}
