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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Validates a drop-in plugin package (.zip) before anything is extracted. Pure JVM code.
 *
 * Renderer package:
 *   renderer.json  {"schemaVersion":1, "id", "name", "glLibrary", "eglLibrary"?, "env"?, "dlopen"?,
 *                   "minMcVersion"?, "maxMcVersion"?}
 *   lib/<abi>/<name>.so
 * Turnip driver package (AdrenoTools format, widely used by Android emulators/launchers):
 *   meta.json      {"name", "libraryName", "driverVersion"?, "description"?, "author"?, "minApi"?}
 *   <libraryName>  (arm64 Vulkan ICD, installed as libvulkan_freedreno.so)
 */
object PluginPackageInspector {
    enum class Type { RENDERER, DRIVER }

    const val MAX_PACKAGE_BYTES = 256L * 1024 * 1024
    const val MAX_LIBRARY_BYTES = 128L * 1024 * 1024
    const val MAX_ENTRIES = 512

    private val ID_PATTERN = Regex("""^[A-Za-z0-9_.\-]{1,64}$""")
    private val LIB_NAME_PATTERN = Regex("""^[A-Za-z0-9_.+\-]{1,128}\.so$""")

    data class Library(
        /** Entry name inside the zip */
        val entryName: String,
        /** File name it will be installed as */
        val installName: String,
        val size: Long,
        val sha256: String,
        val elf: ElfInspector.ElfInfo
    )

    data class RendererManifest(
        val id: String,
        val name: String,
        val glLibrary: String,
        /** null = system EGL; otherwise a library shipped in the package */
        val eglLibrary: String?,
        val env: Map<String, String>,
        val dlopen: List<String>,
        val minMcVersion: String?,
        val maxMcVersion: String?
    )

    data class DriverManifest(val name: String, val libraryName: String, val driverVersion: String?, val minApi: Int?)

    data class Inspection(
        val type: Type,
        /** Stable id used for the install directory */
        val id: String,
        val displayName: String,
        val abi: String,
        val libraries: List<Library>,
        val renderer: RendererManifest? = null,
        val driver: DriverManifest? = null,
        /** Installable, but the user should know (e.g. not 16 KB aligned) */
        val warnings: List<Warning> = emptyList()
    )

    sealed interface Warning {
        data class Not16KbAligned(val library: String, val align: Long?) : Warning
    }

    class InvalidPackageException(message: String) : Exception(message)

    /**
     * @param deviceAbi the device's primary ABI (e.g. "arm64-v8a")
     * @param deviceApi Build.VERSION.SDK_INT, checked against a driver's minApi
     * @throws InvalidPackageException with a user-presentable reason
     */
    fun inspect(zip: File, deviceAbi: String, deviceApi: Int): Inspection {
        if (zip.length() > MAX_PACKAGE_BYTES) fail("Package is larger than ${MAX_PACKAGE_BYTES / (1024 * 1024)} MB")
        val zipFile = runCatching { ZipFile(zip) }.getOrElse { fail("Not a valid zip file") }
        zipFile.use { z ->
            val entries = z.entries().toList()
            if (entries.size > MAX_ENTRIES) fail("Too many entries (${entries.size})")
            entries.forEach { e ->
                val n = e.name.replace('\\', '/')
                if (n.startsWith("/") || n.split('/').any { it == ".." }) fail("Unsafe path in package: ${e.name}")
            }
            fun text(name: String): String? = z.getEntry(name)?.let { e -> z.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }

            text("renderer.json")?.let { return inspectRenderer(z, parseObject(it, "renderer.json"), deviceAbi) }
            text("meta.json")?.let { return inspectDriver(z, parseObject(it, "meta.json"), deviceAbi, deviceApi) }
            // bare driver zip (common for community PanVK/Turnip builds): just the Vulkan library
            bareDriverEntry(z)?.let { entry ->
                val meta = JsonObject().apply {
                    addProperty("libraryName", entry)
                    addProperty("name", entry.substringAfterLast('/').removeSuffix(".so"))
                }
                return inspectDriver(z, meta, deviceAbi, deviceApi)
            }
            fail("Not a renderer package (renderer.json) or driver package (meta.json, or a libvulkan_freedreno.so / libvulkan_panfrost.so)")
        }
    }

