// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The debug build has its own application ID, so it ships a copy of the shortcuts that must match the main one. */
class ShortcutsDebugCopyTest {
    @Test
    fun debugShortcutsDifferOnlyInTargetPackage() {
        val main = File("src/main/res/xml/shortcuts.xml").readText()
        val debug = File("src/debug/res/xml/shortcuts.xml").readLines()
            .filterNot { it.trimStart().startsWith("<!--") }
            .joinToString("\n", postfix = "\n")
            .replace(
                "android:targetPackage=\"io.nodetech.mementocar.debug\"",
                "android:targetPackage=\"io.nodetech.mementocar\"",
            )
        assertEquals(main, debug)
    }
}
