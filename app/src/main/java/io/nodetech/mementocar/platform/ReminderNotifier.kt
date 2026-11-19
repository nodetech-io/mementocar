// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.nodetech.mementocar.MainActivity
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.DataLimits
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.platform.NotificationBlockReason.AppNotificationsDisabled
import io.nodetech.mementocar.platform.NotificationBlockReason.ChannelDisabled
import io.nodetech.mementocar.platform.NotificationBlockReason.PermissionDenied
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmStore: PendingReminderAlarmStore,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("reminder_notifications", Context.MODE_PRIVATE)
    private val preferencesLock = Any()

    fun ensureChannels() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationReadiness(): NotificationReadiness {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationReadiness(canPost = false, reason = PermissionDenied)
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationReadiness(canPost = false, reason = AppNotificationsDisabled)
        }
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_REMINDERS)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            return NotificationReadiness(canPost = false, reason = ChannelDisabled)
        }
        return NotificationReadiness(canPost = true)
    }

    fun showReminderPreview(
        vehicleName: String,
        trigger: ReminderTrigger,
        reminderItems: List<ReminderNotificationItem>,
    ): Boolean {
        if (!notificationReadiness().canPost) return false
        val items = reminderItems
            .map { it.copy(label = it.label.trim().take(DataLimits.ReminderLabelMaxChars)) }
            .filter { it.label.isNotEmpty() }
        if (items.isEmpty()) return false
        val contentIntent = PendingIntent.getActivity(
            context,
            trigger.previewNotificationId(),
            Intent(context, MainActivity::class.java).apply {
                data = reminderUri("preview", trigger.ordinal.toLong())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contextLine = context.getString(R.string.notification_preview_context, trigger.detailLine(context, vehicleName))
        val title = if (items.size == 1) {
            context.getString(R.string.notification_preview_single_title, items.first().label)
        } else {
            context.resources.getQuantityString(R.plurals.notification_preview_multiple_title, items.size, items.size)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(ContextCompat.getColor(context, R.color.mementocar_blue))
            .setContentTitle(title)
            .setContentText(contextLine)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (items.size == 1) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(context.getString(R.string.notification_preview_big_text, contextLine)),
            )
        } else {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(contextLine)
            items.take(MAX_SUMMARY_LINES).forEach { item ->
                style.addLine(item.label)
            }
            if (items.size > MAX_SUMMARY_LINES) {
                val hiddenCount = items.size - MAX_SUMMARY_LINES
                style.addLine(
                    context.resources.getQuantityString(
                        R.plurals.notification_more_items,
                        hiddenCount,
                        hiddenCount,
                    ),
                )
            }
            builder.setStyle(style)
        }
        return notify(reminderIdentity("preview", trigger.ordinal.toLong()), builder)
    }

    fun showReminder(
        eventId: Long,
        vehicleName: String,
        trigger: ReminderTrigger,
        reminderItems: List<ReminderNotificationItem>,
    ): Boolean {
        if (!canPostNotifications()) return false
        val completedIds = completedReminderIds(eventId)
        val items = reminderItems
            .map { it.copy(label = it.label.trim().take(DataLimits.ReminderLabelMaxChars)) }
            .filter { it.label.isNotEmpty() && it.id !in completedIds }
        if (items.isEmpty()) return false
        val shownItems = items.mapNotNull { item ->
            val shown = showReminderItem(
                eventId = eventId,
                vehicleName = vehicleName,
                trigger = trigger,
                item = item,
                totalCount = items.size,
            )
            item.takeIf { shown }
        }
        if (shownItems.isEmpty()) return false
        if (items.size > 1) {
            showReminderSummary(
                eventId = eventId,
                vehicleName = vehicleName,
                trigger = trigger,
                items = items,
            )
        }
        saveShownReminderIds(eventId, shownItems.map { it.id })
        return true
    }

    private fun showReminderItem(
        eventId: Long,
        vehicleName: String,
        trigger: ReminderTrigger,
        item: ReminderNotificationItem,
        totalCount: Int,
    ): Boolean {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                data = reminderUri("open", eventId, item.id)
                putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            0,
            SnoozeReceiver.intent(context, eventId, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val completeIntent = PendingIntent.getBroadcast(
            context,
            0,
            CompleteReminderReceiver.intent(context, eventId, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contextLine = trigger.detailLine(context, vehicleName)
        val expandedText = if (totalCount == 1) {
            contextLine
        } else {
            context.resources.getQuantityString(
                R.plurals.notification_pending_context,
                totalCount,
                contextLine,
                totalCount,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(ContextCompat.getColor(context, R.color.mementocar_blue))
            .setColorized(false)
            .setContentTitle(item.label)
            .setContentText(contextLine)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(contentIntent)
            .setGroup(groupKey(eventId))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(item.label)
                    .bigText(expandedText),
            )
            .addAction(0, context.getString(R.string.complete), completeIntent)
            .addAction(0, context.getString(R.string.notification_snooze_10_min), snoozeIntent)

        return notify(reminderIdentity("item", eventId, item.id), builder)
    }

    private fun showReminderSummary(
        eventId: Long,
        vehicleName: String,
        trigger: ReminderTrigger,
        items: List<ReminderNotificationItem>,
    ): Boolean {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                data = reminderUri("summary", eventId)
                putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(
                context.resources.getQuantityString(R.plurals.notification_pending_reminders_title, items.size, items.size),
            )
            .setSummaryText(trigger.detailLine(context, vehicleName))
        items.take(MAX_SUMMARY_LINES).forEach { item ->
            style.addLine(item.label)
        }
        if (items.size > MAX_SUMMARY_LINES) {
            val hiddenCount = items.size - MAX_SUMMARY_LINES
            style.addLine(
                context.resources.getQuantityString(
                    R.plurals.notification_more_items,
                    hiddenCount,
                    hiddenCount,
                ),
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setColor(ContextCompat.getColor(context, R.color.mementocar_blue))
            .setColorized(false)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.notification_pending_reminders_title, items.size, items.size),
            )
            .setContentText(trigger.detailLine(context, vehicleName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(contentIntent)
            .setGroup(groupKey(eventId))
            .setGroupSummary(true)
            .setStyle(style)

        return notify(reminderIdentity("summary", eventId), builder)
    }

    fun updateSnoozed(eventId: Long) {
        cancel(reminderIdentity("summary", eventId))
        shownReminderIds(eventId).forEach { itemId ->
            cancel(reminderIdentity("item", eventId, itemId))
        }
    }

    fun updateSnoozed(eventId: Long, itemId: Long) {
        cancel(reminderIdentity("item", eventId, itemId))
        refreshSummaryAfterItemRemoval(eventId, itemId)
    }

    fun dismissReminder(eventId: Long) {
        alarmStore.removeDelayed(eventId)
        alarmStore.removeSnoozed(eventId)
        alarmStore.removeCriticalRepeat(eventId)
        cancel(reminderIdentity("summary", eventId))
        shownReminderIds(eventId).forEach { itemId ->
            cancel(reminderIdentity("item", eventId, itemId))
        }
        synchronized(preferencesLock) {
            preferences.edit {
                remove(completedKey(eventId))
                remove(shownKey(eventId))
                remove("suppressed_critical_$eventId")
            }
        }
    }

    fun dismissReminder(eventId: Long, itemId: Long) {
        alarmStore.removeSnoozed(eventId, itemId)
        cancel(reminderIdentity("item", eventId, itemId))
        refreshSummaryAfterItemRemoval(eventId, itemId)
    }

    private fun refreshSummaryAfterItemRemoval(eventId: Long, itemId: Long) {
        val active = try {
            context.getSystemService(NotificationManager::class.java).activeNotifications.toList()
        } catch (_: SecurityException) {
            return
        }
        val summaryTag = reminderIdentity("summary", eventId)
        val summary = active.firstOrNull { it.tag == summaryTag } ?: return
        val removedTag = reminderIdentity("item", eventId, itemId)
        val remaining = active.filter {
            it.notification.group == groupKey(eventId) &&
                it.tag != removedTag &&
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }
        if (remaining.isEmpty()) {
            cancel(summaryTag)
            return
        }
        // Cancelling a group summary also cancels its children on Android. Keep
        // the summary (even for one child) and remove only the deleted/snoozed text.
        val title = context.resources.getQuantityString(
            R.plurals.notification_pending_reminders_title, remaining.size, remaining.size,
        )
        val contextLine = summary.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(contextLine)
        remaining.take(MAX_SUMMARY_LINES).forEach {
            style.addLine(it.notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        }
        if (remaining.size > MAX_SUMMARY_LINES) {
            val hiddenCount = remaining.size - MAX_SUMMARY_LINES
            style.addLine(context.resources.getQuantityString(
                R.plurals.notification_more_items, hiddenCount, hiddenCount,
            ))
        }
        val builder = NotificationCompat.Builder(context, summary.notification)
            .setContentTitle(title)
            .setStyle(style)
            .setOnlyAlertOnce(true)
        notify(summaryTag, builder)
    }

    @SuppressLint("UseKtx")
    fun dismissAll(): Boolean {
        val notificationsCleared = try {
            NotificationManagerCompat.from(context).cancelAll()
            true
        } catch (_: SecurityException) {
            // Some OEM builds enforce notification permissions on cancellation too.
            false
        }
        val stateCleared = synchronized(preferencesLock) {
            preferences.edit().clear().commit()
        }
        return notificationsCleared && stateCleared
    }

    fun suppressCriticalRepeat(eventId: Long, itemId: Long) {
        synchronized(preferencesLock) {
            val ids = suppressedCriticalIds(eventId) + itemId
            preferences.edit(commit = true) {
                putStringSet("suppressed_critical_$eventId", ids.map { it.toString() }.toSet())
            }
        }
    }

    fun suppressedCriticalIds(eventId: Long): Set<Long> = synchronized(preferencesLock) {
        preferences.getStringSet("suppressed_critical_$eventId", emptySet())
            .orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun markReminderCompleted(eventId: Long, itemId: Long) {
        synchronized(preferencesLock) {
            val completed = completedReminderIdsLocked(eventId) + itemId
            preferences.edit {
                putStringSet(completedKey(eventId), completed.map { it.toString() }.toSet())
            }
        }
    }

    fun completedReminderIds(eventId: Long): Set<Long> {
        return synchronized(preferencesLock) {
            completedReminderIdsLocked(eventId)
        }
    }

    private fun saveShownReminderIds(eventId: Long, itemIds: List<Long>) {
        synchronized(preferencesLock) {
            val shown = shownReminderIdsLocked(eventId) + itemIds
            preferences.edit {
                putStringSet(shownKey(eventId), shown.map { it.toString() }.toSet())
            }
        }
    }

    fun shownReminderIds(eventId: Long): Set<Long> {
        return synchronized(preferencesLock) {
            shownReminderIdsLocked(eventId)
        }
    }

    private fun completedReminderIdsLocked(eventId: Long): Set<Long> {
        return preferences.getStringSet(completedKey(eventId), emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    private fun shownReminderIdsLocked(eventId: Long): Set<Long> {
        return preferences.getStringSet(shownKey(eventId), emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    private fun canPostNotifications(): Boolean {
        return notificationReadiness().canPost
    }

    private fun notify(tag: String, builder: NotificationCompat.Builder): Boolean {
        if (!canPostNotifications()) return false
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, builder.build())
            return true
        } catch (_: SecurityException) {
            // Permission can change between the check and the platform call.
            return false
        }
    }

    private fun cancel(tag: String) {
        try {
            NotificationManagerCompat.from(context).cancel(tag, 0)
        } catch (_: SecurityException) {
            // Some OEM builds enforce notification permissions on cancellation too.
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "mementocar_reminders"
        private const val MAX_SUMMARY_LINES = 5
    }
}

data class ReminderNotificationItem(
    val id: Long,
    val label: String,
)

private fun ReminderTrigger.previewNotificationId(): Int {
    return 71_100 + ordinal
}

private fun ReminderTrigger.detailLine(context: Context, vehicleName: String): String {
    return when (this) {
        ReminderTrigger.Connect -> context.getString(R.string.notification_trigger_enter_vehicle, vehicleName)
        ReminderTrigger.Disconnect -> context.getString(R.string.notification_trigger_leave_vehicle, vehicleName)
    }
}

private fun groupKey(eventId: Long): String {
    return "mementocar_reminders_$eventId"
}

private fun completedKey(eventId: Long): String {
    return "completed_reminders_$eventId"
}

private fun shownKey(eventId: Long): String {
    return "shown_reminders_$eventId"
}
