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

sealed interface ModIssue {
    val severity: Severity

    enum class Severity { ERROR, WARNING }

    /** [mod] requires [dependencyId], and no installed mod (or bundled jar / provides) supplies it */
    data class MissingDependency(val mod: ModDependencyInfo, val dependencyId: String) : ModIssue {
        override val severity = Severity.ERROR
    }

    /** [mod] declares it breaks / conflicts with the installed [other] */
    data class Incompatible(
        val mod: ModDependencyInfo,
        val other: ModDependencyInfo,
        val hard: Boolean
    ) : ModIssue {
        override val severity = if (hard) Severity.ERROR else Severity.WARNING
    }

    /** The same mod id is installed more than once (e.g. two versions of one mod) */
    data class Duplicate(val id: String, val mods: List<ModDependencyInfo>) : ModIssue {
        override val severity = Severity.ERROR
    }

    /** [mod] has no metadata for the instance's loader */
    data class WrongLoader(val mod: ModDependencyInfo, val instanceLoader: DepLoader) : ModIssue {
        override val severity = Severity.ERROR
    }
}

/**
 * Presence-based dependency analysis of the enabled mods of one instance.
 * Pure function of its inputs (unit tested). Version ranges are not evaluated.
 */
object ModDependencyGraph {

    /** Ids supplied by the platform itself, never "missing" */
    val BUILTIN_IDS: Set<String> = setOf(
        "minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader",
        "forge", "neoforge", "fml", "javafml", "lowcodefml", "mcp"
    )

    /** Sinytra Connector lets NeoForge load Fabric mods */
    private val CONNECTOR_IDS = setOf("connector")

    fun analyze(mods: List<ModDependencyInfo>, instanceLoader: DepLoader?): List<ModIssue> {
        val issues = mutableListOf<ModIssue>()

        // Duplicates: compare top-level ids only (nested copies are deduplicated by the loader)
        mods.groupBy { it.id }.forEach { (id, group) ->
            if (group.size > 1) issues += ModIssue.Duplicate(id, group)
        }

        val provided: Set<String> = mods.flatMapTo(HashSet()) { it.allProvidedIds() } + BUILTIN_IDS
        val byProvidedId: Map<String, ModDependencyInfo> = buildMap {
            mods.forEach { mod -> mod.allProvidedIds().forEach { putIfAbsent(it, mod) } }
        }

        for (mod in mods) {
            for (relation in mod.relations) {
                when (relation.kind) {
                    RelationKind.REQUIRED -> if (relation.targetId !in provided) {
                        issues += ModIssue.MissingDependency(mod, relation.targetId)
                    }
                    RelationKind.BREAKS, RelationKind.CONFLICTS -> {
                        val other = byProvidedId[relation.targetId]
                        if (other != null && other !== mod) {
                            issues += ModIssue.Incompatible(mod, other, hard = relation.kind == RelationKind.BREAKS)
                        }
                    }
                }
            }
        }

        if (instanceLoader != null) {
            val hasConnector = CONNECTOR_IDS.any { it in provided }
            mods.filter { it.loaders.isNotEmpty() && !runsOn(it.loaders, instanceLoader, hasConnector) }
                .forEach { issues += ModIssue.WrongLoader(it, instanceLoader) }
        }

        return issues
    }

    /** Whether a jar declaring [loaders] can be loaded by [instance] */
    fun runsOn(loaders: Set<DepLoader>, instance: DepLoader, hasConnector: Boolean = false): Boolean = when (instance) {
        DepLoader.FABRIC -> DepLoader.FABRIC in loaders
        // Quilt loads Fabric mods
        DepLoader.QUILT -> DepLoader.QUILT in loaders || DepLoader.FABRIC in loaders
        DepLoader.FORGE -> DepLoader.FORGE in loaders
        // NeoForge 1.20.1 reads META-INF/mods.toml; Connector adds Fabric mods
        DepLoader.NEOFORGE -> DepLoader.NEOFORGE in loaders || DepLoader.FORGE in loaders ||
                (hasConnector && DepLoader.FABRIC in loaders)
    }
}
