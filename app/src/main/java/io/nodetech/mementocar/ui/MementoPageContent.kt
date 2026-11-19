// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Preserve each destination's scroll, but never reuse a closed editor's draft or scroll. */
@Composable
internal fun MementoPageContent(
    page: AppPage,
    editingReminderId: Long?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.(AppPage) -> Unit,
) {
    val pageStates = rememberSaveableStateHolder()
    if (page == AppPage.NewReminder) {
        key(editingReminderId) {
            PageList(modifier, contentPadding) { content(page) }
        }
    } else {
        pageStates.SaveableStateProvider(page.name) {
            PageList(modifier, contentPadding) { content(page) }
        }
    }
}

@Composable
private fun PageList(
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}
