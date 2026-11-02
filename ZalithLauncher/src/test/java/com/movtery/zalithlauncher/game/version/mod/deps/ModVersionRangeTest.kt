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

package com.movtery.zalithlauncher.game.version.mod.deps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModVersionRangeTest {
    private fun v(s: String) = ModVersion.parse(s)!!
    private fun fabric(s: String) = FabricVersionPredicate.parse(s)!!
    private fun maven(s: String) = MavenVersionRange.parse(s)!!

    @Test
    fun versionOrdering() {
        assertTrue(v("1.8.8+mc1.21.1") < v("1.8.13"))
        assertTrue(v("0.8.13+mc1.21.1") > v("0.6.13"))
        assertTrue(v("1.0.0-beta.2") < v("1.0.0"))
        assertTrue(v("1.0.0-beta.2") < v("1.0.0-beta.10"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-beta"))
        assertEquals(0, v("1.2").compareTo(v("1.2.0")))
        assertEquals(v("1.2"), v("1.2.0"))
        assertEquals(v("1.2").hashCode(), v("1.2.0").hashCode())
    }

    @Test
    fun unparseableVersionIsNull() {
        assertNull(ModVersion.parse("\${file.jarVersion}"))
        assertNull(ModVersion.parse("mc1.21.1-0.8.13-fabric"))
        assertNull(ModVersion.parse(""))
        assertNull(ModVersion.parse(null))
    }

    @Test
    fun fabricPredicates() {
        assertTrue(fabric("0.6.x").matches(v("0.6.13+mc1.21.1")))
        assertFalse(fabric("0.6.x").matches(v("0.8.13+mc1.21.1")))
        assertTrue(fabric("1.21.x").matches(v("1.21.1")))
        assertTrue(fabric("<1.8.13").matches(v("1.8.8+mc1.21.1")))
        assertFalse(fabric("<1.8.13").matches(v("1.8.14-beta.1+mc1.21.1")))
        assertTrue(fabric(">=0.16.0").matches(v("0.16.5")))
        assertFalse(fabric(">=0.16.0").matches(v("0.15.11")))
        assertTrue(fabric("~1.2.3").matches(v("1.2.9")))
        assertFalse(fabric("~1.2.3").matches(v("1.3.0")))
        assertTrue(fabric("^1.2.3").matches(v("1.9.0")))
        assertFalse(fabric("^1.2.3").matches(v("2.0.0")))
        assertTrue(fabric(">=1.0 <2.0").matches(v("1.5")))
        assertFalse(fabric(">=1.0 <2.0").matches(v("2.0")))
        assertTrue(fabric("1.2.3").matches(v("1.2.3")))
        assertFalse(fabric("1.2.3").matches(v("1.2.4")))
        assertTrue(fabric("*") === ModVersionRange.ANY)
    }

    @Test
    fun fabricAnyOf() {
        val r = FabricVersionPredicate.parseAny(listOf("1.21", "1.21.1"))!!
        assertTrue(r.matches(v("1.21.1")))
        assertFalse(r.matches(v("1.21.2")))
    }

    @Test
    fun unparseablePredicateIsNull() {
        assertNull(FabricVersionPredicate.parse(">=banana"))
    }

    @Test
    fun mavenRanges() {
        assertTrue(maven("[1.0,2.0)").matches(v("1.5")))
        assertFalse(maven("[1.0,2.0)").matches(v("2.0")))
        assertTrue(maven("[1.0,)").matches(v("99")))
        assertTrue(maven("(,1.0]").matches(v("1.0")))
        assertFalse(maven("(,1.0)").matches(v("1.0")))
        assertTrue(maven("[1.0]").matches(v("1.0.0")))
        assertFalse(maven("[1.0]").matches(v("1.0.1")))
        assertTrue(maven("[1,2),[3,4)").matches(v("3.5")))
        assertFalse(maven("[1,2),[3,4)").matches(v("2.5")))
        // bare version = soft requirement
        assertTrue(maven("1.0").matches(v("0.1")))
        assertNotNull(MavenVersionRange.parse("\${minecraft_version_range}"))
    }
}
