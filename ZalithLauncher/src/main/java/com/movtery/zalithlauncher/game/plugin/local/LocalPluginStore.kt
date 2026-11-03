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

package com.movtery.zalithlauncher.game.plugin.local

import android.content.Context
import androidx.annotation.WorkerThread
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.plugin.driver.Driver
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPlugin
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPluginManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "LocalPluginStore"

/**
 * Drop-in plugin packages imported from .zip files (see [PluginPackageInspector]).
 *
 * Layout: filesDir/local_plugins/{renderers|drivers}/<id>/
 *   - the package's libraries (only the device ABI)
 *   - package.json   (our own record of what was installed)
 *   - drivers additionally get a copy of the launcher's liblinkerhook.so, because the Turnip
 *     loader creates a linker namespace rooted at DRIVER_PATH and dlopens it from there.
 *
 * Imported libraries are native code that runs in the game process (same trust as mods);
 * the UI shows name, ABI and SHA-256 and asks for explicit consent before [install].
 */
object LocalPluginStore {
    private const val RECORD = "package.json"
    private const val LINKER_HOOK = "liblinkerhook.so"

    data class Record(
        val type: PluginPackageInspector.Type,
        val id: String,
        val name: String,
        val renderer: PluginPackageInspector.RendererManifest? = null,
        val driver: PluginPackageInspector.DriverManifest? = null,
        val libraries: List<RecordLibrary> = emptyList(),
        val importedAt: Long = System.currentTimeMillis()
    )

    data class RecordLibrary(val name: String, val sha256: String, val aligned16k: Boolean)

    data class Installed(val record: Record, val dir: File)

    private fun root(context: Context) = File(context.filesDir, "local_plugins")

    private fun typeDir(context: Context, type: PluginPackageInspector.Type) =
        File(root(context), if (type == PluginPackageInspector.Type.RENDERER) "renderers" else "drivers")

    /** Unique identifiers used by the renderer / driver managers */
    fun rendererUniqueId(id: String) = "local.renderer.$id"
    fun driverUniqueId(id: String) = "local.driver.$id"

    /**
     * Extracts the inspected libraries of [zip] and records the package. Re-importing the same id
     * replaces the previous version. Only entries listed in [inspection] are extracted.
     */
    @WorkerThread
    fun install(context: Context, zip: File, inspection: PluginPackageInspector.Inspection): Installed {
        val target = File(typeDir(context, inspection.type), inspection.id)
        val staging = File(target.parentFile, ".${inspection.id}.staging").apply { deleteRecursively(); mkdirs() }
        try {
            ZipFile(zip).use { z ->
                inspection.libraries.forEach { lib ->
                    val entry = z.getEntry(lib.entryName) ?: error("Missing ${lib.entryName}")
                    val out = File(staging, lib.installName)
                    z.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    // the package may have changed since inspection; verify what we wrote
                    val written = PluginPackageInspector.sha256(out.readBytes())
                    check(written == lib.sha256) { "Checksum changed for ${lib.entryName}" }
                }
            }
            if (inspection.type == PluginPackageInspector.Type.DRIVER) syncLinkerHook(context, staging)

            val record = Record(
                type = inspection.type,
                id = inspection.id,
                name = inspection.displayName,
                renderer = inspection.renderer,
                driver = inspection.driver,
                libraries = inspection.libraries.map { RecordLibrary(it.installName, it.sha256, !it.elf.is64Bit || it.elf.is16KbAligned) }
            )
            File(staging, RECORD).writeText(encode(record))

            target.deleteRecursively()
            check(staging.renameTo(target)) { "Failed to move package into place" }
            Logger.info(TAG, "Installed ${inspection.type} package '${inspection.displayName}' (${inspection.id})")
            return Installed(record, target)
        } finally {
            staging.deleteRecursively()
        }
    }

    @WorkerThread
    fun list(context: Context): List<Installed> =
        PluginPackageInspector.Type.entries.flatMap { type ->
            typeDir(context, type).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()
                .mapNotNull { dir ->
                    runCatching { decode(File(dir, RECORD).readText()) }
                        .onFailure { Logger.warning(TAG, "Ignoring unreadable package in $dir", it) }
                        .getOrNull()?.let { Installed(it, dir) }
                }
        }

    @WorkerThread
    fun delete(installed: Installed): Boolean = installed.dir.deleteRecursively()

