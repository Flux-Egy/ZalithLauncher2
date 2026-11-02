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

package com.movtery.zalithlauncher.game.renderer.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RendererAutoSelectorTest {
    /** Loads the real shipped asset so the rules themselves are tested */
    private val compat: RendererCompat = RendererCompat.parse(
        listOf(File("src/main/assets/renderer_compat.json"), File("ZalithLauncher/src/main/assets/renderer_compat.json"))
            .first { it.isFile }.readText()
    )

    private val all = setOf(
        "opengles3", "opengles2", "opengles3_mobileglues", "opengles3_desktopgl_zink_kopper",
        "gallium_virgl", "gallium_freedreno", "gallium_panfrost"
    )

    /** Simple numeric comparison, enough for release versions in tests */
    private fun atLeast(mc: String): (String) -> Boolean = { other ->
        val a = mc.split('.').map { it.toInt() }
        val b = other.split('.').map { it.toInt() }
        (0 until maxOf(a.size, b.size)).map { a.getOrElse(it) { 0 }.compareTo(b.getOrElse(it) { 0 }) }.firstOrNull { it != 0 }?.let { it > 0 } ?: true
    }

    private val adreno740 = GpuInfo.fromGlStrings("Qualcomm", "Adreno (TM) 740", vulkanSupported = true)
    private val adreno505 = GpuInfo.fromGlStrings("Qualcomm", "Adreno (TM) 505", vulkanSupported = true)
    private val mali = GpuInfo.fromGlStrings("ARM", "Mali-G715", vulkanSupported = true)
    private val powervr = GpuInfo.fromGlStrings("Imagination Technologies", "PowerVR Rogue GE8320", vulkanSupported = false)

    private fun pick(gpu: GpuInfo, shaders: Boolean, mc: String, available: Set<String> = all) =
        RendererAutoSelector.select(compat, gpu, shaders, atLeast(mc), available)

    // ------------------------------------------------------------ GPU parsing

    @Test
    fun parsesGpuStrings() {
        assertEquals(GpuVendor.ADRENO, adreno740.vendor); assertEquals(740, adreno740.model)
        assertEquals(GpuVendor.MALI, mali.vendor); assertEquals(715, mali.model)
        assertEquals(GpuVendor.MALI, GpuInfo.fromGlStrings("ARM", "Immortalis-G720").vendor)
        assertEquals(720, GpuInfo.fromGlStrings("ARM", "Immortalis-G720").model)
        assertEquals(GpuVendor.POWERVR, powervr.vendor); assertEquals(8320, powervr.model)
        assertEquals(GpuVendor.XCLIPSE, GpuInfo.fromGlStrings("Samsung", "Samsung Xclipse 940").vendor)
        assertEquals(940, GpuInfo.fromGlStrings("Samsung", "Samsung Xclipse 940").model)
        assertEquals(GpuVendor.OTHER, GpuInfo.fromGlStrings("Google", "SwiftShader").vendor)
        assertEquals(GpuVendor.UNKNOWN, GpuInfo.fromGlStrings(null, null).vendor)
    }

    // -------------------------------------------------------------- selection

    @Test
    fun shaderModsOnModernAdrenoPreferZink() {
        val s = pick(adreno740, shaders = true, mc = "1.21.1")!!
        assertEquals("opengles3_desktopgl_zink_kopper", s.rendererId)
        assertEquals("shaders-adreno-vulkan", s.ruleId)
    }

    @Test
    fun oldAdrenoWithShadersFallsToGenericShaderRule() {
        assertEquals("opengles3_mobileglues", pick(adreno505, shaders = true, mc = "1.21.1")!!.rendererId)
    }

    @Test
    fun shaderModsOnMaliPreferMobileGlues() {
        assertEquals("opengles3_mobileglues", pick(mali, shaders = true, mc = "1.20.1")!!.rendererId)
    }

    @Test
    fun noVulkanSkipsZinkWithinARule() {
        val noVk = adreno740.copy(vulkanSupported = false)
        assertEquals("opengles3_mobileglues", pick(noVk, shaders = true, mc = "1.21.1")!!.rendererId)
    }

    @Test
    fun legacyVersionsUseGl4esFamily() {
        assertEquals("opengles3", pick(adreno740, shaders = false, mc = "1.12.2")!!.rendererId)
        assertEquals("legacy", pick(mali, shaders = true, mc = "1.16.5")!!.ruleId) // shader rules need 1.17+
        assertEquals("opengles2", pick(mali, shaders = false, mc = "1.8.9", available = all - "opengles3")!!.rendererId)
    }

    @Test
    fun defaultKeepsTodaysDefault() {
        for (gpu in listOf(adreno740, mali, powervr, GpuInfo.UNKNOWN)) {
            val s = pick(gpu, shaders = false, mc = "1.21.1")!!
            assertEquals("opengles3", s.rendererId)
            assertEquals("default", s.ruleId)
        }
    }

    @Test
    fun unavailableRenderersAreSkippedAndNothingAvailableGivesNull() {
        assertEquals("opengles3_mobileglues", pick(adreno740, shaders = false, mc = "1.21.1", available = all - "opengles3")!!.rendererId)
        assertNull(pick(adreno740, shaders = false, mc = "1.21.1", available = emptySet()))
    }

    // --------------------------------------------------- hardware constraints

    @Test
    fun hardwareConstraints() {
        assertFalse(RendererAutoSelector.isCompatible(compat, "gallium_freedreno", mali))
        assertTrue(RendererAutoSelector.isCompatible(compat, "gallium_freedreno", adreno740))
        assertFalse(RendererAutoSelector.isCompatible(compat, "gallium_panfrost", adreno740))
        assertTrue(RendererAutoSelector.isCompatible(compat, "gallium_panfrost", mali))
        assertFalse(RendererAutoSelector.isCompatible(compat, "opengles3_desktopgl_zink_kopper", powervr))
        // unknown GPU / unknown Vulkan never block
        assertTrue(RendererAutoSelector.isCompatible(compat, "gallium_freedreno", GpuInfo.UNKNOWN))
        assertTrue(RendererAutoSelector.isCompatible(compat, "opengles3_desktopgl_zink_kopper", adreno740.copy(vulkanSupported = null)))
        assertTrue(RendererAutoSelector.isCompatible(compat, "plugin_renderer_xyz", mali))
    }

    // ------------------------------------------------------- shader detection

    @Test
    fun shaderModDetectionByFileName() {
        assertTrue(RendererAutoSelector.hasShaderMod(listOf("sodium-0.6.jar", "iris-1.8.0+1.21.1.jar")))
        assertTrue(RendererAutoSelector.hasShaderMod(listOf("oculus-mc1.20.1-1.7.0.jar")))
        assertTrue(RendererAutoSelector.hasShaderMod(listOf("OptiFine_1.12.2_HD_U_G5.jar")))
        assertFalse(RendererAutoSelector.hasShaderMod(listOf("iris-1.8.0.jar.disabled", "sodium.jar")))
        assertFalse(RendererAutoSelector.hasShaderMod(listOf("iris-notes.txt")))
    }

    // ----------------------------------------------------------------- parser

    @Test
    fun parserRejectsBadContent() {
        fun fails(json: String) = runCatching { RendererCompat.parse(json) }.isFailure
        assertTrue(fails("not json"))
        assertTrue(fails("""{"schemaVersion":2,"rules":[{"id":"x","when":{},"prefer":["a"]}]}"""))
        assertTrue(fails("""{"schemaVersion":1,"rules":[]}"""))
        assertTrue(fails("""{"schemaVersion":1,"rules":[{"when":{},"prefer":["a"]}]}"""))
        val ok = RendererCompat.parse("""{"schemaVersion":1,"rules":[{"id":"d","when":{"gpuVendors":["adreno","bogus"]},"prefer":["a"]}]}""")
        assertEquals(setOf(GpuVendor.ADRENO), ok.rules.single().condition.gpuVendors)
    }
}
