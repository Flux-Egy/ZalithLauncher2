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

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.renderer.auto.GpuDetector
import com.movtery.zalithlauncher.game.renderer.auto.GpuVendor
import com.movtery.zalithlauncher.game.renderer.upscale.GameUpscaler
import com.movtery.zalithlauncher.game.renderer.upscale.GameUpscalerEffect
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.ResolutionRule
import com.movtery.zalithlauncher.setting.unit.floatRange
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.IntSliderSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.ListSettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard

/** The upscaler suggested for this device at the given render scale */
fun recommendedUpscaler(renderScalePercent: Int): GameUpscaler = when {
    !GameUpscalerEffect.isSupported() -> GameUpscaler.OFF
    // SGSR reconstructs from the low-res image; it is plain shader math and runs on any GPU
    renderScalePercent < 100 -> GameUpscaler.SGSR
    else -> GameUpscaler.CAS
}

/**
 * Upscaler choice (GPU-aware labels), CAS sharpness and the performance preset.
 * Placed below the resolution scale in the renderer settings.
 */
@Composable
fun UpscalerSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val supported = remember { GameUpscalerEffect.isSupported() }
    val vendor = remember { GpuDetector.cached()?.vendor }
    val scale = if (AllSettings.resolutionRule.state == ResolutionRule.PERCENTAGE) AllSettings.resolutionRatio.state else 100
    val recommended = recommendedUpscaler(scale)

    val gpuName = when (vendor) {
        GpuVendor.ADRENO -> "Adreno"
        GpuVendor.MALI -> "Mali"
        GpuVendor.POWERVR -> "PowerVR"
        GpuVendor.XCLIPSE -> "Xclipse"
        else -> null
    }

    ListSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.gameUpscaler,
        items = GameUpscaler.entries,
        title = stringResource(R.string.settings_upscaler_title),
        summary = if (supported) {
            stringResource(R.string.settings_upscaler_summary)
        } else {
            stringResource(R.string.settings_upscaler_unsupported)
        },
        enabled = supported,
        getItemText = { upscaler ->
            val name = stringResource(
                when (upscaler) {
                    GameUpscaler.OFF -> R.string.settings_upscaler_off
                    GameUpscaler.SGSR -> R.string.settings_upscaler_sgsr
                    GameUpscaler.CAS -> R.string.settings_upscaler_cas
                }
            )
            if (upscaler == recommended && upscaler != GameUpscaler.OFF) {
                name + " · " + if (gpuName != null) {
                    stringResource(R.string.settings_upscaler_recommended_for, gpuName)
                } else {
                    stringResource(R.string.settings_upscaler_recommended)
                }
            } else name
        },
        getItemSummary = { upscaler ->
            androidx.compose.material3.Text(
                text = stringResource(
                    when (upscaler) {
                        GameUpscaler.OFF -> R.string.settings_upscaler_off_desc
                        GameUpscaler.SGSR -> R.string.settings_upscaler_sgsr_desc
                        GameUpscaler.CAS -> R.string.settings_upscaler_cas_desc
                    }
                ),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    )

    if (supported && AllSettings.gameUpscaler.state == GameUpscaler.CAS) {
        IntSliderSettingsCard(
            modifier = modifier,
            position = CardPosition.Middle,
            unit = AllSettings.gameUpscalerSharpness,
            title = stringResource(R.string.settings_upscaler_sharpness),
            valueRange = AllSettings.gameUpscalerSharpness.floatRange,
            suffix = "%"
        )
    }

    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.settings_performance_preset_title),
        summary = stringResource(R.string.settings_performance_preset_summary),
        onClick = {
            applyPerformancePreset()
            Toast.makeText(context, R.string.settings_performance_preset_applied, Toast.LENGTH_SHORT).show()
        }
    )
}

/**
 * One-tap preset: render at 50 % of the screen resolution, reconstruct with the
 * recommended upscaler, and keep the tuned G1 GC flags on. Every value stays
 * individually adjustable afterwards.
 */
fun applyPerformancePreset() {
    AllSettings.resolutionRule.save(ResolutionRule.PERCENTAGE)
    AllSettings.resolutionRatio.save(50)
    AllSettings.gameUpscaler.save(recommendedUpscaler(50))
    AllSettings.jvmGcTuning.save(true)
}
