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

package com.movtery.zalithlauncher.ui.screens.content.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.advisor.Advice
import com.movtery.zalithlauncher.game.advisor.FpsRecord
import com.movtery.zalithlauncher.game.advisor.FpsSessionStore
import com.movtery.zalithlauncher.game.advisor.HardwareProfiler
import com.movtery.zalithlauncher.game.advisor.HardwareSnapshot
import com.movtery.zalithlauncher.game.advisor.RecommendationsRepository
import com.movtery.zalithlauncher.game.advisor.SmartAdvisor
import com.movtery.zalithlauncher.game.renderer.auto.RendererAutoSelector
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class AdvisorState(
    val hw: HardwareSnapshot,
    val advice: List<Advice>,
    val versionName: String?,
    val fps: FpsRecord?
)

/** Smart Advisor home card (hideable; re-enable in Settings > Launcher) */
@Composable
fun SmartAdvisorCard() {
    val context = LocalContext.current
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()

    val state by produceState<AdvisorState?>(null, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                RecommendationsRepository.refreshIfStale(context)
                val hw = HardwareProfiler.snapshot(context)
                val matrix = RecommendationsRepository.load(context)
                val v = version
                val modNames = v?.let { File(it.getGameDir(), "mods").list()?.toList() }.orEmpty()
                val modCount = modNames.count { it.endsWith(".jar", ignoreCase = true) }
                AdvisorState(
                    hw = hw,
                    advice = SmartAdvisor.advise(
                        hw = hw,
                        matrix = matrix,
                        modCount = modCount,
                        currentRamMb = v?.getRamAllocation(context),
                        hasShaderMod = RendererAutoSelector.hasShaderMod(modNames)
                    ),
                    versionName = v?.getVersionName(),
                    fps = v?.let { FpsSessionStore.load(context, it.getVersionName()) }
                )
            }.getOrNull()
        }
    }
    val s = state ?: return

    BackgroundCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.advisor_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { AllSettings.showSmartAdvisor.save(false) }) {
                    Text(text = stringResource(R.string.advisor_hide))
                }
            }
            Text(
                modifier = Modifier.alpha(0.8f),
                text = buildString {
                    val gpu = s.hw.gpuVendor.lowercase().replaceFirstChar { it.uppercase() }
                    append(gpu)
                    s.hw.gpuModel?.let { append(' ').append(it) }
                    s.hw.socModel?.let { append(" (").append(it).append(')') }
                    append(" · ").append(s.hw.totalRamMb / 1024).append(" GB RAM · ")
                    append(s.hw.cpuCores).append(" CPU")
                },
                style = MaterialTheme.typography.labelMedium
            )

            // measured FPS of the selected instance
            Text(
                text = s.fps?.let {
                    stringResource(R.string.advisor_fps_measured, it.median, it.low, it.config)
                } ?: if (s.versionName != null) stringResource(R.string.advisor_fps_none) else "",
                style = MaterialTheme.typography.bodyMedium
            )

            s.advice.forEach { advice ->
                val text = when (advice) {
                    is Advice.Ram -> {
                        val rec = formatMb(advice.recommendedMb)
                        advice.currentMb?.let { stringResource(R.string.advisor_ram_with_current, rec, formatMb(it)) }
                            ?: stringResource(R.string.advisor_ram, rec)
                    }
                    is Advice.Shaders -> stringResource(R.string.advisor_shaders, advice.names.joinToString())
                    is Advice.GpuNote -> advice.text
                    is Advice.DriverNote -> advice.text
                    Advice.ShadersRisky -> stringResource(R.string.advisor_shaders_risky)
                }
                Text(text = "• $text", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatMb(mb: Int): String =
    if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else if (mb >= 1024) "%.1f GB".format(mb / 1024f) else "$mb MB"
