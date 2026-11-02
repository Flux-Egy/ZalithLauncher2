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

/**
 * Builds one key per element that is guaranteed to be unique within the list.
 *
 * The natural identity from [idOf] is used as-is; if it repeats (two elements with the
 * same identity), later occurrences get "#1", "#2", ... appended, skipping any candidate
 * already taken. Lazy lists crash on duplicate keys, so this makes every key safe while
 * keeping the natural key for the common, duplicate-free case.
 *
 * Keys are Strings, so they are Bundle-saveable as Compose requires on Android.
 */
fun <T> List<T>.uniqueKeys(idOf: (T) -> String): List<String> {
    val used = HashSet<String>(size * 2)
    return map { element ->
        val id = idOf(element)
        var candidate = id
        var n = 1
        while (!used.add(candidate)) {
            candidate = "$id#$n"
            n++
        }
        candidate
    }
}

/**
 * Identity of an object instance for use as a lazy-list key, for elements whose visible
 * content is edited in place (a content-derived key would change while the user types and
 * recreate the row). Combine with [uniqueKeys], since identity hashes can collide.
 */
fun identityKey(element: Any?): String = "@" + System.identityHashCode(element).toString(16)
