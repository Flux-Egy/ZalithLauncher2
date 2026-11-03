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

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.advisor.SmartAdvisor
import com.movtery.zalithlauncher.game.advisor.RecommendationsRepository
import com.movtery.zalithlauncher.game.advisor.HardwareProfiler
import android.content.Context
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

/**
 * The upscaler suggested for this device at the given render scale:
 * Snapdragon GSR on Adreno (Qualcomm's own mobile upscaler), FSR 1 on Mali, PowerVR,
 * Xclipse and unknown GPUs; CAS sharpening when nothing is upscaled.
 */
fun recommendedUpscaler(renderScalePercent: Int, vendor: GpuVendor? = GpuDetector.cached()?.vendor): GameUpscaler = when {
    !GameUpscalerEffect.isSupported() -> GameUpscaler.OFF
    renderScalePercent >= 100 -> GameUpscaler.CAS
    vendor == GpuVendor.ADRENO -> GameUpscaler.SGSR
    else -> GameUpscaler.FSR1
}

/**
 * Upscaler choice (GPU-aware labels), CAS sharpness and the performance preset.
 * Placed below the resolution scale in the renderer settings.
 */
@Composable
fun UpscalerSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                    GameUpscaler.FSR1 -> R.string.settings_upscaler_fsr1
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
                        GameUpscaler.FSR1 -> R.string.settings_upscaler_fsr1_desc
                    }
                ),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    )

    if (supported && (AllSettings.gameUpscaler.state == GameUpscaler.CAS || AllSettings.gameUpscaler.state == GameUpscaler.FSR1)) {
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
            scope.launch {
                val result = withContext(Dispatchers.IO) { applyPerformancePreset(context) }
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.settings_performance_preset_applied_detail,
                        result.ramMb / 1024f,
                        result.scalePercent,
                        result.upscaler.name
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )
}

data class PresetResult(val ramMb: Int, val scalePercent: Int, val upscaler: GameUpscaler)

/**
 * One-tap "best settings": game memory from the Smart Advisor (never above half of the
 * device RAM), tuned G1 GC flags with the RAM safety limit on, 50 % render scale and the
 * upscaler recommended for this GPU (Snapdragon GSR on Adreno, FSR 1 elsewhere).
 * Every value stays individually adjustable afterwards. Call off the main thread.
 *
 * Per-instance memory settings, if an instance has them, still take precedence.
 */
fun applyPerformancePreset(context: Context): PresetResult {
    val hw = HardwareProfiler.snapshot(context)
    val matrix = RecommendationsRepository.load(context)
    val modCount = VersionsManager.currentVersion.value
        ?.let { File(it.getGameDir(), "mods").list()?.count { name -> name.endsWith(".jar", true) } } ?: 0
    val ram = SmartAdvisor.recommendedRam(hw, matrix, modCount)
    val scale = 50
    val upscaler = recommendedUpscaler(scale)
    AllSettings.ramAllocation.save(ram)
    AllSettings.ramSafetyLimit.save(true)
    AllSettings.jvmGcTuning.save(true)
    AllSettings.resolutionRule.save(ResolutionRule.PERCENTAGE)
    AllSettings.resolutionRatio.save(scale)
    AllSettings.gameUpscaler.save(upscaler)
    return PresetResult(ram, scale, upscaler)
}
