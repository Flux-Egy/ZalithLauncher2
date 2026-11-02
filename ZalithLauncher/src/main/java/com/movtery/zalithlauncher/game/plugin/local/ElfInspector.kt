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

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ELF header / program header reader for validating imported native libraries.
 * Pure Kotlin; reads only the headers.
 */
object ElfInspector {
    const val PAGE_16K = 16384L

    private const val PT_LOAD = 1
    private const val ET_DYN = 3

    data class ElfInfo(
        /** Android ABI name (arm64-v8a, armeabi-v7a, x86_64, x86) or null if unsupported machine */
        val abi: String?,
        val is64Bit: Boolean,
        val isSharedObject: Boolean,
        /** Smallest p_align of PT_LOAD segments, null if none */
        val minLoadAlign: Long?
    ) {
        /** 16 KB page-size compatibility (only meaningful for 64-bit ABIs) */
        val is16KbAligned: Boolean get() = minLoadAlign != null && minLoadAlign >= PAGE_16K
    }

    /** @return null if [bytes] is not a little-endian ELF file we can read */
    fun inspect(bytes: ByteArray): ElfInfo? = runCatching {
        if (bytes.size < 52) return null
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() || bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()) return null
        val is64 = when (bytes[4].toInt()) { 1 -> false; 2 -> true; else -> return null }
        if (bytes[5].toInt() != 1) return null // little-endian only (all Android ABIs)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = buf.getShort(16).toInt() and 0xFFFF
        val machine = buf.getShort(18).toInt() and 0xFFFF
        val abi = when (machine) {
            183 -> "arm64-v8a"   // EM_AARCH64
            40 -> "armeabi-v7a"  // EM_ARM
            62 -> "x86_64"       // EM_X86_64
            3 -> "x86"           // EM_386
            else -> null
        }

        val phOff: Long
        val phEntSize: Int
        val phNum: Int
        if (is64) {
            if (bytes.size < 64) return null
            phOff = buf.getLong(32)
            phEntSize = buf.getShort(54).toInt() and 0xFFFF
            phNum = buf.getShort(56).toInt() and 0xFFFF
        } else {
            phOff = (buf.getInt(28).toLong() and 0xFFFFFFFFL)
            phEntSize = buf.getShort(42).toInt() and 0xFFFF
            phNum = buf.getShort(44).toInt() and 0xFFFF
        }

        var minAlign: Long? = null
        for (i in 0 until phNum) {
            val base = phOff + i.toLong() * phEntSize
            val alignOffset = if (is64) base + 48 else base + 28
            val needed = if (is64) alignOffset + 8 else alignOffset + 4
            if (base < 0 || needed > bytes.size) break
            val pType = buf.getInt(base.toInt())
            if (pType != PT_LOAD) continue
            val align = if (is64) buf.getLong(alignOffset.toInt()) else (buf.getInt(alignOffset.toInt()).toLong() and 0xFFFFFFFFL)
            minAlign = if (minAlign == null) align else minOf(minAlign, align)
        }
        ElfInfo(abi = abi, is64Bit = is64, isSharedObject = type == ET_DYN, minLoadAlign = minAlign)
    }.getOrNull()
}
