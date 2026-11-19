// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.data

import java.security.MessageDigest

fun stableHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
