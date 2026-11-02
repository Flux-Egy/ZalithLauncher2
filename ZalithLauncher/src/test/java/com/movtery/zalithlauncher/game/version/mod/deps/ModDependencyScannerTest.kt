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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModDependencyScannerTest {
    private fun jar(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun String.b() = toByteArray()

    private fun fabricJson(id: String, depends: String = "{}", jars: List<String> = emptyList()) =
        """{"schemaVersion":1,"id":"$id","depends":$depends,"jars":[${jars.joinToString { """{"file":"$it"}""" }}]}"""

    @Test
    fun readsNestedJarInJarRecursively() {
        val deep = jar("fabric.mod.json" to fabricJson("deep-lib").b())
        val inner = jar(
            "fabric.mod.json" to fabricJson("inner-lib", jars = listOf("META-INF/jars/deep.jar")).b(),
            "META-INF/jars/deep.jar" to deep
        )
        val outer = jar(
            "fabric.mod.json" to fabricJson("outer", depends = """{"minecraft":"*","inner-lib":"*"}""", jars = listOf("META-INF/jars/inner.jar")).b(),
            "META-INF/jars/inner.jar" to inner
        )
        val file = Files.createTempFile("outer", ".jar").toFile().apply { writeBytes(outer); deleteOnExit() }

        val info = ModDependencyScanner.scan(file, DepLoader.FABRIC)!!
        assertEquals("outer", info.id)
        assertEquals(setOf("outer", "inner-lib", "deep-lib"), info.allProvidedIds())
        // the jar satisfies its own dependency through the bundled jar
        assertTrue(ModDependencyGraph.analyze(listOf(info), DepLoader.FABRIC).isEmpty())
    }

    @Test
    fun multiLoaderJarUsesTheInstanceLoadersDependencies() {
        val multi = jar(
            "fabric.mod.json" to fabricJson("multi", depends = """{"fabric-api":"*"}""").b(),
            "META-INF/neoforge.mods.toml" to """
                [[mods]]
                modId="multi"
                [[dependencies.multi]]
                    modId="neoforge"
                    type="required"
            """.trimIndent().b()
        )
        val onNeo = ModDependencyScanner.scanBytes(multi, "multi.jar", DepLoader.NEOFORGE)!!
        assertEquals(setOf(DepLoader.FABRIC, DepLoader.NEOFORGE), onNeo.loaders)
        assertEquals(listOf("neoforge"), onNeo.relations.map { it.targetId })
        assertTrue("no false 'missing fabric-api' on NeoForge", ModDependencyGraph.analyze(listOf(onNeo), DepLoader.NEOFORGE).isEmpty())

        val onFabric = ModDependencyScanner.scanBytes(multi, "multi.jar", DepLoader.FABRIC)!!
        assertEquals(listOf("fabric-api"), onFabric.relations.map { it.targetId })
    }

    @Test
    fun forgeJarJarCountsAsProvided() {
        val nested = jar("META-INF/mods.toml" to "[[mods]]\nmodId=\"bundled\"\n".b())
        val mod = jar(
            "META-INF/mods.toml" to """
                [[mods]]
                modId="main"
                [[dependencies.main]]
                    modId="bundled"
                    mandatory=true
            """.trimIndent().b(),
            "META-INF/jarjar/metadata.json" to """{"jars":[{"path":"META-INF/jarjar/bundled.jar"}]}""".b(),
            "META-INF/jarjar/bundled.jar" to nested
        )
        val info = ModDependencyScanner.scanBytes(mod, "main.jar", DepLoader.FORGE)!!
        assertTrue("bundled" in info.allProvidedIds())
        assertTrue(ModDependencyGraph.analyze(listOf(info), DepLoader.FORGE).isEmpty())
    }

    @Test
    fun legacyForgeMcmodInfo() {
        val mod = jar("mcmod.info" to """[{"modid":"oldmod","name":"Old Mod"}]""".b())
        val info = ModDependencyScanner.scanBytes(mod, "old.jar", DepLoader.FORGE)!!
        assertEquals("oldmod", info.id)
        assertEquals(setOf(DepLoader.FORGE), info.loaders)
    }

    @Test
    fun nonModJarsAndGarbageReturnNull() {
        assertNull(ModDependencyScanner.scanBytes(jar("assets/x.png" to byteArrayOf(1, 2)), "rp.zip", DepLoader.FABRIC))
        assertNull(ModDependencyScanner.scanBytes("not a zip".b(), "x.jar", DepLoader.FABRIC))
        val missing = File("does-not-exist-${System.nanoTime()}.jar")
        assertNull(ModDependencyScanner.scan(missing, DepLoader.FABRIC))
    }

    @Test
    fun preferredOrder() {
        assertEquals(listOf(DepLoader.QUILT, DepLoader.FABRIC), ModDependencyScanner.preferredOrder(DepLoader.QUILT))
        assertEquals(listOf(DepLoader.NEOFORGE, DepLoader.FORGE, DepLoader.FABRIC), ModDependencyScanner.preferredOrder(DepLoader.NEOFORGE))
    }
}
