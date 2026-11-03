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

package com.movtery.zalithlauncher.ui.theme.style

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Font choices for the custom theme */
enum class ThemeFont { DEFAULT, SANS_SERIF, SERIF, MONOSPACE, CURSIVE, PIXEL }

/**
 * A shareable custom theme. Enum-like fields are kept as names so this class stays
 * free of UI dependencies; [CustomThemeCodec.decode] only returns valid names.
 *
 * The background image/video itself is not part of the file (it can be large and is
 * stored per device); its opacity and blur are.
 */
data class CustomThemeSpec(
    val name: String = "Custom",
    /** A [LauncherStyle] name other than CUSTOM */
    val layout: String = "ZALITH_DEFAULT",
    /** ARGB */
    val primary: Int = 0xFF3C8527.toInt(),
    /** ARGB, or null to derive it from [primary] */
    val secondary: Int? = null,
    /** com.materialkolor.PaletteStyle name */
    val paletteStyle: String = "TonalSpot",
    val cornerRadius: Int = 12,
    val font: ThemeFont = ThemeFont.DEFAULT,
    /** DarkMode name: Enable / Disable / FollowSystem */
    val darkMode: String = "FollowSystem",
    val backgroundOpacity: Int = 80,
    val backgroundBlur: Int = 0
)

/**
 * JSON import/export for [CustomThemeSpec].
 *
 * Imports are untrusted (shared between users), so decoding is strict about structure
 * and lenient about values: unknown fields are ignored, out-of-range numbers are
 * clamped, and unknown enum names fall back to defaults.
 */
object CustomThemeCodec {
    const val FORMAT = "zalith-theme"
    const val VERSION = 1

    /** Larger files are rejected before parsing */
    const val MAX_BYTES = 64 * 1024

    val CORNER_RADIUS_RANGE = 0..32
    val OPACITY_RANGE = 20..100
    val BLUR_RANGE = 0..40
    private const val MAX_NAME_LENGTH = 64

    class InvalidThemeException(message: String) : Exception(message)

    fun encode(spec: CustomThemeSpec): String {
        val json = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            addProperty("name", spec.name)
            addProperty("layout", spec.layout)
            addProperty("primary", colorToHex(spec.primary))
            spec.secondary?.let { addProperty("secondary", colorToHex(it)) }
            addProperty("paletteStyle", spec.paletteStyle)
            addProperty("cornerRadius", spec.cornerRadius)
            addProperty("font", spec.font.name)
            addProperty("darkMode", spec.darkMode)
            addProperty("backgroundOpacity", spec.backgroundOpacity)
            addProperty("backgroundBlur", spec.backgroundBlur)
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(json)
    }

    /**
     * @param validLayouts accepted layout names (LauncherStyle entries except CUSTOM)
     * @param validPaletteStyles accepted palette style names
     * @throws InvalidThemeException if this is not a Zalith theme file
     */
    fun decode(
        text: String,
        validLayouts: Set<String>,
        validPaletteStyles: Set<String>
    ): CustomThemeSpec {
        if (text.length > MAX_BYTES) throw InvalidThemeException("Theme file is too large")
        val root = runCatching { JsonParser.parseString(text) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw InvalidThemeException("Not a JSON object")
        if (root.str("format") != FORMAT) throw InvalidThemeException("Not a Zalith theme file")
        val version = root.int("version") ?: throw InvalidThemeException("Missing version")
        if (version > VERSION) throw InvalidThemeException("Theme file version $version is newer than supported ($VERSION)")

        val defaults = CustomThemeSpec()
        val primary = root.str("primary")?.let { parseColor(it) }
            ?: throw InvalidThemeException("Missing or invalid primary color")
        return CustomThemeSpec(
            name = root.str("name")?.filter { !it.isISOControl() }?.trim()?.take(MAX_NAME_LENGTH)
                ?.takeIf { it.isNotEmpty() } ?: defaults.name,
            layout = root.str("layout")?.takeIf { it in validLayouts } ?: defaults.layout,
            primary = primary,
            secondary = root.str("secondary")?.let { parseColor(it) },
            paletteStyle = root.str("paletteStyle")?.takeIf { it in validPaletteStyles } ?: defaults.paletteStyle,
            cornerRadius = (root.int("cornerRadius") ?: defaults.cornerRadius).coerceIn(CORNER_RADIUS_RANGE),
            font = root.str("font")?.let { name -> ThemeFont.entries.firstOrNull { it.name == name } } ?: defaults.font,
            darkMode = root.str("darkMode")?.takeIf { it in DARK_MODES } ?: defaults.darkMode,
            backgroundOpacity = (root.int("backgroundOpacity") ?: defaults.backgroundOpacity).coerceIn(OPACITY_RANGE),
            backgroundBlur = (root.int("backgroundBlur") ?: defaults.backgroundBlur).coerceIn(BLUR_RANGE)
        )
    }

    private val DARK_MODES = setOf("Enable", "Disable", "FollowSystem")

    /** "#RRGGBB" (opaque) or "#AARRGGBB"; null if malformed */
    fun parseColor(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val value = hex.toLong(16)
        return if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    fun colorToHex(argb: Int): String {
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb.toLong() and 0xFFFFFFFFL)
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { e: JsonElement ->
            e.asDouble.takeIf { it.isFinite() }?.toInt()
        }
}
