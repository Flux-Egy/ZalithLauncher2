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

enum class GpuVendor {
    /** Qualcomm Adreno */
    ADRENO,

    /** ARM Mali / Immortalis */
    MALI,

    /** Imagination PowerVR */
    POWERVR,

    /** Samsung Xclipse (AMD RDNA based) */
    XCLIPSE,

    OTHER,
    UNKNOWN
}

/**
 * GPU identity as reported by GLES (GL_VENDOR / GL_RENDERER).
 * @param model numeric model, e.g. 740 for "Adreno (TM) 740", 715 for "Mali-G715"; null if unknown
 * @param vulkanSupported Vulkan 1.1+ hardware support, null if unknown
 */
data class GpuInfo(
    val vendor: GpuVendor,
    val model: Int?,
    val glVendor: String = "",
    val glRenderer: String = "",
    val vulkanSupported: Boolean? = null
) {
    companion object {
        val UNKNOWN = GpuInfo(GpuVendor.UNKNOWN, null)

        private val ADRENO_MODEL = Regex("""adreno[^0-9]*(\d{3,4})""", RegexOption.IGNORE_CASE)
        private val MALI_MODEL = Regex("""(?:mali|immortalis)-?[gt]?(\d{2,4})""", RegexOption.IGNORE_CASE)
        private val POWERVR_MODEL = Regex("""(?:ge|gm|gx|bxm|dxt)[-]?(\d{3,4})""", RegexOption.IGNORE_CASE)
        private val XCLIPSE_MODEL = Regex("""xclipse\s*(\d{3,4})""", RegexOption.IGNORE_CASE)

        /** Pure parser for GL_VENDOR / GL_RENDERER strings (unit tested) */
        fun fromGlStrings(glVendor: String?, glRenderer: String?, vulkanSupported: Boolean? = null): GpuInfo {
            val vendor = glVendor.orEmpty()
            val renderer = glRenderer.orEmpty()
            val both = "$vendor $renderer"
            fun model(regex: Regex) = regex.find(renderer)?.groupValues?.get(1)?.toIntOrNull()

            val (gpuVendor, model) = when {
                both.contains("adreno", ignoreCase = true) -> GpuVendor.ADRENO to model(ADRENO_MODEL)
                both.contains("mali", ignoreCase = true) || both.contains("immortalis", ignoreCase = true) ->
                    GpuVendor.MALI to model(MALI_MODEL)
                both.contains("powervr", ignoreCase = true) || both.contains("imagination", ignoreCase = true) ->
                    GpuVendor.POWERVR to model(POWERVR_MODEL)
                both.contains("xclipse", ignoreCase = true) -> GpuVendor.XCLIPSE to model(XCLIPSE_MODEL)
                vendor.isBlank() && renderer.isBlank() -> GpuVendor.UNKNOWN to null
                else -> GpuVendor.OTHER to null
            }
            return GpuInfo(gpuVendor, model, vendor, renderer, vulkanSupported)
        }
    }
}
