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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UniqueKeysTest {
    @Test
    fun naturalKeysAreKeptWhenUnique() {
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").uniqueKeys { it })
    }

    @Test
    fun duplicatesGetSuffixes() {
        assertEquals(listOf("a", "a#1", "b", "a#2"), listOf("a", "a", "b", "a").uniqueKeys { it })
    }

    @Test
    fun suffixNeverCollidesWithARealId() {
        // "a#1" already exists as a real id, so the duplicate "a" must skip it
        val keys = listOf("a", "a#1", "a").uniqueKeys { it }
        assertEquals(listOf("a", "a#1", "a#2"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun alwaysUniqueEvenForAdversarialInput() {
        val input = listOf("x", "x#1", "x", "x#1", "x#2", "x", "", "", "#1")
        val keys = input.uniqueKeys { it }
        assertEquals(input.size, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun appendingKeepsEarlierKeysStable() {
        val before = listOf("log", "log", "other").uniqueKeys { it }
        val after = listOf("log", "log", "other", "log").uniqueKeys { it }
        assertEquals(before, after.take(before.size))
    }

    @Test
    fun emptyList() {
        assertEquals(emptyList<String>(), emptyList<String>().uniqueKeys { it })
    }

    @Test
    fun identityKeyDiffersForEqualButDistinctObjects() {
        data class Row(val text: String)
        val a = Row("same")
        val b = Row("same")
        assertEquals(a, b)
        // identity hashes can in theory collide; uniqueKeys guarantees uniqueness regardless
        val keys = listOf(a, b, a).uniqueKeys(::identityKey)
        assertEquals(3, keys.toSet().size)
        assertEquals(identityKey(a), keys[0])
        assertNotEquals(keys[0], keys[1])
    }
}
