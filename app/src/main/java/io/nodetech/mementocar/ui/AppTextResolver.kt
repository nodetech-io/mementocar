// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTextResolver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val resourcesDelegate = context.resources
    val resources = QuantityTextResolver(resourcesDelegate)

    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return resourcesDelegate.getString(resId, *formatArgs)
    }
}

class QuantityTextResolver internal constructor(
    private val resources: Resources,
) {
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return resources.getQuantityString(resId, quantity, *formatArgs)
    }
}
