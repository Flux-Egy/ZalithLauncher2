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

package com.movtery.zalithlauncher.ui.screens.content.settings.elements

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.movtery.colorpicker.rememberColorPickerController
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.DarkMode
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.ui.components.ColorPickerDialog
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.IntSliderSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.ListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.theme.style.CustomThemeCodec
import com.movtery.zalithlauncher.ui.theme.style.CustomThemeSpec
import com.movtery.zalithlauncher.ui.theme.style.LauncherStyle
import com.movtery.zalithlauncher.ui.theme.style.ThemeFont
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CustomThemeSettings"

/** Localized name of a launcher style */
@Composable
fun LauncherStyle.displayName(): String = stringResource(
    when (this) {
        LauncherStyle.ZALITH_DEFAULT -> R.string.settings_launcher_style_default
        LauncherStyle.PC_LAUNCHER -> R.string.settings_launcher_style_pc
        LauncherStyle.MODRINTH_STYLE -> R.string.settings_launcher_style_modrinth
        LauncherStyle.CONSOLE -> R.string.settings_launcher_style_console
        LauncherStyle.MATERIAL_YOU -> R.string.settings_launcher_style_material_you
        LauncherStyle.GLASS -> R.string.settings_launcher_style_glass
        LauncherStyle.RETRO_PIXEL -> R.string.settings_launcher_style_retro
        LauncherStyle.CUSTOM -> R.string.settings_launcher_style_custom
    }
)

@Composable
private fun ThemeFont.displayName(): String = stringResource(
    when (this) {
        ThemeFont.DEFAULT -> R.string.custom_theme_font_default
        ThemeFont.SANS_SERIF -> R.string.custom_theme_font_sans
        ThemeFont.SERIF -> R.string.custom_theme_font_serif
        ThemeFont.MONOSPACE -> R.string.custom_theme_font_mono
        ThemeFont.CURSIVE -> R.string.custom_theme_font_cursive
        ThemeFont.PIXEL -> R.string.custom_theme_font_pixel
    }
)

/** Snapshot of the current custom theme settings */
private fun currentSpec(): CustomThemeSpec = CustomThemeSpec(
    name = AllSettings.customThemeName.getValue().ifBlank { "Custom" },
    layout = AllSettings.customThemeLayout.getValue().name,
    primary = AllSettings.launcherCustomColor.getValue(),
    secondary = AllSettings.customThemeSecondary.getValue().takeIf { it != 0 },
    paletteStyle = AllSettings.launcherCustomPaletteStyle.getValue().name,
    cornerRadius = AllSettings.customThemeCornerRadius.getValue(),
    font = AllSettings.customThemeFont.getValue(),
    darkMode = AllSettings.launcherDarkMode.getValue().name,
    backgroundOpacity = AllSettings.launcherBackgroundOpacity.getValue(),
    backgroundBlur = AllSettings.backgroundBlur.getValue()
)

/** Applies an imported theme and switches to the custom style */
private fun applySpec(spec: CustomThemeSpec) {
    AllSettings.customThemeName.save(spec.name)
    AllSettings.customThemeLayout.save(LauncherStyle.valueOf(spec.layout))
    AllSettings.launcherCustomColor.save(spec.primary)
    AllSettings.customThemeSecondary.save(spec.secondary ?: 0)
    AllSettings.launcherCustomPaletteStyle.save(PaletteStyle.valueOf(spec.paletteStyle))
    AllSettings.customThemeCornerRadius.save(spec.cornerRadius)
    AllSettings.customThemeFont.save(spec.font)
    AllSettings.launcherDarkMode.save(DarkMode.valueOf(spec.darkMode))
    AllSettings.launcherBackgroundOpacity.save(spec.backgroundOpacity)
    AllSettings.backgroundBlur.save(spec.backgroundBlur)
    AllSettings.launcherStyle.save(LauncherStyle.CUSTOM)
}

private fun readLimited(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
    return input.use {
        //限制读取量（readNBytes 需要 API 33，这里手动读取）
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = it.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > CustomThemeCodec.MAX_BYTES) throw CustomThemeCodec.InvalidThemeException("Theme file is too large")
        }
        String(out.toByteArray(), Charsets.UTF_8)
    }
}

