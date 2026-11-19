// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.nodetech.mementocar.MainActivity
import io.nodetech.mementocar.R

/** Quick Settings action tile that starts the hands-free voice reminder flow. */
class AddReminderTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_add_reminder_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.tile_add_reminder_subtitle)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::launchAddReminder) else launchAddReminder()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchAddReminder() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_VOICE_REMINDER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
