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

import kotlin.math.max
import kotlin.math.min

/**
 * Pure (Android-free, unit-tested) JVM sizing and GC flag decisions for game launches.
 *
 * All thresholds are engineering estimates, not measurements; they are named constants so
 * they can be tuned from device data.
 */
object JvmTuning {
    /** Never clamp below this; smaller heaps are only used if the user asked for them */
    const val MIN_HEAP_MB = 512

    /** Smallest ceiling ever applied; below this the JVM cannot start Minecraft at all */
    const val JVM_FLOOR_MB = 256

    /** A 32-bit process cannot reliably reserve a larger contiguous Java heap */
    const val MAX_HEAP_32BIT_MB = 1024

    /**
     * Upper bound for the Java heap as a fraction of physical RAM (heuristic).
     * The game process also needs native memory beyond the heap (see [NATIVE_OVERHEAD_MB]),
     * and Android plus other apps need the rest; exceeding this makes a low-memory-killer
     * kill (which looks like a silent crash) likely. Chosen so it is never below the
     * launcher's first-run defaults (findBestRAMAllocation) for any device tier.
     */
    const val MAX_HEAP_FRACTION_OF_RAM = 0.5

    /**
     * Estimated non-heap memory of the game process: metaspace, code cache, thread stacks,
     * LWJGL/OpenAL natives, GL/Vulkan driver and renderer buffers. Estimate.
     */
    const val NATIVE_OVERHEAD_MB = 768

    data class HeapDecision(
        /** Heap to pass as -Xms/-Xmx */
        val heapMb: Int,
        /** Original request if it was lowered, else null */
        val clampedFromMb: Int?,
        /** Ceiling that applied */
        val ceilingMb: Int,
        /** Heap + estimated native overhead exceeds memory currently available on the device */
        val lowMemoryWarning: Boolean
    )

    fun heapCeilingMb(totalRamMb: Long, is64Bit: Boolean): Int {
        val byRam = (totalRamMb * MAX_HEAP_FRACTION_OF_RAM).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bounded = if (is64Bit) byRam else min(byRam, MAX_HEAP_32BIT_MB)
        // strict: never above half of RAM, even on very small devices (only the JVM's own
        // practical minimum can exceed it, on devices below 512 MB that cannot run the game anyway)
        return max(bounded, JVM_FLOOR_MB)
    }

    /**
     * @param requestedMb heap from the version config / global setting
     * @param totalRamMb physical RAM (ActivityManager.MemoryInfo.totalMem)
     * @param availRamMb currently available RAM (MemoryInfo.availMem); <= 0 if unknown
     * @param safetyLimit when false the request is used unchanged (only the warning is computed)
     */
    fun resolveHeap(
        requestedMb: Int,
        totalRamMb: Long,
        availRamMb: Long,
        is64Bit: Boolean,
        safetyLimit: Boolean
    ): HeapDecision {
        val ceiling = heapCeilingMb(totalRamMb, is64Bit)
        val heap = if (safetyLimit && requestedMb > ceiling) ceiling else requestedMb
        val warning = availRamMb > 0 && heap.toLong() + NATIVE_OVERHEAD_MB > availRamMb
        return HeapDecision(
            heapMb = heap,
            clampedFromMb = requestedMb.takeIf { heap != it },
            ceilingMb = ceiling,
            lowMemoryWarning = warning
        )
    }

    /**
     * GC flags for the game JVM. Returns an empty list when the user already selected a
     * collector, and never repeats an option the user set explicitly (later occurrences
     * would override the user's value in HotSpot).
     *
     * - Java 11+: G1 is the default only on "server-class" machines; it is made explicit so
     *   low core/RAM detection cannot fall back to SerialGC, plus a 50 ms pause goal.
     * - Java 8 (Minecraft <= 1.16): ParallelGC is the default. With >= 1 GB heap, use the
     *   G1 set the official Minecraft launcher ships for Java 8 (without a fixed region size,
     *   which is poorly suited to small heaps). Smaller heaps keep the default collector.
     */
    fun gcArgs(javaVersion: Int, heapMb: Int, existingArgs: List<String>): List<String> {
        if (existingArgs.any { GC_SELECTION.matches(it) }) return emptyList()

        val wanted = when {
            javaVersion >= 11 -> listOf(
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50"
            )
            javaVersion in 1..10 && heapMb >= 1024 -> listOf(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50"
            )
            else -> emptyList()
        }
        val userKeys = existingArgs.mapNotNull { optionKey(it) }.toSet()
        return wanted.filter { optionKey(it) !in userKeys }
    }

    /** "-XX:+UseG1GC" -> "UseG1GC", "-XX:MaxGCPauseMillis=50" -> "MaxGCPauseMillis" */
    internal fun optionKey(arg: String): String? {
        if (!arg.startsWith("-XX:")) return null
        return arg.removePrefix("-XX:").removePrefix("+").removePrefix("-").substringBefore('=').ifEmpty { null }
    }

    private val GC_SELECTION = Regex("""^-XX:[+-]Use\w*GC$""")
}
