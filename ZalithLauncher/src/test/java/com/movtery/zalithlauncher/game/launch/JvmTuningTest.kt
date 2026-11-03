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

package com.movtery.zalithlauncher.game.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmTuningTest {
    /** Launcher first-run defaults (SettingsInitializer.findBestRAMAllocation), 64-bit */
    private val defaultTiers = listOf(
        900L to 296, 1400L to 448, 1850L to 656, 2800L to 936, 3700L to 1144, 5600L to 1536, 7600L to 2048, 11500L to 2048
    )

    @Test
    fun ceilingNeverClampsTheFirstRunDefaults() {
        for ((ram, default) in defaultTiers) {
            val d = JvmTuning.resolveHeap(default, ram, availRamMb = -1, is64Bit = true, safetyLimit = true)
            assertEquals("RAM $ram MB", default, d.heapMb)
            assertNull(d.clampedFromMb)
        }
    }

    @Test
    fun clampsOversizedRequestsToHalfOfRam() {
        // 8 GB phone (reports ~7600 MB), old settings max was total - 1024 = 6576 MB
        val d = JvmTuning.resolveHeap(6576, 7600, availRamMb = -1, is64Bit = true, safetyLimit = true)
        assertEquals(3800, d.heapMb)
        assertEquals(6576, d.clampedFromMb)
        assertEquals(3800, d.ceilingMb)
    }

    @Test
    fun safetyLimitOffKeepsRequest() {
        val d = JvmTuning.resolveHeap(6576, 7600, availRamMb = -1, is64Bit = true, safetyLimit = false)
        assertEquals(6576, d.heapMb)
        assertNull(d.clampedFromMb)
    }

    @Test
    fun neverRaisesASmallRequest() {
        val d = JvmTuning.resolveHeap(300, 7600, availRamMb = -1, is64Bit = true, safetyLimit = true)
        assertEquals(300, d.heapMb)
        assertNull(d.clampedFromMb)
    }

    @Test
    fun thirtyTwoBitIsCappedAndTinyDevicesKeepMinimumCeiling() {
        assertEquals(1024, JvmTuning.heapCeilingMb(7600, is64Bit = false))
        assertEquals(696, JvmTuning.resolveHeap(696, 3700, -1, is64Bit = false, safetyLimit = true).heapMb)
        // strict 50 %: a 700 MB device gets at most 350 MB
        assertEquals(350, JvmTuning.heapCeilingMb(700, is64Bit = true))
    }

    @Test
    fun ceilingNeverExceedsHalfOfRam() {
        for (ram in listOf(512L, 700L, 900L, 1024L, 1400L, 2048L, 3072L, 4096L, 6144L, 8192L, 11500L, 16384L)) {
            for (is64 in listOf(true, false)) {
                val c = JvmTuning.heapCeilingMb(ram, is64)
                assertTrue("RAM $ram 64=$is64 ceiling $c", c <= ram / 2)
                // and a huge request is clamped to it
                assertEquals(c, JvmTuning.resolveHeap(100_000, ram, -1, is64, safetyLimit = true).heapMb)
            }
        }
    }

    @Test
    fun lowMemoryWarningUsesAvailableRamPlusNativeOverhead() {
        val low = JvmTuning.resolveHeap(2048, 7600, availRamMb = 2048 + JvmTuning.NATIVE_OVERHEAD_MB - 1L, is64Bit = true, safetyLimit = true)
        assertTrue(low.lowMemoryWarning)
        val ok = JvmTuning.resolveHeap(2048, 7600, availRamMb = 2048 + JvmTuning.NATIVE_OVERHEAD_MB.toLong(), is64Bit = true, safetyLimit = true)
        assertFalse(ok.lowMemoryWarning)
        assertFalse(JvmTuning.resolveHeap(2048, 7600, availRamMb = -1, is64Bit = true, safetyLimit = true).lowMemoryWarning)
    }

    @Test
    fun modernJavaGetsExplicitG1() {
        assertEquals(listOf("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"), JvmTuning.gcArgs(21, 2048, emptyList()))
        assertEquals(listOf("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"), JvmTuning.gcArgs(17, 512, emptyList()))
    }

    @Test
    fun java8UsesOfficialG1SetOnlyWithLargeEnoughHeap() {
        assertEquals(
            listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50"),
            JvmTuning.gcArgs(8, 1024, emptyList())
        )
        assertTrue(JvmTuning.gcArgs(8, 1023, emptyList()).isEmpty())
        assertTrue(JvmTuning.gcArgs(0, 4096, emptyList()).isEmpty()) // unknown runtime version
    }

    @Test
    fun userSelectedCollectorWins() {
        assertTrue(JvmTuning.gcArgs(21, 4096, listOf("-Xmx4G", "-XX:+UseZGC")).isEmpty())
        assertTrue(JvmTuning.gcArgs(8, 4096, listOf("-XX:+UseParallelGC")).isEmpty())
        assertTrue(JvmTuning.gcArgs(21, 4096, listOf("-XX:-UseG1GC")).isEmpty())
    }

    @Test
    fun userOptionsAreNeverOverridden() {
        assertEquals(listOf("-XX:+UseG1GC"), JvmTuning.gcArgs(21, 4096, listOf("-XX:MaxGCPauseMillis=200")))
        assertEquals(
            listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20", "-XX:MaxGCPauseMillis=50"),
            JvmTuning.gcArgs(8, 2048, listOf("-XX:G1ReservePercent=10"))
        )
    }

    @Test
    fun optionKeyParsing() {
        assertEquals("UseG1GC", JvmTuning.optionKey("-XX:+UseG1GC"))
        assertEquals("UseG1GC", JvmTuning.optionKey("-XX:-UseG1GC"))
        assertEquals("MaxGCPauseMillis", JvmTuning.optionKey("-XX:MaxGCPauseMillis=50"))
        assertNull(JvmTuning.optionKey("-Xmx2G"))
        assertNull(JvmTuning.optionKey("-XX:"))
    }
}
