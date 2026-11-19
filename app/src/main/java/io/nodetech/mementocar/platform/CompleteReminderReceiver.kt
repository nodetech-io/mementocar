// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nodetech.mementocar.data.MementoCarRepository
import io.nodetech.mementocar.data.ReminderEventItemCompletion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CompleteReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MementoCarRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var operationCoordinator: ReminderOperationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                operationCoordinator.runExclusive {
                    if (itemId > 0L) {
                        completeReminderItem(eventId, itemId)
                    } else {
                        repository.completeReminderEvent(
                            eventId = eventId,
                            completedItemIds = notifier.shownReminderIds(eventId),
                        )
                        notifier.dismissReminder(eventId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun completeReminderItem(eventId: Long, itemId: Long) {
        when (repository.completeReminderEventItem(eventId, itemId)) {
            ReminderEventItemCompletion.ItemCompleted -> {
                notifier.dismissReminder(eventId, itemId)
                return
            }
            ReminderEventItemCompletion.EventCompleted -> {
                notifier.dismissReminder(eventId)
                return
            }
            ReminderEventItemCompletion.NoPersistedItem -> Unit
        }

        notifier.markReminderCompleted(eventId, itemId)
        repository.deactivateCompletedOneTimeChecklistItems(listOf(itemId))
        notifier.dismissReminder(eventId, itemId)

        repository.reminderEventById(eventId) ?: return
        val checklist = notifier.shownReminderIds(eventId)
        val completedIds = notifier.completedReminderIds(eventId)
        if (checklist.isNotEmpty() && checklist.all { it in completedIds }) {
            repository.completeReminderEvent(eventId, completedIds)
            notifier.dismissReminder(eventId)
        }
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_ITEM_ID = "item_id"

        fun intent(context: Context, eventId: Long, itemId: Long = -1): Intent {
            return Intent(context, CompleteReminderReceiver::class.java)
                .setData(reminderUri("complete", eventId, itemId))
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_ITEM_ID, itemId)
        }
    }
}
