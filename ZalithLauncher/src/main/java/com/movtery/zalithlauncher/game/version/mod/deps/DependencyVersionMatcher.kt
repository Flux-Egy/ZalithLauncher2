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

import java.io.File
import java.util.zip.ZipFile

/**
 * Picks dependency versions that satisfy the version range the parent mod declares.
 *
 * Platforms only say "Iris needs Sodium"; the range ("sodium": "0.6.x") lives in the
 * parent jar's metadata. Pure Kotlin + java.util.zip, unit-tested.
 */
object DependencyVersionMatcher {

    private val PRE_RELEASE = Regex("""^(alpha|beta|rc|pre)(\.?\d+)?$""", RegexOption.IGNORE_CASE)

    /**
     * Extracts the mod's own version from a platform version name such as
     * "mc1.21.1-0.6.13-fabric", "1.8.8+1.21.1-fabric" or "0.8.13-beta.2+mc1.21.1".
     * Tokens that are Minecraft versions of [gameVersions] or start with "mc" are skipped.
     * @return null when no plausible version is found (caller must not filter then)
     */
    fun extractVersion(versionName: String, gameVersions: Collection<String>): ModVersion? {
        val tokens = versionName.split('-', '+', ' ', '_').filter { it.isNotBlank() }
        for ((index, raw) in tokens.withIndex()) {
            val token = raw.removePrefix("v").removePrefix("V")
            if (raw.startsWith("mc", ignoreCase = true)) continue
            if (token in gameVersions) continue
            if (!token.first().isDigit() || !token.contains('.')) continue
            val next = tokens.getOrNull(index + 1)
            val withPre = if (next != null && PRE_RELEASE.matches(next)) "$token-$next" else token
            ModVersion.parse(withPre)?.let { return it }
        }
        return null
    }

    /**
     * Keeps the candidates whose version satisfies [range]. Candidates whose version cannot
     * be read are kept (benefit of the doubt). If nothing matches, returns an empty list.
     */
    fun <T> filter(
        candidates: List<T>,
        range: ModVersionRange,
        versionName: (T) -> String,
        gameVersions: (T) -> Collection<String>
    ): List<T> = candidates.filter { candidate ->
        val v = extractVersion(versionName(candidate), gameVersions(candidate))
        v == null || range.matches(v)
    }

    /**
     * Required dependency ranges declared by a mod jar (fabric.mod.json, quilt.mod.json,
     * META-INF/mods.toml, META-INF/neoforge.mods.toml), keyed by dependency mod id.
     * Unversioned relations are omitted. Empty on any read error.
     */
    fun requiredRanges(jar: File): Map<String, Pair<ModVersionRange, String>> = runCatching {
        ZipFile(jar).use { zip ->
            fun text(name: String): String? = zip.getEntry(name)?.let { e ->
                if (e.size > 1024 * 1024) null else zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val infos = text("fabric.mod.json")?.let { ModMetadataParsers.parseFabric(it)?.infos }
                ?: text("quilt.mod.json")?.let { ModMetadataParsers.parseQuilt(it)?.infos }
                ?: text("META-INF/neoforge.mods.toml")?.let { ModMetadataParsers.parseModsToml(it, DepLoader.NEOFORGE)?.infos }
                ?: text("META-INF/mods.toml")?.let { ModMetadataParsers.parseModsToml(it, DepLoader.FORGE)?.infos }
                ?: emptyList()
            buildMap {
                infos.flatMap { it.relations }
                    .filter { it.kind == RelationKind.REQUIRED && it.range != null && it.range !== ModVersionRange.ANY }
                    .forEach { put(it.targetId, it.range!! to (it.rangeText ?: "")) }
            }
        }
    }.getOrDefault(emptyMap())

    /** Normalized key for matching a platform project (slug / title) to a mod id */
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Finds the range for a dependency project, matching its slug or title to the declared mod ids */
    fun rangeFor(
        ranges: Map<String, Pair<ModVersionRange, String>>,
        slug: String?,
        title: String?
    ): Pair<ModVersionRange, String>? {
        if (ranges.isEmpty()) return null
        val byKey = ranges.entries.associate { normalize(it.key) to it.value }
        return listOfNotNull(slug, title).map { normalize(it) }.firstNotNullOfOrNull { byKey[it] }
    }
}
