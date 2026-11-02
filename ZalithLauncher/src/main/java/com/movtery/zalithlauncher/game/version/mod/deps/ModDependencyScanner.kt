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

package com.movtery.zalithlauncher.game.version.mod.deps

import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Reads [ModDependencyInfo] from mod jars, including jar-in-jar mods (Fabric/Quilt "jars",
 * Forge/NeoForge META-INF/jarjar) so bundled dependencies count as present.
 *
 * Android-free (java.util.zip only) so it can be unit tested on the JVM. Call from a
 * background thread.
 */
object ModDependencyScanner {
    private const val MAX_NESTING_DEPTH = 3

    /** Nested jars larger than this are not inspected (keeps memory bounded) */
    private const val MAX_NESTED_JAR_BYTES = 32L * 1024 * 1024

    private const val FABRIC = "fabric.mod.json"
    private const val QUILT = "quilt.mod.json"
    private const val FORGE_TOML = "META-INF/mods.toml"
    private const val NEOFORGE_TOML = "META-INF/neoforge.mods.toml"
    private const val LEGACY_FORGE = "mcmod.info"
    private const val JARJAR = "META-INF/jarjar/metadata.json"
    private val METADATA_ENTRIES = setOf(FABRIC, QUILT, FORGE_TOML, NEOFORGE_TOML, LEGACY_FORGE, JARJAR)

    /**
     * @param instanceLoader selects which metadata of a multi-loader jar supplies the
     * dependency list (their dependencies differ per loader); null = first found
     * @return null if the file declares no mod metadata (resource jar, library, unreadable)
     */
    fun scan(file: File, instanceLoader: DepLoader?): ModDependencyInfo? {
        val entries = runCatching {
            ZipFile(file).use { zip ->
                METADATA_ENTRIES.mapNotNull { name ->
                    zip.getEntry(name)?.let { entry -> name to zip.getInputStream(entry).use { it.readBytes() } }
                }.toMap()
            }
        }.getOrElse {
            // Some jars confuse java.util.zip.ZipFile; fall back to streaming
            runCatching { file.inputStream().buffered().use { readEntries(it, METADATA_ENTRIES) } }.getOrNull()
        } ?: return null

        val nestedLoader: (Set<String>) -> Map<String, ByteArray> = { paths ->
            runCatching {
                ZipFile(file).use { zip ->
                    paths.mapNotNull { path ->
                        val entry = zip.getEntry(path) ?: return@mapNotNull null
                        if (entry.size > MAX_NESTED_JAR_BYTES) return@mapNotNull null
                        path to zip.getInputStream(entry).use { it.readBytes() }
                    }.toMap()
                }
            }.getOrElse {
                runCatching { file.inputStream().buffered().use { readEntries(it, paths) } }.getOrDefault(emptyMap())
            }
        }
        return build(entries, nestedLoader, file.name, instanceLoader, depth = 0)
    }

    /** Scans a jar held in memory (nested jars; also used by tests) */
    fun scanBytes(bytes: ByteArray, fileName: String, instanceLoader: DepLoader?, depth: Int = 0): ModDependencyInfo? {
        val entries = runCatching { readEntries(ByteArrayInputStream(bytes), METADATA_ENTRIES) }.getOrNull() ?: return null
        val nestedLoader: (Set<String>) -> Map<String, ByteArray> = { paths ->
            runCatching { readEntries(ByteArrayInputStream(bytes), paths) }.getOrDefault(emptyMap())
        }
        return build(entries, nestedLoader, fileName, instanceLoader, depth)
    }

    private fun build(
        entries: Map<String, ByteArray>,
        nestedLoader: (Set<String>) -> Map<String, ByteArray>,
        fileName: String,
        instanceLoader: DepLoader?,
        depth: Int
    ): ModDependencyInfo? {
        fun text(name: String) = entries[name]?.toString(Charsets.UTF_8)

        // Every loader this jar declares metadata for
        val candidates = LinkedHashMap<DepLoader, ModMetadataParsers.Parsed>()
        text(QUILT)?.let { ModMetadataParsers.parseQuilt(it) }?.let { candidates[DepLoader.QUILT] = it }
        text(FABRIC)?.let { ModMetadataParsers.parseFabric(it) }?.let { candidates[DepLoader.FABRIC] = it }
        text(NEOFORGE_TOML)?.let { ModMetadataParsers.parseModsToml(it, DepLoader.NEOFORGE) }?.let { candidates[DepLoader.NEOFORGE] = it }
        text(FORGE_TOML)?.let { ModMetadataParsers.parseModsToml(it, DepLoader.FORGE) }?.let { candidates[DepLoader.FORGE] = it }
        if (DepLoader.FORGE !in candidates) {
            text(LEGACY_FORGE)?.let { parseLegacyForge(it) }?.let { candidates[DepLoader.FORGE] = it }
        }
        if (candidates.isEmpty()) return null

        val selectedLoader = preferredOrder(instanceLoader).firstOrNull { it in candidates } ?: candidates.keys.first()
        val selected = candidates.getValue(selectedLoader)

        // Nested jars: declared by the selected metadata, plus Forge/NeoForge jarjar
        val nestedPaths = buildSet {
            addAll(selected.nestedJarPaths)
            if (selectedLoader == DepLoader.FORGE || selectedLoader == DepLoader.NEOFORGE) {
                text(JARJAR)?.let { addAll(ModMetadataParsers.parseJarJarMetadata(it)) }
            }
        }
        val nested = if (depth < MAX_NESTING_DEPTH && nestedPaths.isNotEmpty()) {
            nestedLoader(nestedPaths).mapNotNull { (path, bytes) ->
                scanBytes(bytes, path.substringAfterLast('/'), instanceLoader, depth + 1)
            }
        } else emptyList()

        // A mods.toml can declare several mods: the first is primary, the rest are provided too
        val primary = selected.infos.first()
        val siblings = selected.infos.drop(1)
        return primary.copy(
            loaders = candidates.keys.toSet(),
            relations = primary.relations + siblings.flatMap { it.relations },
            nested = nested + siblings,
            fileName = fileName
        )
    }

    /** Metadata to prefer for an instance's loader */
    fun preferredOrder(instanceLoader: DepLoader?): List<DepLoader> = when (instanceLoader) {
        DepLoader.FABRIC -> listOf(DepLoader.FABRIC)
        DepLoader.QUILT -> listOf(DepLoader.QUILT, DepLoader.FABRIC)
        DepLoader.FORGE -> listOf(DepLoader.FORGE)
        DepLoader.NEOFORGE -> listOf(DepLoader.NEOFORGE, DepLoader.FORGE, DepLoader.FABRIC)
        null -> emptyList()
    }

    /** Legacy Forge mcmod.info: [{"modid": ...}] or {"modList": [...]}; dependencies are not modelled */
    private fun parseLegacyForge(json: String): ModMetadataParsers.Parsed? {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        val list = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.get("modList")?.takeIf { it.isJsonArray }?.asJsonArray
            else -> null
        } ?: return null
        val infos = list.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("modid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: id
            ModDependencyInfo(id = id, name = name, loaders = setOf(DepLoader.FORGE))
        }
        return if (infos.isEmpty()) null else ModMetadataParsers.Parsed(infos, emptyList())
    }

    private fun readEntries(input: InputStream, wanted: Set<String>): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in wanted) {
                    result[entry.name] = zip.readBytes()
                    if (result.size == wanted.size) break
                }
            }
        }
        return result
    }
}
