/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.components.lazy

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable

/**
 * Like `items(list) { ... }`, but with a stable key derived from [id] that is guaranteed
 * unique (see [uniqueKeys]) and an optional [contentType] for item reuse.
 *
 * Prefer a real identity (file path, project id, uuid, primary key). For rows that are
 * edited in place use [identityKey].
 */
inline fun <T> LazyListScope.uniqueItems(
    list: List<T>,
    crossinline id: (T) -> String,
    crossinline contentType: (T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
) {
    val keys = list.uniqueKeys { id(it) }
    items(
        count = list.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(list[index]) }
    ) { index ->
        itemContent(list[index])
    }
}

/**
 * Grid variant of [uniqueItems].
 */
inline fun <T> LazyGridScope.uniqueItems(
    list: List<T>,
    crossinline id: (T) -> String,
    crossinline contentType: (T) -> Any? = { null },
    crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit
) {
    val keys = list.uniqueKeys { id(it) }
    items(
        count = list.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(list[index]) }
    ) { index ->
        itemContent(list[index])
    }
}
