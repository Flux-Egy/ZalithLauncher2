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

import com.google.gson.JsonParser

/**
 * Data-driven renderer compatibility rules (assets/renderer_compat.json). Pure Kotlin.
 *
 * Renderer ids are RendererInterface.getRendererId() values (e.g. "opengles3_mobileglues").
 */
data class RendererCompat(
    val schemaVersion: Int,
    val renderers: Map<String, RendererConstraint>,
    val rules: List<Rule>
) {
    data class RendererConstraint(
        /** If non-empty, the renderer only works on these GPU vendors */
        val gpuVendors: Set<GpuVendor> = emptySet(),
        val requiresVulkan: Boolean = false
    )

    data class Condition(
        val shaderMod: Boolean? = null,
        val minMc: String? = null,
        /** Exclusive upper bound: matches versions lower than this */
        val belowMc: String? = null,
        val gpuVendors: Set<GpuVendor> = emptySet(),
        val minGpuModel: Int? = null
    )

    data class Rule(val id: String, val condition: Condition, val prefer: List<String>)

    companion object {
        const val SUPPORTED_SCHEMA = 1

        /** @throws IllegalArgumentException on malformed / unsupported content */
        fun parse(json: String): RendererCompat {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }
                .getOrElse { throw IllegalArgumentException("renderer_compat.json is not a JSON object", it) }
            val schema = root.get("schemaVersion")?.asInt ?: throw IllegalArgumentException("missing schemaVersion")
            require(schema == SUPPORTED_SCHEMA) { "unsupported schemaVersion $schema" }

            fun vendors(element: com.google.gson.JsonElement?): Set<GpuVendor> =
                element?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { e -> runCatching { GpuVendor.valueOf(e.asString.uppercase()) }.getOrNull() }
                    ?.toSet().orEmpty()

            val renderers = root.getAsJsonObject("renderers")?.entrySet()?.associate { (id, value) ->
                val o = value.asJsonObject
                id to RendererConstraint(
                    gpuVendors = vendors(o.get("gpuVendors")),
                    requiresVulkan = o.get("requiresVulkan")?.asBoolean ?: false
                )
            }.orEmpty()

            val rules = root.getAsJsonArray("rules")?.map { element ->
                val o = element.asJsonObject
                val w = o.getAsJsonObject("when")
                Rule(
                    id = o.get("id")?.asString ?: throw IllegalArgumentException("rule without id"),
                    condition = Condition(
                        shaderMod = w?.get("shaderMod")?.asBoolean,
                        minMc = w?.get("minMc")?.asString,
                        belowMc = w?.get("belowMc")?.asString,
                        gpuVendors = vendors(w?.get("gpuVendors")),
                        minGpuModel = w?.get("minGpuModel")?.asInt
                    ),
                    prefer = o.getAsJsonArray("prefer")?.map { it.asString }.orEmpty()
                )
            }.orEmpty()
            require(rules.isNotEmpty()) { "no rules" }
            return RendererCompat(schema, renderers, rules)
        }
    }
}

/**
 * Picks a renderer for a launch and flags hardware mismatches. Pure (unit tested).
 */
object RendererAutoSelector {

    data class Selection(val rendererId: String, val ruleId: String)

    /**
     * @param mcAtLeast true if the game version is >= the given version string
     * @param available renderer ids that are actually installed/usable in this build
     * @return null if no rule yields an available, hardware-compatible renderer
     */
    fun select(
        compat: RendererCompat,
        gpu: GpuInfo,
        hasShaderMod: Boolean,
        mcAtLeast: (String) -> Boolean,
        available: Set<String>
    ): Selection? {
        for (rule in compat.rules) {
            if (!matches(rule.condition, gpu, hasShaderMod, mcAtLeast)) continue
            val pick = rule.prefer.firstOrNull { id -> id in available && isCompatible(compat, id, gpu) }
            if (pick != null) return Selection(pick, rule.id)
        }
        return null
    }

    /**
     * Whether a renderer may run on this GPU according to its constraints.
     * Unknown GPU vendor / unknown Vulkan support never count as incompatible.
     */
    fun isCompatible(compat: RendererCompat, rendererId: String, gpu: GpuInfo): Boolean {
        val c = compat.renderers[rendererId] ?: return true
        if (c.gpuVendors.isNotEmpty() && gpu.vendor != GpuVendor.UNKNOWN && gpu.vendor !in c.gpuVendors) return false
        if (c.requiresVulkan && gpu.vulkanSupported == false) return false
        return true
    }

    private fun matches(
        c: RendererCompat.Condition,
        gpu: GpuInfo,
        hasShaderMod: Boolean,
        mcAtLeast: (String) -> Boolean
    ): Boolean {
        if (c.shaderMod != null && c.shaderMod != hasShaderMod) return false
        if (c.minMc != null && !mcAtLeast(c.minMc)) return false
        if (c.belowMc != null && mcAtLeast(c.belowMc)) return false
        if (c.gpuVendors.isNotEmpty() && gpu.vendor !in c.gpuVendors) return false
        if (c.minGpuModel != null && (gpu.model == null || gpu.model < c.minGpuModel)) return false
        return true
    }

    /** Mod file names that indicate a shader loader (Iris, Oculus, OptiFine, OptiFabric) */
    private val SHADER_MOD_NAMES = listOf("iris", "oculus", "optifine", "optifabric")

    /** Cheap name-based detection over the enabled files of the mods folder (no jar parsing) */
    fun hasShaderMod(modFileNames: Collection<String>): Boolean = modFileNames.any { name ->
        val lower = name.lowercase()
        !lower.endsWith(".disabled") && (lower.endsWith(".jar") || lower.endsWith(".zip")) &&
                SHADER_MOD_NAMES.any { lower.contains(it) }
    }
}
