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

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moandjiezana.toml.Toml

/** Loaders a mod jar declares metadata for (a multi-loader jar can declare several) */
enum class DepLoader(val displayName: String) {
    FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge")
}

/** How strongly a mod relates to another mod id */
enum class RelationKind {
    /** Must be present */
    REQUIRED,

    /** Must NOT be present (Fabric "breaks", Quilt "breaks", NeoForge "incompatible") */
    BREAKS,

    /** Should not be present (Fabric "conflicts", NeoForge "discouraged") */
    CONFLICTS
}

data class ModRelation(val targetId: String, val kind: RelationKind)

/**
 * Dependency-relevant metadata of one mod (or one nested jar-in-jar mod).
 * Version ranges are intentionally not modelled yet: the graph only checks presence.
 */
data class ModDependencyInfo(
    val id: String,
    val name: String = id,
    val loaders: Set<DepLoader>,
    /** Extra ids this mod satisfies (Fabric/Quilt "provides") */
    val provides: Set<String> = emptySet(),
    val relations: List<ModRelation> = emptyList(),
    /** Mods bundled inside this jar (jar-in-jar), recursively */
    val nested: List<ModDependencyInfo> = emptyList(),
    /** Source file name, for display */
    val fileName: String = ""
) {
    /** This mod's id, its provides and everything bundled inside it */
    fun allProvidedIds(): Set<String> = buildSet {
        add(id)
        addAll(provides)
        nested.forEach { addAll(it.allProvidedIds()) }
    }
}

/**
 * Pure parsers for mod metadata files. Each returns null when the text is not valid
 * metadata of that format. Paths of nested jars are returned separately so the jar
 * reader can recurse into them.
 */
object ModMetadataParsers {

    data class Parsed(val infos: List<ModDependencyInfo>, val nestedJarPaths: List<String>)

    // ---------------------------------------------------------------- Fabric

    fun parseFabric(json: String): Parsed? {
        val root = parseJsonObject(json) ?: return null
        val id = root.string("id") ?: return null
        val relations = mutableListOf<ModRelation>()
        root.objectKeys("depends").forEach { relations += ModRelation(it, RelationKind.REQUIRED) }
        root.objectKeys("breaks").forEach { relations += ModRelation(it, RelationKind.BREAKS) }
        root.objectKeys("conflicts").forEach { relations += ModRelation(it, RelationKind.CONFLICTS) }
        val jars = root.array("jars").mapNotNull { (it as? JsonObject)?.string("file") }
        return Parsed(
            infos = listOf(
                ModDependencyInfo(
                    id = id,
                    name = root.string("name") ?: id,
                    loaders = setOf(DepLoader.FABRIC),
                    provides = root.array("provides").mapNotNull { it.asStringOrNull() }.toSet(),
                    relations = relations
                )
            ),
            nestedJarPaths = jars
        )
    }

    // ----------------------------------------------------------------- Quilt

    fun parseQuilt(json: String): Parsed? {
        val loader = parseJsonObject(json)?.obj("quilt_loader") ?: return null
        val id = loader.string("id") ?: return null
        val relations = mutableListOf<ModRelation>()
        loader.array("depends").forEach { dep ->
            // "id" or {"id": ..., "optional": true, ...}; alternatives (arrays) are skipped
            val obj = dep as? JsonObject
            val depId = dep.asStringOrNull() ?: obj?.string("id") ?: return@forEach
            if (obj?.bool("optional") == true) return@forEach
            relations += ModRelation(depId, RelationKind.REQUIRED)
        }
        loader.array("breaks").forEach { dep ->
            val depId = dep.asStringOrNull() ?: (dep as? JsonObject)?.string("id") ?: return@forEach
            relations += ModRelation(depId, RelationKind.BREAKS)
        }
        val provides = loader.array("provides").mapNotNull { it.asStringOrNull() ?: (it as? JsonObject)?.string("id") }
        val name = loader.obj("metadata")?.string("name") ?: id
        return Parsed(
            infos = listOf(
                ModDependencyInfo(
                    id = id,
                    name = name,
                    loaders = setOf(DepLoader.QUILT),
                    provides = provides.toSet(),
                    relations = relations
                )
            ),
            nestedJarPaths = loader.array("jars").mapNotNull { it.asStringOrNull() }
        )
    }

    // ------------------------------------------- Forge / NeoForge (mods.toml)

    /**
     * @param loader FORGE for META-INF/mods.toml, NEOFORGE for META-INF/neoforge.mods.toml
     */
    fun parseModsToml(text: String, loader: DepLoader): Parsed? {
        val toml = runCatching { Toml().read(text) }.getOrNull() ?: return null
        val mods = runCatching { toml.getTables("mods") }.getOrNull().orEmpty()
        if (mods.isEmpty()) return null
        val dependencies = runCatching { toml.getTable("dependencies") }.getOrNull()

        val infos = mods.mapNotNull { mod ->
            val id = mod.getString("modId") ?: return@mapNotNull null
            val relations = mutableListOf<ModRelation>()
            val deps = runCatching { dependencies?.getTables(id) }.getOrNull().orEmpty()
            deps.forEach { dep ->
                val depId = dep.getString("modId") ?: return@forEach
                // Server-only dependencies are not needed on the client
                if (dep.getString("side")?.equals("SERVER", ignoreCase = true) == true) return@forEach
                val kind = when (dep.getString("type")?.lowercase()) {
                    "required" -> RelationKind.REQUIRED
                    "incompatible" -> RelationKind.BREAKS
                    "discouraged" -> RelationKind.CONFLICTS
                    "optional" -> null
                    // Forge (and old NeoForge) use mandatory = true/false
                    null -> if (dep.getBoolean("mandatory") == true) RelationKind.REQUIRED else null
                    else -> null
                } ?: return@forEach
                relations += ModRelation(depId, kind)
            }
            ModDependencyInfo(
                id = id,
                name = mod.getString("displayName") ?: id,
                loaders = setOf(loader),
                relations = relations
            )
        }
        return if (infos.isEmpty()) null else Parsed(infos, nestedJarPaths = emptyList())
    }

    /** META-INF/jarjar/metadata.json (Forge/NeoForge jar-in-jar) -> nested jar paths */
    fun parseJarJarMetadata(json: String): List<String> =
        parseJsonObject(json)?.array("jars")?.mapNotNull { (it as? JsonObject)?.string("path") }.orEmpty()

    // ---------------------------------------------------------------- helpers

    private fun parseJsonObject(text: String): JsonObject? =
        runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String? = get(key)?.asStringOrNull()
    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(key: String): List<JsonElement> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    private fun JsonObject.objectKeys(key: String): Set<String> = obj(key)?.keySet().orEmpty()
    private fun JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
