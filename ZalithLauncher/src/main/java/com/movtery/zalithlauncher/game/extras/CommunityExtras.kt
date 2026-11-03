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

package com.movtery.zalithlauncher.game.extras

import com.movtery.zalithlauncher.game.renderer.auto.RendererCompat
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.theme.style.CustomThemeCodec
import com.movtery.zalithlauncher.ui.theme.style.CustomThemeSpec
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

/**
 * Power-user drop-in folder: <external files>/Extras
 *
 * - renderer_rules/ : JSON files in the renderer_compat.json format. Their rules are
 *   evaluated before the bundled ones (they cannot remove bundled safety constraints).
 * - themes/         : custom theme files (Settings > Launcher > Custom theme > Export),
 *   listed in the custom theme settings for one-tap use.
 *
 * Files are data only (no code). Each is size-limited and parsed strictly; a broken file
 * is skipped with a log line and never prevents the launcher from starting.
 */
object CommunityExtras {
    private const val TAG = "CommunityExtras"
    private const val MAX_FILE_BYTES = 64 * 1024L
    private const val MAX_FILES = 32

    const val RENDERER_RULES = "renderer_rules"
    const val THEMES = "themes"

    private const val README = """Zalith Launcher - Extras

renderer_rules/  *.json in the renderer_compat.json format (schemaVersion 1).
                 Rules here are checked before the built-in rules. Only renderers
                 that are installed can be selected.
themes/          Custom theme files exported from Settings > Launcher > Custom theme.

Files are plain data. Invalid files are ignored (see the launcher log).
"""

    fun root(): File = File(PathManager.DIR_FILES_EXTERNAL, "Extras")

    /** Creates the folder layout and README (idempotent). Call off the main thread. */
    fun ensureLayout() {
        runCatching {
            File(root(), RENDERER_RULES).mkdirs()
            File(root(), THEMES).mkdirs()
            val readme = File(root(), "README.txt")
            if (!readme.exists()) readme.writeText(README)
        }.onFailure { Logger.warning(TAG, "Cannot create the Extras folder", it) }
    }

    private fun jsonFiles(sub: String): List<File> =
        File(root(), sub).listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.take(MAX_FILES)
            .orEmpty()

    private fun readSmall(file: File): String? {
        if (file.length() > MAX_FILE_BYTES) {
            Logger.warning(TAG, "Ignoring ${file.name}: larger than $MAX_FILE_BYTES bytes")
            return null
        }
        return runCatching { file.readText() }.getOrNull()
    }

    /** Valid renderer rule files, as (file name, rules) */
    fun rendererRules(): List<Pair<String, RendererCompat>> = jsonFiles(RENDERER_RULES).mapNotNull { file ->
        val text = readSmall(file) ?: return@mapNotNull null
        runCatching { file.name to RendererCompat.parse(text) }
            .onFailure { Logger.warning(TAG, "Ignoring renderer rules ${file.name}: ${it.message}") }
            .getOrNull()
    }

    /** Valid theme files, as (file name, theme) */
    fun themes(validLayouts: Set<String>, validPaletteStyles: Set<String>): List<Pair<String, CustomThemeSpec>> =
        jsonFiles(THEMES).mapNotNull { file ->
            val text = readSmall(file) ?: return@mapNotNull null
            runCatching { file.name to CustomThemeCodec.decode(text, validLayouts, validPaletteStyles) }
                .onFailure { Logger.warning(TAG, "Ignoring theme ${file.name}: ${it.message}") }
                .getOrNull()
        }
}
