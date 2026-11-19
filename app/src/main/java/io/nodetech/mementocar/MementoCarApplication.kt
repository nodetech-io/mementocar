// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar

import android.app.Application
import io.nodetech.mementocar.platform.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MementoCarApplication : Application() {
    @Inject lateinit var reminderNotifier: ReminderNotifier

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannels()
    }
}
