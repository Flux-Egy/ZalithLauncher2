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

import com.movtery.zalithlauncher.game.extras.CommunityExtras
import com.movtery.zalithlauncher.context.GlobalContext
import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.installed.utils.isBiggerOrEqualVer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "RendererResolver"

/**
 * Decides which renderer a launch uses. Deterministic for the same inputs, so the launcher
 * process and the :game process resolve the same renderer independently:
 * - an explicit choice (per-version or global setting) is always respected;
 * - otherwise, with auto-selection enabled, [RendererAutoSelector] applies
 *   assets/renderer_compat.json using the cached GPU, the Minecraft version and shader mods;
 * - if anything is missing, the previous behaviour applies (Renderers falls back to its default).
 */
object RendererResolver {
    private const val COMPAT_ASSET = "renderer_compat.json"

    val compat: RendererCompat? by lazy {
        runCatching {
            val bundled = GlobalContext.assets.open(COMPAT_ASSET).use { RendererCompat.parse(it.readBytes().toString(Charsets.UTF_8)) }
            //用户在 Extras/renderer_rules 放置的规则优先于内置规则（两个进程读取相同文件，结果一致）
            val extras = runCatching { CommunityExtras.rendererRules() }.getOrDefault(emptyList())
            if (extras.isNotEmpty()) Logger.info(TAG, "Renderer rules from Extras: ${extras.joinToString { it.first }}")
            bundled.withExtras(extras)
        }.onFailure { Logger.error(TAG, "Failed to load $COMPAT_ASSET", it) }.getOrNull()
    }

    /** @return the renderer unique identifier to pass to [Renderers.setCurrentRenderer] */
    fun resolveRendererId(version: Version): String {
        val chosen = version.getRenderer()
        if (chosen.isNotBlank()) return chosen
        if (!AllSettings.rendererAutoSelect.getValue()) return chosen

        val compat = compat ?: return chosen
        val mcVersion = version.getVersionInfo()?.minecraftVersion ?: return chosen
        val gpu = GpuDetector.cached() ?: GpuInfo.UNKNOWN
        val shaders = hasShaderMod(version)

        // keep the first renderer registered for each id (built-ins are registered first)
        val byId = LinkedHashMap<String, RendererInterface>()
        Renderers.getRenderers().forEach { byId.putIfAbsent(it.getRendererId(), it) }

        val selection = RendererAutoSelector.select(
            compat = compat,
            gpu = gpu,
            hasShaderMod = shaders,
            mcAtLeast = { mcVersion.isBiggerOrEqualVer(it) },
            available = byId.keys
        ) ?: return chosen

        val renderer = byId.getValue(selection.rendererId)
        Logger.info(
            TAG,
            "Auto-selected ${renderer.getRendererName()} (${selection.rendererId}) by rule '${selection.ruleId}' " +
                    "for MC $mcVersion, GPU ${gpu.vendor} ${gpu.model ?: ""}, shaders=$shaders"
        )
        return renderer.getUniqueIdentifier()
    }

    /**
     * True if [renderer] was explicitly chosen and its declared hardware constraints rule out
     * the detected GPU (e.g. Freedreno on Mali). Unknown GPU never counts as a mismatch.
     */
    fun isHardwareMismatch(version: Version, renderer: RendererInterface): Boolean {
        if (version.getRenderer().isBlank()) return false
        val compat = compat ?: return false
        val gpu = GpuDetector.cached() ?: return false
        return !RendererAutoSelector.isCompatible(compat, renderer.getRendererId(), gpu)
    }

    private fun hasShaderMod(version: Version): Boolean {
        if (version.getVersionInfo()?.hasLoader(ModLoader.OPTIFINE) == true) return true
        val names = runCatching { VersionFolders.MOD.getDir(version.getGameDir()).list()?.toList() }
            .getOrNull().orEmpty()
        return RendererAutoSelector.hasShaderMod(names)
    }
}
