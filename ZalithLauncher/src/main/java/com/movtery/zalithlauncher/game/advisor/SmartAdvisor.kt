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

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.security.Ed25519
import java.util.Base64

/** What the advisor knows about the device (collected by HardwareProfiler) */
data class HardwareSnapshot(
    /** GpuVendor name: ADRENO, MALI, POWERVR, XCLIPSE, OTHER, UNKNOWN */
    val gpuVendor: String,
    val gpuModel: Int?,
    val totalRamMb: Int,
    val vulkanSupported: Boolean?,
    val cpuCores: Int,
    val androidSdk: Int,
    val socModel: String? = null
)

/** One rule of the recommendation matrix for a GPU family */
data class GpuRule(
    val vendor: String,
    val minModel: Int,
    val maxModel: Int,
    val shaders: List<String>,
    val note: String?
) {
    fun matches(vendor: String, model: Int?): Boolean =
        this.vendor == vendor && (model == null || model in minModel..maxModel)
}

data class RamRule(val deviceMaxMb: Int, val allocMb: Int)

/** The recommendation matrix (bundled, or fetched and signature-checked) */
data class Recommendations(
    val issued: String,
    val ram: List<RamRule>,
    val extraMbPer50Mods: Int,
    val gpus: List<GpuRule>,
    /** Driver notes; a rule's [GpuRule.shaders] is unused here */
    val drivers: List<GpuRule>
) {
    companion object {
        const val FORMAT = "zalith-recommendations"
        const val VERSION = 1
        const val MAX_BYTES = 256 * 1024

        /** @return null if [text] is not a valid matrix */
        fun parse(text: String): Recommendations? {
            if (text.length > MAX_BYTES) return null
            val root = runCatching { JsonParser.parseString(text) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            if (root.str("format") != FORMAT) return null
            if ((root.int("version") ?: return null) > VERSION) return null
            val ram = root.arr("ram").mapNotNull { e ->
                val o = e.objOrNull() ?: return@mapNotNull null
                RamRule(o.int("deviceMaxMb") ?: return@mapNotNull null, o.int("allocMb") ?: return@mapNotNull null)
            }.sortedBy { it.deviceMaxMb }
            if (ram.isEmpty()) return null
            return Recommendations(
                issued = root.str("issued").orEmpty(),
                ram = ram,
                extraMbPer50Mods = (root.int("extraMbPer50Mods") ?: 0).coerceIn(0, 4096),
                gpus = root.arr("gpus").mapNotNull { gpuRule(it) },
                drivers = root.arr("drivers").mapNotNull { gpuRule(it) }
            )
        }

        private fun gpuRule(e: JsonElement): GpuRule? {
            val o = e.objOrNull() ?: return null
            return GpuRule(
                vendor = o.str("vendor") ?: return null,
                minModel = o.int("minModel") ?: 0,
                maxModel = o.int("maxModel") ?: Int.MAX_VALUE,
                shaders = o.arr("shaders").mapNotNull { s -> s.takeIf { it.isJsonPrimitive }?.asString?.take(80) }.take(8),
                note = o.str("note")?.take(400)
            )
        }

        /**
         * Parses [json] only if [signatureBase64] is a valid Ed25519 signature of the exact
         * bytes of [json] by [publicKey].
         */
        fun parseSigned(json: ByteArray, signatureBase64: String, publicKey: ByteArray): Recommendations? {
            val signature = runCatching { Base64.getDecoder().decode(signatureBase64.trim()) }.getOrNull() ?: return null
            if (!Ed25519.verify(publicKey, json, signature)) return null
            return parse(String(json, Charsets.UTF_8))
        }

        private fun JsonElement.objOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.str(k: String): String? =
            get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        private fun JsonObject.int(k: String): Int? =
            get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                ?.takeIf { it.isFinite() }?.toInt()
        private fun JsonObject.arr(k: String): List<JsonElement> =
            get(k)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    }
}

/** Advice items; the UI turns them into localized text */
sealed interface Advice {
    /** Suggested max memory for the game */
    data class Ram(val recommendedMb: Int, val deviceMb: Int, val currentMb: Int?) : Advice

    /** Shader packs suited to this GPU tier */
    data class Shaders(val names: List<String>) : Advice

    /** Free-form note from the matrix about this GPU */
    data class GpuNote(val text: String) : Advice

    /** Free-form note about drivers for this GPU */
    data class DriverNote(val text: String) : Advice

    /** Shaders installed, but the device is unlikely to handle them (no Vulkan / low RAM) */
    data object ShadersRisky : Advice
}

object SmartAdvisor {

    /**
     * @param modCount mods in the selected instance (adds memory headroom)
     * @param currentRamMb the memory currently allocated to the game, if known
     * @param hasShaderMod whether the instance has a shader loader (Iris/Oculus/OptiFine)
     */
    fun advise(
        hw: HardwareSnapshot,
        matrix: Recommendations,
        modCount: Int = 0,
        currentRamMb: Int? = null,
        hasShaderMod: Boolean = false
    ): List<Advice> = buildList {
        add(Advice.Ram(recommendedRam(hw, matrix, modCount), hw.totalRamMb, currentRamMb))

        val gpu = matrix.gpus.firstOrNull { it.matches(hw.gpuVendor, hw.gpuModel) }
        if (gpu != null) {
            if (gpu.shaders.isNotEmpty()) add(Advice.Shaders(gpu.shaders))
            gpu.note?.let { add(Advice.GpuNote(it)) }
        }
        matrix.drivers.filter { it.matches(hw.gpuVendor, hw.gpuModel) }
            .mapNotNull { it.note }
            .forEach { add(Advice.DriverNote(it)) }

        if (hasShaderMod && (hw.vulkanSupported == false || hw.totalRamMb < 6 * 1024)) {
            add(Advice.ShadersRisky)
        }
    }

    /**
     * Memory to allocate: the matrix value for the device's RAM, plus headroom for large
     * mod lists, never more than half of the device RAM (Android needs the rest).
     */
    fun recommendedRam(hw: HardwareSnapshot, matrix: Recommendations, modCount: Int): Int {
        val base = matrix.ram.firstOrNull { hw.totalRamMb <= it.deviceMaxMb }?.allocMb ?: matrix.ram.last().allocMb
        val extra = (modCount / 50) * matrix.extraMbPer50Mods
        val cap = hw.totalRamMb / 2
        // round down to 256 MB steps
        return (minOf(base + extra, cap) / 256 * 256).coerceAtLeast(512)
    }
}