/** Layout, colors, corner radius, font and JSON import/export of the custom theme */
@Composable
fun CustomThemeSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editPrimary by remember { mutableStateOf(false) }
    var editSecondary by remember { mutableStateOf(false) }

    ListSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.customThemeLayout,
        items = LauncherStyle.entries.filter { it != LauncherStyle.CUSTOM },
        title = stringResource(R.string.custom_theme_layout),
        getItemText = { it.displayName() }
    )

    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.custom_theme_primary),
        summary = CustomThemeCodec.colorToHex(AllSettings.launcherCustomColor.state),
        onClick = { editPrimary = true },
        trailingIcon = { ColorSwatch(Color(AllSettings.launcherCustomColor.state)) }
    )

    val secondary = AllSettings.customThemeSecondary.state
    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.custom_theme_secondary),
        summary = if (secondary == 0) stringResource(R.string.custom_theme_secondary_auto)
        else CustomThemeCodec.colorToHex(secondary) + " · " + stringResource(R.string.custom_theme_secondary_reset_hint),
        onClick = { editSecondary = true },
        trailingIcon = {
            if (secondary != 0) ColorSwatch(Color(secondary))
        }
    )

    ListSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.launcherCustomPaletteStyle,
        items = PaletteStyle.entries,
        title = stringResource(R.string.custom_theme_palette),
        getItemText = { it.name }
    )

    IntSliderSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.customThemeCornerRadius,
        title = stringResource(R.string.custom_theme_corner_radius),
        valueRange = AllSettings.customThemeCornerRadius.floatRange,
        suffix = "dp"
    )

    ListSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.customThemeFont,
        items = ThemeFont.entries,
        title = stringResource(R.string.custom_theme_font),
        getItemText = { it.displayName() }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(CustomThemeCodec.encode(currentSpec()).toByteArray(Charsets.UTF_8))
                    }
                }.onFailure { Logger.warning(TAG, "Theme export failed", it) }.isSuccess
            }
            Toast.makeText(
                context,
                if (ok) R.string.custom_theme_export_done else R.string.custom_theme_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.custom_theme_export),
        summary = stringResource(R.string.custom_theme_export_summary),
        onClick = {
            val name = AllSettings.customThemeName.getValue().ifBlank { "zalith-theme" }
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            exportLauncher.launch("$name.json")
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CustomThemeCodec.decode(
                        text = readLimited(context, uri),
                        validLayouts = LauncherStyle.entries.filter { it != LauncherStyle.CUSTOM }.map { it.name }.toSet(),
                        validPaletteStyles = PaletteStyle.entries.map { it.name }.toSet()
                    )
                }
            }
            result.onSuccess { spec ->
                applySpec(spec)
                Toast.makeText(context, context.getString(R.string.custom_theme_import_done, spec.name), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Logger.warning(TAG, "Theme import failed", e)
                Toast.makeText(context, context.getString(R.string.custom_theme_import_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            }
        }
    }
    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.custom_theme_import),
        summary = stringResource(R.string.custom_theme_import_summary),
        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    )

    if (editPrimary) {
        val controller = rememberColorPickerController(initialColor = Color(AllSettings.launcherCustomColor.getValue()))
        ColorPickerDialog(
            colorController = controller,
            showAlpha = false,
            onCancel = { editPrimary = false },
            onConfirm = { color ->
                AllSettings.launcherCustomColor.save(color.copy(alpha = 1f).toArgb())
                editPrimary = false
            }
        )
    }
    if (editSecondary) {
        val initial = AllSettings.customThemeSecondary.getValue().takeIf { it != 0 }
            ?: AllSettings.launcherCustomColor.getValue()
        val controller = rememberColorPickerController(initialColor = Color(initial))
        ColorPickerDialog(
            colorController = controller,
            showAlpha = false,
            //取消即恢复为“由主色推导”
            onCancel = {
                AllSettings.customThemeSecondary.save(0)
                editSecondary = false
            },
            onConfirm = { color ->
                //0 保留为“自动”，不透明色不会等于 0
                AllSettings.customThemeSecondary.save(color.copy(alpha = 1f).toArgb())
                editSecondary = false
            }
        )
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}
