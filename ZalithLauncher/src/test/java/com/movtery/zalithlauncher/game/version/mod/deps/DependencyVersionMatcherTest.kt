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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DependencyVersionMatcherTest {
    /** Real Modrinth version names for Sodium on 1.21.1 (newest first), with their game versions */
    private val sodium = listOf(
        "mc1.21.1-0.8.13-fabric" to listOf("1.21.1"),
        "mc1.21.1-0.8.13-beta.2-fabric" to listOf("1.21.1"),
        "mc1.21.1-0.8.12-fabric" to listOf("1.21.1"),
        "mc1.21.1-0.8.12-alpha.4-fabric" to listOf("1.21.1"),
        "mc1.21.1-0.6.13-fabric" to listOf("1.21", "1.21.1"),
        "mc1.21.1-0.6.9-fabric" to listOf("1.21", "1.21.1")
    )

    @Test
    fun extractsModVersionFromPlatformNames() {
        fun v(name: String, games: List<String>) = DependencyVersionMatcher.extractVersion(name, games)?.toString()
        assertEquals("0.6.13", v("mc1.21.1-0.6.13-fabric", listOf("1.21.1")))
        assertEquals("0.8.13-beta.2", v("mc1.21.1-0.8.13-beta.2-fabric", listOf("1.21.1")))
        assertEquals("1.8.8", v("1.8.8+1.21.1-fabric", listOf("1.21", "1.21.1")))
        assertEquals("0.116.7", v("0.116.7+1.21.1", listOf("1.21.1")))
        assertEquals("5.0.4", v("v5.0.4", emptyList()))
        assertNull(v("Iris Shaders", emptyList()))
    }

    @Test
    fun irisNeedsSodium06PicksNewest06() {
        val range = FabricVersionPredicate.parseAny(listOf("0.6.x"))!!
        val picked = DependencyVersionMatcher.filter(sodium, range, { it.first }, { it.second })
        assertEquals("mc1.21.1-0.6.13-fabric", picked.first().first)
        assertEquals(2, picked.size)
    }

    @Test
    fun unparseableCandidatesAreKept() {
        val range = FabricVersionPredicate.parseAny(listOf("0.6.x"))!!
        val picked = DependencyVersionMatcher.filter(listOf("Sodium build" to emptyList<String>()), range, { it.first }, { it.second })
        assertEquals(1, picked.size)
    }

    @Test
    fun readsRangesFromJarAndMatchesSlugOrTitle() {
        val jar = Files.createTempFile("iris", ".jar").toFile()
        try {
            ZipOutputStream(jar.outputStream()).use { z ->
                z.putNextEntry(ZipEntry("fabric.mod.json"))
                z.write("""{"id":"iris","version":"1.8.8+mc1.21.1","depends":{"fabricloader":">=0.12.3","minecraft":["1.21.x"],"sodium":["0.6.x"],"fabric-api":"*"}}""".toByteArray())
                z.closeEntry()
            }
            val ranges = DependencyVersionMatcher.requiredRanges(jar)
            // unversioned ("*") relations are omitted
            assertTrue("fabric-api" !in ranges)
            assertEquals("0.6.x", DependencyVersionMatcher.rangeFor(ranges, slug = "sodium", title = "Sodium")!!.second)
            assertEquals("0.6.x", DependencyVersionMatcher.rangeFor(ranges, slug = null, title = "Sodium")!!.second)
            assertNull(DependencyVersionMatcher.rangeFor(ranges, slug = "lithium", title = "Lithium"))
        } finally {
            jar.delete()
        }
        assertEquals(emptyMap<String, Any>(), DependencyVersionMatcher.requiredRanges(File("/nonexistent.jar")))
    }
}
