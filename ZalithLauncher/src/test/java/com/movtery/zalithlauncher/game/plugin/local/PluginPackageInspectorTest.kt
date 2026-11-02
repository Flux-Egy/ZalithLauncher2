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

package com.movtery.zalithlauncher.game.plugin.local

import com.movtery.zalithlauncher.game.plugin.local.PluginPackageInspector.InvalidPackageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Uses real prebuilt libraries from the repository as ELF fixtures:
 * - arm64 libEGL_angle.so  (16 KB aligned)
 * - arm64 libgl4es_114.so  (4 KB aligned)
 * - armeabi-v7a libgl4es_114.so (32-bit ARM)
 */
class PluginPackageInspectorTest {
    private fun jniLib(abi: String, name: String): ByteArray =
        listOf(File("src/main/jniLibs/$abi/$name"), File("ZalithLauncher/src/main/jniLibs/$abi/$name"))
            .first { it.isFile }.readBytes()

    private val arm64Aligned by lazy { jniLib("arm64-v8a", "libEGL_angle.so") }
    private val arm64Unaligned by lazy { jniLib("arm64-v8a", "libgl4es_114.so") }
    private val arm32 by lazy { jniLib("armeabi-v7a", "libgl4es_114.so") }

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val f = Files.createTempFile("pkg", ".zip").toFile().apply { deleteOnExit() }
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return f
    }

    private fun String.b() = toByteArray()

    private fun assertInvalid(file: File, abi: String = "arm64-v8a", api: Int = 34, contains: String) {
        try {
            PluginPackageInspector.inspect(file, abi, api)
            fail("expected InvalidPackageException containing '$contains'")
        } catch (e: InvalidPackageException) {
            assertTrue("message '${e.message}' should contain '$contains'", e.message!!.contains(contains))
        }
    }

    // ------------------------------------------------------------------- ELF

    @Test
    fun elfInspectorReadsAbiAndAlignment() {
        val a = ElfInspector.inspect(arm64Aligned)!!
        assertEquals("arm64-v8a", a.abi); assertTrue(a.is64Bit); assertTrue(a.isSharedObject); assertTrue(a.is16KbAligned)
        val u = ElfInspector.inspect(arm64Unaligned)!!
        assertEquals(4096L, u.minLoadAlign); assertFalse(u.is16KbAligned)
        val v7 = ElfInspector.inspect(arm32)!!
        assertEquals("armeabi-v7a", v7.abi); assertFalse(v7.is64Bit)
        assertNull(ElfInspector.inspect("not an elf at all, just text".b()))
        assertNull(ElfInspector.inspect(arm64Aligned.copyOf(20)))
    }

    // -------------------------------------------------------------- renderer

    private val manifest = """{"schemaVersion":1,"id":"my_gl","name":"My GL","glLibrary":"libmygl.so",
        "env":{"MY_FLAG":"1"},"dlopen":["libdep.so"],"minMcVersion":"1.17"}"""

    @Test
    fun validRendererPackage() {
        val r = PluginPackageInspector.inspect(
            zip("renderer.json" to manifest.b(), "lib/arm64-v8a/libmygl.so" to arm64Aligned, "lib/arm64-v8a/libdep.so" to arm64Aligned,
                "lib/armeabi-v7a/libmygl.so" to arm32),
            "arm64-v8a", 34
        )
        assertEquals(PluginPackageInspector.Type.RENDERER, r.type)
        assertEquals("my_gl", r.id)
        assertEquals("My GL", r.displayName)
        assertEquals(setOf("libmygl.so", "libdep.so"), r.libraries.map { it.installName }.toSet()) // other ABIs ignored
        assertEquals(mapOf("MY_FLAG" to "1"), r.renderer!!.env)
        assertEquals("1.17", r.renderer!!.minMcVersion)
        assertTrue(r.warnings.isEmpty())
        assertEquals(64, r.libraries.first().sha256.length)
    }

    @Test
    fun unalignedLibraryIsAWarningNotAnError() {
        val r = PluginPackageInspector.inspect(
            zip("renderer.json" to """{"schemaVersion":1,"id":"x","glLibrary":"libx.so"}""".b(), "lib/arm64-v8a/libx.so" to arm64Unaligned),
            "arm64-v8a", 34
        )
        val w = r.warnings.single() as PluginPackageInspector.Warning.Not16KbAligned
        assertEquals("libx.so", w.library)
        assertEquals(4096L, w.align)
    }

    @Test
    fun rendererRejections() {
        assertInvalid(zip("renderer.json" to manifest.b(), "lib/armeabi-v7a/libmygl.so" to arm32), contains = "arm64-v8a")
        assertInvalid(zip("renderer.json" to manifest.b(), "lib/arm64-v8a/libmygl.so" to arm32, "lib/arm64-v8a/libdep.so" to arm32), contains = "armeabi-v7a")
        assertInvalid(zip("renderer.json" to manifest.b(), "lib/arm64-v8a/libmygl.so" to arm64Aligned), contains = "libdep.so")
        assertInvalid(zip("renderer.json" to manifest.b(), "lib/arm64-v8a/libmygl.so" to "text".b(), "lib/arm64-v8a/libdep.so" to arm64Aligned), contains = "not an ELF")
        assertInvalid(zip("renderer.json" to """{"schemaVersion":2,"id":"x","glLibrary":"a.so"}""".b()), contains = "schemaVersion")
        assertInvalid(zip("renderer.json" to """{"schemaVersion":1,"id":"../evil","glLibrary":"a.so"}""".b()), contains = "invalid id")
        assertInvalid(zip("renderer.json" to """{"schemaVersion":1,"id":"x","glLibrary":"../a.so"}""".b()), contains = "invalid library name")
    }

    // ---------------------------------------------------------------- driver

    private val meta = """{"schemaVersion":1,"name":"Turnip v25","libraryName":"vulkan.ad07XX.so","driverVersion":"Vulkan 1.3","minApi":28}"""

    @Test
    fun validAdrenoToolsDriverPackage() {
        val d = PluginPackageInspector.inspect(zip("meta.json" to meta.b(), "vulkan.ad07XX.so" to arm64Aligned), "arm64-v8a", 34)
        assertEquals(PluginPackageInspector.Type.DRIVER, d.type)
        assertEquals("Turnip v25", d.displayName)
        assertEquals(PluginPackageInspector.DRIVER_INSTALL_NAME, d.libraries.single().installName)
        assertTrue(d.id.startsWith("driver_"))
        assertEquals("Vulkan 1.3", d.driver!!.driverVersion)
    }

    @Test
    fun driverRejections() {
        assertInvalid(zip("meta.json" to meta.b(), "vulkan.ad07XX.so" to arm64Aligned), abi = "armeabi-v7a", contains = "arm64-v8a device")
        assertInvalid(zip("meta.json" to meta.b(), "vulkan.ad07XX.so" to arm64Aligned), api = 27, contains = "API 28")
        assertInvalid(zip("meta.json" to meta.b()), contains = "not in the package")
        assertInvalid(zip("meta.json" to meta.b(), "vulkan.ad07XX.so" to arm32), contains = "armeabi-v7a")
    }

    // -------------------------------------------------------------- general

    @Test
    fun generalRejections() {
        assertInvalid(zip("readme.txt" to "hi".b()), contains = "Not a renderer package")
        assertInvalid(zip("../escape.so" to arm64Aligned, "meta.json" to meta.b()), contains = "Unsafe path")
        assertInvalid(zip("/abs/x.so" to arm64Aligned, "meta.json" to meta.b()), contains = "Unsafe path")
        val notZip = Files.createTempFile("x", ".zip").toFile().apply { writeText("not a zip"); deleteOnExit() }
        assertInvalid(notZip, contains = "Not a valid zip")
    }
}
