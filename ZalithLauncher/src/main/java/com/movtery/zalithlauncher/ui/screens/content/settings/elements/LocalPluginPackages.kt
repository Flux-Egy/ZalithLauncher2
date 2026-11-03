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
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.PluginLoader
import com.movtery.zalithlauncher.game.plugin.local.LocalPluginStore
import com.movtery.zalithlauncher.game.plugin.local.PluginPackageInspector
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LocalPluginPackages"

private sealed interface ImportState {
    data object None : ImportState
    data class Confirm(val inspection: PluginPackageInspector.Inspection, val file: File) : ImportState
    data class Error(val message: String) : ImportState
    data class Manage(val packages: List<LocalPluginStore.Installed>) : ImportState
    data class ConfirmDelete(val installed: LocalPluginStore.Installed) : ImportState
}

/** ABI of this app process (a 32-bit build on an arm64 device loads armeabi-v7a libraries) */
private fun processAbi(): String =
    if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: Build.SUPPORTED_ABIS.first()
    else Build.SUPPORTED_32_BIT_ABIS.firstOrNull() ?: Build.SUPPORTED_ABIS.first()

/**
 * Settings cards to import drop-in renderer / Turnip driver packages (.zip) and manage them.
 */
@Composable
fun LocalPluginPackagesSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.None) }
    var installedCount by remember { mutableIntStateOf(0) }

    fun refreshCount() {
        scope.launch { installedCount = withContext(Dispatchers.IO) { LocalPluginStore.list(context).size } }
    }
    LaunchedEffect(Unit) { refreshCount() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            state = withContext(Dispatchers.IO) { inspectUri(context, uri) }
        }
    }

    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = CardPosition.Middle,
        title = stringResource(R.string.local_plugin_import_title),
        summary = stringResource(R.string.local_plugin_import_summary),
        onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
    )
    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = CardPosition.Middle,
        title = stringResource(R.string.local_plugin_installed_title, installedCount),
        summary = stringResource(R.string.local_plugin_installed_summary),
        onClick = {
            scope.launch {
                state = ImportState.Manage(withContext(Dispatchers.IO) { LocalPluginStore.list(context) })
            }
        }
    )

    when (val s = state) {
        ImportState.None -> {}
        is ImportState.Error -> SimpleAlertDialog(
            title = stringResource(R.string.generic_warning),
            text = stringResource(R.string.local_plugin_invalid, s.message),
            onDismiss = { state = ImportState.None }
        )
        is ImportState.Confirm -> {
            val inspection = s.inspection
            val typeText = stringResource(
                if (inspection.type == PluginPackageInspector.Type.RENDERER) R.string.local_plugin_type_renderer
                else R.string.local_plugin_type_driver
            )
            val libraries = inspection.libraries.joinToString("\n") { "• ${it.installName}\n  SHA-256 ${it.sha256}" }
            val warnings = inspection.warnings.filterIsInstance<PluginPackageInspector.Warning.Not16KbAligned>()
                .map { stringResource(R.string.local_plugin_warning_16k, it.library) }
            SimpleAlertDialog(
                title = stringResource(R.string.local_plugin_confirm_title, inspection.displayName),
                text = stringResource(R.string.local_plugin_confirm_text, typeText, inspection.abi, libraries) +
                        warnings.joinToString(separator = "") { "\n\n$it" },
                confirmText = stringResource(R.string.download_install),
                onConfirm = {
                    state = ImportState.None
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { LocalPluginStore.install(context, s.file, inspection) }
                                .also { s.file.delete() }
                        }
                        result.onSuccess {
                            PluginLoader.loadAllPlugins(context, force = true)
                            refreshCount()
                            Toast.makeText(
                                context,
                                context.getString(R.string.local_plugin_installed_ok, inspection.displayName),
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { e ->
                            Logger.error(TAG, "Install failed", e)
                            state = ImportState.Error(e.message ?: e.javaClass.simpleName)
                        }
                    }
                },
                onDismiss = {
                    s.file.delete()
                    state = ImportState.None
                }
            )
        }
        is ImportState.Manage -> {
            //joinToString 的 lambda 不是 inline，不能在其中调用 stringResource，先取出字符串
            val rendererLabel = stringResource(R.string.local_plugin_type_renderer)
            val driverLabel = stringResource(R.string.local_plugin_type_driver)
            val noneText = stringResource(R.string.local_plugin_none)
            val alignedText = stringResource(R.string.local_plugin_status_16k_ok)
            val notAlignedText = stringResource(R.string.local_plugin_status_16k_bad)
            //本机是否使用 16 KB 内存页（未对齐的库只在这种设备上无法加载）
            val pageSize = remember { runCatching { android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) }.getOrDefault(4096L) }
            val pageText = stringResource(R.string.local_plugin_device_page_size, pageSize / 1024)
            val text = if (s.packages.isEmpty()) {
                noneText
            } else {
                pageText + "\n" + s.packages.joinToString("\n") { p ->
                    val type = if (p.record.type == PluginPackageInspector.Type.RENDERER) rendererLabel else driverLabel
                    val aligned = if (p.record.libraries.all { it.aligned16k }) alignedText else notAlignedText
                    "• ${p.record.name} ($type) · $aligned"
                }
            }
            SimpleAlertDialog(
                title = stringResource(R.string.local_plugin_installed_title, s.packages.size),
                text = text,
                confirmText = stringResource(R.string.local_plugin_delete_last),
                onConfirm = {
                    // delete the most recently imported package, one at a time, with confirmation
                    s.packages.maxByOrNull { it.record.importedAt }?.let { state = ImportState.ConfirmDelete(it) }
                        ?: run { state = ImportState.None }
                },
                onDismiss = { state = ImportState.None }
            )
        }
        is ImportState.ConfirmDelete -> SimpleAlertDialog(
            title = stringResource(R.string.generic_warning),
            text = stringResource(R.string.local_plugin_delete_confirm, s.installed.record.name),
            onConfirm = {
                state = ImportState.None
                scope.launch {
                    withContext(Dispatchers.IO) { LocalPluginStore.delete(s.installed) }
                    PluginLoader.loadAllPlugins(context, force = true)
                    refreshCount()
                }
            },
            onDismiss = { state = ImportState.None }
        )
    }
}

/** Copies the picked document to cache and inspects it (off the main thread) */
private fun inspectUri(context: Context, uri: Uri): ImportState {
    val file = File(context.cacheDir, "local_plugin_import_${System.nanoTime()}.zip")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return ImportState.Error("Cannot read the selected file")
        val inspection = PluginPackageInspector.inspect(file, processAbi(), Build.VERSION.SDK_INT)
        ImportState.Confirm(inspection, file)
    } catch (e: PluginPackageInspector.InvalidPackageException) {
        file.delete()
        ImportState.Error(e.message ?: "Invalid package")
    } catch (e: Exception) {
        file.delete()
        Logger.error(TAG, "Failed to inspect package", e)
        ImportState.Error(e.message ?: e.javaClass.simpleName)
    }
}
