// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.platform

import android.net.Uri
import androidx.core.net.toUri

internal fun reminderIdentity(kind: String, eventId: Long = 0, itemId: Long = -1): String =
    "mementocar://reminders/$kind/$eventId/$itemId"

internal fun reminderUri(kind: String, eventId: Long = 0, itemId: Long = -1): Uri =
    reminderIdentity(kind, eventId, itemId).toUri()