    /**
     * Registers installed packages with the renderer / driver managers.
     * Called by PluginLoader after the APK plugins are parsed.
     */
    @WorkerThread
    fun loadInto(context: Context) {
        list(context).forEach { installed ->
            runCatching {
                val record = installed.record
                val dir = installed.dir.absolutePath
                when (record.type) {
                    PluginPackageInspector.Type.RENDERER -> {
                        val m = record.renderer ?: return@runCatching
                        RendererPluginManager.addLocalRenderer(
                            RendererPlugin(
                                packageName = rendererUniqueId(record.id),
                                id = m.id,
                                displayName = m.name,
                                summary = context.getString(R.string.local_plugin_imported_summary),
                                minMCVer = m.minMcVersion,
                                maxMCVer = m.maxMcVersion,
                                glName = m.glLibrary,
                                // same convention as APK plugins: a leading "/" means "in the plugin dir"
                                eglName = m.eglLibrary?.let { "$dir/$it" } ?: "libEGL.so",
                                path = dir,
                                env = m.env.mapValues { (key, value) ->
                                    if (key == "LIB_MESA_NAME" || key == "MESA_LIBRARY") "$dir/$value" else value
                                },
                                dlopen = m.dlopen
                            )
                        )
                    }
                    PluginPackageInspector.Type.DRIVER -> {
                        // the launcher's linker hook may change with app updates
                        syncLinkerHook(context, installed.dir)
                        DriverPluginManager.addLocalDriver(
                            Driver(
                                id = driverUniqueId(record.id),
                                appName = record.name,
                                appVersion = record.driver?.driverVersion.orEmpty(),
                                name = record.name,
                                summary = context.getString(R.string.local_plugin_imported_summary),
                                path = dir,
                                isLauncher = false,
                                vulkanLibrary = record.libraries.firstOrNull()?.name
                            )
                        )
                    }
                }
            }.onFailure { Logger.error(TAG, "Failed to load local package ${installed.dir}", it) }
        }
    }

    // Explicit (de)serialisation: reflection-based Gson would break under R8 obfuscation,
    // since field names could differ between app versions.
    private fun encode(r: Record): String = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("type", r.type.name)
        addProperty("id", r.id)
        addProperty("name", r.name)
        addProperty("importedAt", r.importedAt)
        r.renderer?.let { m ->
            add("renderer", JsonObject().apply {
                addProperty("id", m.id)
                addProperty("name", m.name)
                addProperty("glLibrary", m.glLibrary)
                m.eglLibrary?.let { addProperty("eglLibrary", it) }
                add("env", JsonObject().apply { m.env.forEach { (k, v) -> addProperty(k, v) } })
                add("dlopen", JsonArray().apply { m.dlopen.forEach { add(it) } })
                m.minMcVersion?.let { addProperty("minMcVersion", it) }
                m.maxMcVersion?.let { addProperty("maxMcVersion", it) }
            })
        }
        r.driver?.let { d ->
            add("driver", JsonObject().apply {
                addProperty("name", d.name)
                addProperty("libraryName", d.libraryName)
                d.driverVersion?.let { addProperty("driverVersion", it) }
                d.minApi?.let { addProperty("minApi", it) }
            })
        }
        add("libraries", JsonArray().apply {
            r.libraries.forEach { lib ->
                add(JsonObject().apply {
                    addProperty("name", lib.name)
                    addProperty("sha256", lib.sha256)
                    addProperty("aligned16k", lib.aligned16k)
                })
            }
        })
    }.toString()

    private fun decode(text: String): Record {
        val o = JsonParser.parseString(text).asJsonObject
        fun JsonObject.s(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.asString
        val renderer = o.getAsJsonObject("renderer")?.let { m ->
            PluginPackageInspector.RendererManifest(
                id = m.s("id")!!,
                name = m.s("name") ?: m.s("id")!!,
                glLibrary = m.s("glLibrary")!!,
                eglLibrary = m.s("eglLibrary"),
                env = m.getAsJsonObject("env")?.entrySet()?.associate { (k, v) -> k to v.asString }.orEmpty(),
                dlopen = m.getAsJsonArray("dlopen")?.map { it.asString }.orEmpty(),
                minMcVersion = m.s("minMcVersion"),
                maxMcVersion = m.s("maxMcVersion")
            )
        }
        val driver = o.getAsJsonObject("driver")?.let { d ->
            PluginPackageInspector.DriverManifest(
                name = d.s("name")!!,
                libraryName = d.s("libraryName")!!,
                driverVersion = d.s("driverVersion"),
                minApi = d.get("minApi")?.asInt
            )
        }
        return Record(
            type = PluginPackageInspector.Type.valueOf(o.s("type")!!),
            id = o.s("id")!!,
            name = o.s("name") ?: o.s("id")!!,
            renderer = renderer,
            driver = driver,
            libraries = o.getAsJsonArray("libraries")?.map { e ->
                val l = e.asJsonObject
                RecordLibrary(l.s("name")!!, l.s("sha256")!!, l.get("aligned16k")?.asBoolean ?: true)
            }.orEmpty(),
            importedAt = o.get("importedAt")?.asLong ?: 0L
        )
    }

    private fun syncLinkerHook(context: Context, dir: File) {
        val source = File(context.applicationInfo.nativeLibraryDir, LINKER_HOOK)
        if (!source.isFile) {
            Logger.warning(TAG, "$LINKER_HOOK not found in ${source.parent}; imported driver may fail to load")
            return
        }
        val target = File(dir, LINKER_HOOK)
        if (!target.isFile || target.length() != source.length() || target.lastModified() < source.lastModified()) {
            source.copyTo(target, overwrite = true)
        }
    }
}
