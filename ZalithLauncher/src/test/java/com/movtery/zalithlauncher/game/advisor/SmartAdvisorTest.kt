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

package com.movtery.zalithlauncher.game.advisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class SmartAdvisorTest {
    private val bundled: Recommendations by lazy {
        // the asset shipped in the APK must always parse
        val f = listOf("src/main/assets/recommendations.json", "ZalithLauncher/src/main/assets/recommendations.json")
            .map { File(it) }.first { it.isFile }
        Recommendations.parse(f.readText())!!
    }

    private fun hw(vendor: String, model: Int?, ramGb: Int, vulkan: Boolean? = true) =
        HardwareSnapshot(vendor, model, ramGb * 1024, vulkan, cpuCores = 8, androidSdk = 36)

    @Test
    fun bundledMatrixParses() {
        assertTrue(bundled.ram.isNotEmpty())
        assertTrue(bundled.gpus.isNotEmpty())
    }

    @Test
    fun ramFollowsDeviceSizeAndCap() {
        assertEquals(1024, SmartAdvisor.recommendedRam(hw("ADRENO", 650, 4), bundled, 0))
        assertEquals(4096, SmartAdvisor.recommendedRam(hw("ADRENO", 825, 11), bundled, 0))
        // +512 MB per 50 mods
        assertEquals(5120, SmartAdvisor.recommendedRam(hw("ADRENO", 825, 11), bundled, 100))
        // never more than half the device RAM
        assertEquals(1536, SmartAdvisor.recommendedRam(hw("MALI", 57, 3), bundled, 400))
    }

    @Test
    fun adreno825GetsShadersAndDriverNote() {
        val advice = SmartAdvisor.advise(hw("ADRENO", 825, 11), bundled)
        assertNotNull(advice.filterIsInstance<Advice.Shaders>().singleOrNull())
        assertTrue(advice.filterIsInstance<Advice.DriverNote>().single().text.contains("825"))
    }

    @Test
    fun maliGetsMaliRule() {
        val advice = SmartAdvisor.advise(hw("MALI", 720, 8), bundled)
        assertTrue(advice.filterIsInstance<Advice.GpuNote>().single().text.contains("Mali"))
        assertTrue(advice.none { it is Advice.DriverNote })
    }

    @Test
    fun riskyShadersOnLowRamOrNoVulkan() {
        assertTrue(SmartAdvisor.advise(hw("MALI", 57, 4), bundled, hasShaderMod = true).contains(Advice.ShadersRisky))
        assertTrue(SmartAdvisor.advise(hw("ADRENO", 650, 8, vulkan = false), bundled, hasShaderMod = true).contains(Advice.ShadersRisky))
        assertTrue(SmartAdvisor.advise(hw("ADRENO", 825, 11), bundled, hasShaderMod = true).none { it == Advice.ShadersRisky })
    }

    // Signed with an OpenSSL-generated Ed25519 test key (not the release key)
    private val testPub = "d41c7ef7160834c50d04a335b72235e1d1bc326b3a8d5716060bcf47fee29c64"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val signedJson = """{"format":"zalith-recommendations","version":1,"issued":"t","ram":[{"deviceMaxMb":8192,"allocMb":3072},{"deviceMaxMb":2147483647,"allocMb":6144}],"gpus":[],"drivers":[]}"""
    private val signature = "h80ykSNSXQX9iHZi4DDQ28nNa3CT0mapr7UZeNzfpYYqMgqOxWtZO0kP52Clr30nJoBANapolZwwptTCXOd/Dw=="

    @Test
    fun signedMatrixIsAccepted() {
        val rec = Recommendations.parseSigned(signedJson.toByteArray(), signature, testPub)
        assertEquals(2, rec!!.ram.size)
    }

    @Test
    fun tamperedOrUnsignedMatrixIsRejected() {
        assertNull(Recommendations.parseSigned(signedJson.replace("3072", "9999").toByteArray(), signature, testPub))
        assertNull(Recommendations.parseSigned(signedJson.toByteArray(), "not base64!!", testPub))
        assertNull(Recommendations.parseSigned(signedJson.toByteArray(), Base64.getEncoder().encodeToString(ByteArray(64)), testPub))
    }

    @Test
    fun invalidMatricesAreRejected() {
        assertNull(Recommendations.parse("{}"))
        assertNull(Recommendations.parse("""{"format":"zalith-recommendations","version":99,"ram":[{"deviceMaxMb":1,"allocMb":1}]}"""))
        assertNull(Recommendations.parse("""{"format":"zalith-recommendations","version":1,"ram":[]}"""))
    }

    @Test
    fun upscalerAdviceFollowsGpuFamily() {
        fun up(v: String) = SmartAdvisor.advise(hw(v, 700, 8), bundled).filterIsInstance<Advice.Upscaler>().single().name
        assertEquals("SGSR", up("ADRENO"))
        assertEquals("FSR1", up("MALI"))
        assertEquals("FSR1", up("POWERVR"))
        assertEquals("FSR1", up("XCLIPSE"))
        assertEquals("FSR1", up("UNKNOWN"))
    }
}
