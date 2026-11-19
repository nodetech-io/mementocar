// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nodetech.mementocar.ui.MementoCarApp
import io.nodetech.mementocar.ui.MementoCarViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MementoCarViewModel by viewModels()
    private var requestedReminderEventId by mutableStateOf<Long?>(null)
    private var requestedVoiceReminderStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            requestedReminderEventId = intent.reminderEventId()
            requestedVoiceReminderStart = intent.startsVoiceReminder()
        }
        setContent {
            MementoCarApp(
                viewModel = viewModel,
                requestedReminderEventId = requestedReminderEventId,
                onReminderEventRequestHandled = {
                    requestedReminderEventId = null
                    intent.removeExtra(EXTRA_EVENT_ID)
                },
                requestedVoiceReminderStart = requestedVoiceReminderStart,
                onVoiceReminderStartHandled = {
                    requestedVoiceReminderStart = false
                    if (intent.action == ACTION_ADD_VOICE_REMINDER) {
                        intent.action = Intent.ACTION_MAIN
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedReminderEventId = intent.reminderEventId()
        requestedVoiceReminderStart = intent.startsVoiceReminder()
    }

    companion object {
        const val ACTION_ADD_VOICE_REMINDER = "io.nodetech.mementocar.ADD_VOICE_REMINDER"
        const val EXTRA_EVENT_ID = "event_id"
    }
}

private fun Intent.reminderEventId(): Long? {
    return getLongExtra(MainActivity.EXTRA_EVENT_ID, -1L).takeIf { it > 0L }
}

private fun Intent.startsVoiceReminder(): Boolean {
    return action == MainActivity.ACTION_ADD_VOICE_REMINDER
}