    private fun inspectRenderer(z: ZipFile, json: JsonObject, deviceAbi: String): Inspection {
        val schema = json.get("schemaVersion")?.asInt ?: fail("renderer.json: missing schemaVersion")
        if (schema != 1) fail("renderer.json: unsupported schemaVersion $schema")
        val id = json.str("id") ?: fail("renderer.json: missing id")
        if (!ID_PATTERN.matches(id)) fail("renderer.json: invalid id")
        val glLibrary = json.str("glLibrary") ?: fail("renderer.json: missing glLibrary")
        val eglLibrary = json.str("eglLibrary")
        val dlopen = json.get("dlopen")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
        val env = json.get("env")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()
            ?.associate { (k, v) -> k to v.asString }.orEmpty()
        (listOfNotNull(glLibrary, eglLibrary) + dlopen).forEach {
            if (!LIB_NAME_PATTERN.matches(it)) fail("renderer.json: invalid library name '$it'")
        }

        val prefix = "lib/$deviceAbi/"
        val libEntries = z.entries().toList().filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".so") }
        if (libEntries.isEmpty()) fail("No libraries for this device's ABI ($deviceAbi) under $prefix")
        val libraries = libEntries.map { e ->
            val name = e.name.removePrefix(prefix)
            if (!LIB_NAME_PATTERN.matches(name)) fail("Invalid library file name: ${e.name}")
            readLibrary(z, e.name, name, deviceAbi)
        }
        val shipped = libraries.map { it.installName }.toSet()
        (listOf(glLibrary) + dlopen + listOfNotNull(eglLibrary)).forEach {
            if (it !in shipped) fail("renderer.json references $it, which is not in $prefix")
        }

        return Inspection(
            type = Type.RENDERER,
            id = id,
            displayName = json.str("name") ?: id,
            abi = deviceAbi,
            libraries = libraries,
            renderer = RendererManifest(
                id = id,
                name = json.str("name") ?: id,
                glLibrary = glLibrary,
                eglLibrary = eglLibrary,
                env = env,
                dlopen = dlopen,
                minMcVersion = json.str("minMcVersion"),
                maxMcVersion = json.str("maxMcVersion")
            ),
            warnings = alignmentWarnings(libraries)
        )
    }

    /** Entry of a known Vulkan driver library in a zip without meta.json, if exactly one */
    private fun bareDriverEntry(z: ZipFile): String? = z.entries().asSequence()
        .map { it.name }
        .filter { it.substringAfterLast('/') in BARE_DRIVER_NAMES }
        .toList()
        .singleOrNull()

    private val BARE_DRIVER_NAMES = setOf("libvulkan_freedreno.so", "libvulkan_panfrost.so")

    /** Mesa PanVK (Mali) driver, as opposed to Turnip (Adreno) */
    fun isPanVk(libraryName: String, gpu: String?): Boolean =
        libraryName.contains("panfrost", ignoreCase = true) || gpu.equals("mali", ignoreCase = true)

    private fun inspectDriver(z: ZipFile, json: JsonObject, deviceAbi: String, deviceApi: Int): Inspection {
        if (deviceAbi != "arm64-v8a") fail("Vulkan driver packages require an arm64-v8a device")
        val libraryName = json.str("libraryName") ?: fail("meta.json: missing libraryName")
        if (!LIB_NAME_PATTERN.matches(libraryName.substringAfterLast('/'))) fail("meta.json: invalid libraryName")
        val name = json.str("name") ?: libraryName
        val minApi = json.get("minApi")?.takeIf { it.isJsonPrimitive }?.asInt
        if (minApi != null && deviceApi < minApi) fail("Driver requires Android API $minApi (device has $deviceApi)")
        z.getEntry(libraryName) ?: fail("meta.json references $libraryName, which is not in the package")
        // PanVK keeps a name the native loader recognizes (it only loads PanVK on Mali)
        val installName = if (isPanVk(libraryName, json.str("gpu"))) PANVK_INSTALL_NAME else DRIVER_INSTALL_NAME
        val library = readLibrary(z, libraryName, installName = installName, deviceAbi = deviceAbi)

        val id = "driver_" + library.sha256.take(16)
        return Inspection(
            type = Type.DRIVER,
            id = id,
            displayName = name,
            abi = deviceAbi,
            libraries = listOf(library),
            driver = DriverManifest(name, libraryName, json.str("driverVersion"), minApi),
            warnings = alignmentWarnings(listOf(library))
        )
    }

    /** The driver loader dlopens this name from DRIVER_PATH (driver_helper.c) */
    const val DRIVER_INSTALL_NAME = "libvulkan_freedreno.so"

    /** PanVK install name; passed to the loader as VULKAN_DRIVER_LIB */
    const val PANVK_INSTALL_NAME = "libvulkan_panfrost.so"

    private fun readLibrary(z: ZipFile, entryName: String, installName: String, deviceAbi: String): Library {
        val entry = z.getEntry(entryName)
        if (entry.size > MAX_LIBRARY_BYTES) fail("$entryName is larger than ${MAX_LIBRARY_BYTES / (1024 * 1024)} MB")
        val bytes = z.getInputStream(entry).use { it.readBytes() }
        if (bytes.size > MAX_LIBRARY_BYTES) fail("$entryName is too large")
        val elf = ElfInspector.inspect(bytes) ?: fail("$entryName is not an ELF library")
        if (!elf.isSharedObject) fail("$entryName is not a shared library")
        if (elf.abi != deviceAbi) fail("$entryName is built for ${elf.abi ?: "an unknown CPU"}, this device needs $deviceAbi")
        return Library(entryName, installName, bytes.size.toLong(), sha256(bytes), elf)
    }

    private fun alignmentWarnings(libraries: List<Library>): List<Warning> =
        libraries.filter { it.elf.is64Bit && !it.elf.is16KbAligned }
            .map { Warning.Not16KbAligned(it.installName, it.elf.minLoadAlign) }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseObject(text: String, name: String): JsonObject =
        runCatching { JsonParser.parseString(text).asJsonObject }.getOrElse { fail("$name is not a JSON object") }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun fail(message: String): Nothing = throw InvalidPackageException(message)
}
