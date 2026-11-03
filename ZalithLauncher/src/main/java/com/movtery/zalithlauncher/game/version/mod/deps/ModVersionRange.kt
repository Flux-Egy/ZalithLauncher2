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

/**
 * A mod version as declared in its metadata, compared like Fabric's SemanticVersion:
 * numeric components (missing ones count as 0), then pre-release ("1.0.0-beta.2" sorts
 * before "1.0.0"); build metadata after '+' is ignored.
 */
class ModVersion private constructor(
    val raw: String,
    private val core: List<Int>,
    private val preRelease: List<String>?
) : Comparable<ModVersion> {

    override fun compareTo(other: ModVersion): Int {
        for (i in 0 until maxOf(core.size, other.core.size)) {
            val c = core.getOrElse(i) { 0 }.compareTo(other.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        val a = preRelease
        val b = other.preRelease
        if (a == null || b == null) {
            // a release sorts after any pre-release of the same core
            return when {
                a == null && b == null -> 0
                a == null -> 1
                else -> -1
            }
        }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: return -1
            val y = b.getOrNull(i) ?: return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val c = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1 // numeric identifiers sort before alphanumeric ones
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return 0
    }

    /** Whether the first [prefix].size components equal [prefix] */
    internal fun corePrefixEquals(prefix: List<Int>): Boolean =
        prefix.indices.all { core.getOrElse(it) { 0 } == prefix[it] }

    internal fun component(i: Int): Int = core.getOrElse(i) { 0 }

    override fun equals(other: Any?): Boolean = other is ModVersion && compareTo(other) == 0
    override fun hashCode(): Int = core.dropLastWhile { it == 0 }.hashCode() * 31 + (preRelease?.hashCode() ?: 0)
    override fun toString(): String = raw

    companion object {
        private val CORE = Regex("""^(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?$""")

        /**
         * @return null when [text] is not a comparable version (e.g. a Forge placeholder
         * like "${'$'}{file.jarVersion}"); callers must then treat any range as satisfied
         */
        fun parse(text: String?): ModVersion? {
            val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val withoutBuild = trimmed.substringBefore('+')
            val match = CORE.matchEntire(withoutBuild) ?: return null
            val core = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val pre = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')
            return ModVersion(trimmed, core, pre)
        }
    }
}

/** A set of acceptable versions declared by a dependency or a breaks/conflicts entry */
fun interface ModVersionRange {
    fun matches(version: ModVersion): Boolean

    companion object {
        val ANY = ModVersionRange { true }
    }
}

/**
 * Fabric / Quilt version predicates: "*", "1.2.3", "=1.2.3", ">=1.2", "<1.8.13",
 * "~1.2.3" (same minor), "^1.2.3" (same major), "1.21.x" (wildcard), and several
 * space-separated predicates combined with AND. A JSON array of predicates is OR.
 */
object FabricVersionPredicate {

    /** @return null if any part cannot be parsed (the relation is then treated as unversioned) */
    fun parse(text: String): ModVersionRange? {
        val terms = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return ModVersionRange.ANY
        val parsed = terms.map { parseTerm(it) ?: return null }
        // a single term keeps its identity, so "*" stays ModVersionRange.ANY
        return parsed.singleOrNull() ?: ModVersionRange { v -> parsed.all { it.matches(v) } }
    }

    /** OR of several predicate strings */
    fun parseAny(texts: List<String>): ModVersionRange? {
        if (texts.isEmpty()) return ModVersionRange.ANY
        val parsed = texts.map { parse(it) ?: return null }
        return parsed.singleOrNull() ?: ModVersionRange { v -> parsed.any { it.matches(v) } }
    }

    private fun parseTerm(term: String): ModVersionRange? {
        if (term == "*") return ModVersionRange.ANY
        val op = listOf(">=", "<=", ">", "<", "=", "~", "^").firstOrNull { term.startsWith(it) }
        val body = if (op != null) term.removePrefix(op) else term

        // wildcard form: 1.21.x / 1.x / 1.21.*
        val parts = body.substringBefore('+').split('.')
        val wildcardAt = parts.indexOfFirst { it == "x" || it == "X" || it == "*" }
        if (wildcardAt >= 0) {
            if (op != null && op != "=") return null
            val prefix = parts.take(wildcardAt).map { it.toIntOrNull() ?: return null }
            return ModVersionRange { v -> v.corePrefixEquals(prefix) }
        }

        val target = ModVersion.parse(body) ?: return null
        return when (op) {
            ">=" -> ModVersionRange { it >= target }
            "<=" -> ModVersionRange { it <= target }
            ">" -> ModVersionRange { it > target }
            "<" -> ModVersionRange { it < target }
            "~" -> ModVersionRange { it >= target && it.component(0) == target.component(0) && it.component(1) == target.component(1) }
            "^" -> ModVersionRange { it >= target && it.component(0) == target.component(0) }
            else -> ModVersionRange { it.compareTo(target) == 0 }
        }
    }
}

/**
 * Forge / NeoForge (Maven) version ranges: "[1.0,2.0)", "[1.0,)", "(,1.0]", "[1.0]",
 * unions like "[1,2),[3,4)". A bare version ("1.0") is a soft requirement: any version.
 */
object MavenVersionRange {
    private val INTERVAL = Regex("""([\[(])\s*([^,\])]*)\s*(?:,\s*([^\])]*))?\s*([\])])""")

    fun parse(text: String): ModVersionRange? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "*") return ModVersionRange.ANY
        if (trimmed.first() != '[' && trimmed.first() != '(') {
            // soft requirement, or a placeholder such as ${minecraft_version}
            return ModVersionRange.ANY
        }
        val intervals = INTERVAL.findAll(trimmed).toList()
        if (intervals.isEmpty()) return null
        val ranges = intervals.map { m ->
            val (open, lowText, highTextOrNull, close) = m.destructured
            val hasComma = m.value.contains(',')
            val low = lowText.takeIf { it.isNotBlank() }?.let { ModVersion.parse(it) ?: return null }
            val high = if (hasComma) {
                highTextOrNull.takeIf { it.isNotBlank() }?.let { ModVersion.parse(it) ?: return null }
            } else low // "[1.0]" = exactly 1.0
            val lowInclusive = open == "["
            val highInclusive = close == "]"
            ModVersionRange { v ->
                (low == null || if (lowInclusive) v >= low else v > low) &&
                        (high == null || if (highInclusive) v <= high else v < high)
            }
        }
        return ModVersionRange { v -> ranges.any { it.matches(v) } }
    }
}
