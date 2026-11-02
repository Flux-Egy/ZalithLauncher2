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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModDependencyGraphTest {
    private fun fabric(id: String, vararg rel: ModRelation, provides: Set<String> = emptySet(), nested: List<ModDependencyInfo> = emptyList()) =
        ModDependencyInfo(id = id, loaders = setOf(DepLoader.FABRIC), provides = provides, relations = rel.toList(), nested = nested)

    private fun req(id: String) = ModRelation(id, RelationKind.REQUIRED)

    // ------------------------------------------------------------------ graph

    @Test
    fun satisfiedDependencyHasNoIssues() {
        val issues = ModDependencyGraph.analyze(
            listOf(fabric("sodium", req("fabric-api"), req("minecraft"), req("fabricloader")), fabric("fabric-api")),
            DepLoader.FABRIC
        )
        assertEquals(emptyList<ModIssue>(), issues)
    }

    @Test
    fun missingRequiredDependencyIsReported() {
        val issues = ModDependencyGraph.analyze(listOf(fabric("iris", req("sodium"))), DepLoader.FABRIC)
        val missing = issues.single() as ModIssue.MissingDependency
        assertEquals("iris", missing.mod.id)
        assertEquals("sodium", missing.dependencyId)
        assertEquals(ModIssue.Severity.ERROR, missing.severity)
    }

    @Test
    fun providesAndNestedJarsSatisfyDependencies() {
        val api = fabric("fabric-api", provides = setOf("fabric"), nested = listOf(fabric("fabric-resource-loader-v0")))
        val lib = fabric("cloth-config", nested = listOf(fabric("cloth-basic-math", nested = listOf(fabric("deep-lib")))))
        val mods = listOf(
            api, lib,
            fabric("a", req("fabric")),                    // provides alias
            fabric("b", req("fabric-resource-loader-v0")), // nested module
            fabric("c", req("deep-lib"))                   // nested two levels down
        )
        assertEquals(emptyList<ModIssue>(), ModDependencyGraph.analyze(mods, DepLoader.FABRIC))
    }

    @Test
    fun breaksIsErrorConflictsIsWarning() {
        val optifabric = fabric("optifabric")
        val issues = ModDependencyGraph.analyze(
            listOf(
                fabric("sodium", ModRelation("optifabric", RelationKind.BREAKS)),
                fabric("foo", ModRelation("optifabric", RelationKind.CONFLICTS)),
                optifabric
            ),
            DepLoader.FABRIC
        ).filterIsInstance<ModIssue.Incompatible>()
        assertEquals(2, issues.size)
        assertEquals(ModIssue.Severity.ERROR, issues.first { it.mod.id == "sodium" }.severity)
        assertEquals(ModIssue.Severity.WARNING, issues.first { it.mod.id == "foo" }.severity)
    }

    @Test
    fun breaksWithAbsentModIsFine() {
        val issues = ModDependencyGraph.analyze(listOf(fabric("sodium", ModRelation("optifabric", RelationKind.BREAKS))), DepLoader.FABRIC)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun duplicatesAreReported() {
        val issues = ModDependencyGraph.analyze(listOf(fabric("jei"), fabric("jei"), fabric("other")), DepLoader.FABRIC)
        val dup = issues.single() as ModIssue.Duplicate
        assertEquals("jei", dup.id)
        assertEquals(2, dup.mods.size)
    }

    @Test
    fun wrongLoaderRules() {
        val forgeMod = ModDependencyInfo("create", loaders = setOf(DepLoader.FORGE))
        val neoOnly = ModDependencyInfo("neo-only", loaders = setOf(DepLoader.NEOFORGE))
        val multi = ModDependencyInfo("multi", loaders = setOf(DepLoader.FABRIC, DepLoader.NEOFORGE))
        val quiltOnly = ModDependencyInfo("qsl-thing", loaders = setOf(DepLoader.QUILT))
        val unknown = ModDependencyInfo("resource-only", loaders = emptySet())

        fun wrong(mods: List<ModDependencyInfo>, loader: DepLoader) =
            ModDependencyGraph.analyze(mods, loader).filterIsInstance<ModIssue.WrongLoader>().map { it.mod.id }.toSet()

        assertEquals(setOf("create", "neo-only", "qsl-thing"), wrong(listOf(forgeMod, neoOnly, multi, quiltOnly, unknown), DepLoader.FABRIC))
        assertEquals(setOf("create", "neo-only"), wrong(listOf(forgeMod, neoOnly, multi, quiltOnly, fabric("sodium")), DepLoader.QUILT))
        assertEquals(setOf("neo-only", "multi", "sodium"), wrong(listOf(forgeMod, neoOnly, multi, fabric("sodium")), DepLoader.FORGE))
        assertEquals(setOf("sodium"), wrong(listOf(forgeMod, neoOnly, multi, fabric("sodium")), DepLoader.NEOFORGE))
        // Sinytra Connector lets NeoForge load Fabric mods
        val connector = ModDependencyInfo("connector", loaders = setOf(DepLoader.NEOFORGE))
        assertEquals(emptySet<String>(), wrong(listOf(connector, fabric("sodium")), DepLoader.NEOFORGE))
        // Unknown instance loader: no loader check
        assertTrue(ModDependencyGraph.analyze(listOf(forgeMod, fabric("x")), null).isEmpty())
    }

    // ---------------------------------------------------------------- parsers

    @Test
    fun parsesFabricModJson() {
        val parsed = ModMetadataParsers.parseFabric(
            """
            {"schemaVersion":1,"id":"iris","name":"Iris","version":"1.8.0",
             "provides":["oculus-compat"],
             "depends":{"fabricloader":">=0.15","minecraft":"~1.21","sodium":"*"},
             "recommends":{"modmenu":"*"},
             "breaks":{"optifabric":"*"},
             "conflicts":{"oldmod":"*"},
             "jars":[{"file":"META-INF/jars/glsl-transformer.jar"},{"file":"META-INF/jars/antlr.jar"}]}
            """.trimIndent()
        )!!
        val info = parsed.infos.single()
        assertEquals("iris", info.id)
        assertEquals("Iris", info.name)
        assertEquals(setOf("oculus-compat"), info.provides)
        assertEquals(setOf("fabricloader", "minecraft", "sodium"), info.relations.filter { it.kind == RelationKind.REQUIRED }.map { it.targetId }.toSet())
        assertEquals(listOf("optifabric"), info.relations.filter { it.kind == RelationKind.BREAKS }.map { it.targetId })
        assertEquals(listOf("oldmod"), info.relations.filter { it.kind == RelationKind.CONFLICTS }.map { it.targetId })
        assertTrue(info.relations.none { it.targetId == "modmenu" }) // recommends is optional
        assertEquals(listOf("META-INF/jars/glsl-transformer.jar", "META-INF/jars/antlr.jar"), parsed.nestedJarPaths)
    }

    @Test
    fun parsesQuiltModJson() {
        val parsed = ModMetadataParsers.parseQuilt(
            """
            {"schema_version":1,"quilt_loader":{"group":"org.example","id":"qmod","version":"1.0",
              "metadata":{"name":"Q Mod"},
              "provides":["qmod-api",{"id":"qmod-alias"}],
              "depends":["quilt_loader",{"id":"qsl","versions":">=6"},{"id":"emi","optional":true}],
              "breaks":[{"id":"badmod"}],
              "jars":["META-INF/jars/inner.jar"]}}
            """.trimIndent()
        )!!
        val info = parsed.infos.single()
        assertEquals("qmod", info.id)
        assertEquals("Q Mod", info.name)
        assertEquals(setOf("qmod-api", "qmod-alias"), info.provides)
        assertEquals(listOf("quilt_loader", "qsl"), info.relations.filter { it.kind == RelationKind.REQUIRED }.map { it.targetId })
        assertEquals(listOf("badmod"), info.relations.filter { it.kind == RelationKind.BREAKS }.map { it.targetId })
        assertEquals(listOf("META-INF/jars/inner.jar"), parsed.nestedJarPaths)
    }

    @Test
    fun parsesForgeModsToml() {
        val parsed = ModMetadataParsers.parseModsToml(
            """
            modLoader="javafml"
            loaderVersion="[47,)"
            [[mods]]
            modId="create"
            displayName="Create"
            [[dependencies.create]]
                modId="forge"
                mandatory=true
                versionRange="[47,)"
            [[dependencies.create]]
                modId="flywheel"
                mandatory=true
            [[dependencies.create]]
                modId="jei"
                mandatory=false
            [[dependencies.create]]
                modId="serverthing"
                mandatory=true
                side="SERVER"
            """.trimIndent(),
            DepLoader.FORGE
        )!!
        val info = parsed.infos.single()
        assertEquals("create", info.id)
        assertEquals(setOf(DepLoader.FORGE), info.loaders)
        assertEquals(listOf("forge", "flywheel"), info.relations.map { it.targetId })
        assertTrue(info.relations.all { it.kind == RelationKind.REQUIRED })
    }

    @Test
    fun parsesNeoForgeTypes() {
        val parsed = ModMetadataParsers.parseModsToml(
            """
            [[mods]]
            modId="neomod"
            [[dependencies.neomod]]
                modId="neoforge"
                type="required"
            [[dependencies.neomod]]
                modId="optifine"
                type="incompatible"
            [[dependencies.neomod]]
                modId="rubidium"
                type="discouraged"
            [[dependencies.neomod]]
                modId="jei"
                type="optional"
            """.trimIndent(),
            DepLoader.NEOFORGE
        )!!
        val kinds = parsed.infos.single().relations.associate { it.targetId to it.kind }
        assertEquals(
            mapOf("neoforge" to RelationKind.REQUIRED, "optifine" to RelationKind.BREAKS, "rubidium" to RelationKind.CONFLICTS),
            kinds
        )
    }

    @Test
    fun parsesJarJarMetadata() {
        val paths = ModMetadataParsers.parseJarJarMetadata(
            """{"jars":[{"identifier":{"group":"a","artifact":"b"},"path":"META-INF/jarjar/b.jar"}]}"""
        )
        assertEquals(listOf("META-INF/jarjar/b.jar"), paths)
    }

    @Test
    fun invalidInputReturnsNull() {
        assertNull(ModMetadataParsers.parseFabric("not json"))
        assertNull(ModMetadataParsers.parseFabric("""{"name":"no id"}"""))
        assertNull(ModMetadataParsers.parseQuilt("""{"schema_version":1}"""))
        assertNull(ModMetadataParsers.parseModsToml("modLoader=\"javafml\"", DepLoader.FORGE))
        assertNull(ModMetadataParsers.parseModsToml("[[[broken", DepLoader.FORGE))
        assertNotNull(ModMetadataParsers.parseJarJarMetadata("garbage"))
    }

    // ---------------------------------------------------------- version ranges

    /** Trimmed from the real fabric.mod.json of Iris 1.8.8+mc1.21.1 */
    private val iris188 = """{"id":"iris","version":"1.8.8+mc1.21.1","name":"Iris",
        "depends":{"fabricloader":">=0.12.3","minecraft":["1.21.x"],"sodium":["0.6.x"]},
        "breaks":{"embeddium":"*","optifabric":"*","distanthorizons":"<=2.1.99"}}"""

    /** Trimmed from the real fabric.mod.json of Sodium 0.8.13+mc1.21.1 */
    private val sodium0813 = """{"id":"sodium","version":"0.8.13+mc1.21.1","name":"Sodium",
        "depends":{"minecraft":["1.21","1.21.1"],"fabricloader":">=0.16.0"},
        "breaks":{"embeddium":"*","iris":"<1.8.13","bobby":"<5.2.4"},"provides":["indium"]}"""

    private fun parse(json: String) = ModMetadataParsers.parseFabric(json)!!.infos.single()

    @Test
    fun irisWithTooNewSodiumIsAVersionMismatch() {
        val issues = ModDependencyGraph.analyze(listOf(parse(iris188), parse(sodium0813)), DepLoader.FABRIC)
        val mismatch = issues.filterIsInstance<ModIssue.VersionMismatch>().single()
        assertEquals("iris", mismatch.mod.id)
        assertEquals("sodium", mismatch.dependencyId)
        assertEquals("0.6.x", mismatch.requiredRange)
        assertEquals(listOf("0.8.13+mc1.21.1"), mismatch.found)
        // Sodium 0.8.13 really does break Iris < 1.8.13
        val breaks = issues.filterIsInstance<ModIssue.Incompatible>().single()
        assertEquals("sodium", breaks.mod.id)
        assertEquals("iris", breaks.other.id)
        assertEquals("<1.8.13", breaks.range)
        assertTrue(breaks.hard)
    }

    @Test
    fun breaksOutsideTheRangeIsNotReported() {
        val iris1814 = iris188.replace("1.8.8+mc1.21.1", "1.8.14+mc1.21.1").replace("\"0.6.x\"", "\"0.8.x\"")
        val issues = ModDependencyGraph.analyze(listOf(parse(iris1814), parse(sodium0813)), DepLoader.FABRIC)
        assertEquals(emptyList<ModIssue>(), issues)
    }

    @Test
    fun matchingVersionsHaveNoIssues() {
        // Sodium 0.6.x does not declare a break on this Iris version
        val sodium0613 = sodium0813.replace("0.8.13+mc1.21.1", "0.6.13+mc1.21.1").replace(",\"iris\":\"<1.8.13\"", "")
        val issues = ModDependencyGraph.analyze(listOf(parse(iris188), parse(sodium0613)), DepLoader.FABRIC)
        assertEquals(emptyList<ModIssue>(), issues)
    }

    @Test
    fun unknownVersionGetsBenefitOfDoubt() {
        val forge = ModMetadataParsers.parseModsToml(
            """
            modLoader="javafml"
            [[mods]]
            modId="a"
            version="${'$'}{file.jarVersion}"
            [[mods]]
            modId="b"
            version="1.0"
            [[dependencies.b]]
            modId="a"
            type="required"
            versionRange="[2.0,)"
            """.trimIndent(),
            DepLoader.NEOFORGE
        )!!.infos
        assertEquals(emptyList<ModIssue>(), ModDependencyGraph.analyze(forge, DepLoader.NEOFORGE))
    }

    @Test
    fun forgeVersionRangeMismatch() {
        val forge = ModMetadataParsers.parseModsToml(
            """
            modLoader="javafml"
            [[mods]]
            modId="lib"
            version="1.4.0"
            [[mods]]
            modId="addon"
            version="2.0"
            [[dependencies.addon]]
            modId="lib"
            type="required"
            versionRange="[1.5,2)"
            [[dependencies.addon]]
            modId="minecraft"
            type="required"
            versionRange="[1.21.1]"
            """.trimIndent(),
            DepLoader.NEOFORGE
        )!!.infos
        val mismatch = ModDependencyGraph.analyze(forge, DepLoader.NEOFORGE).single() as ModIssue.VersionMismatch
        assertEquals("lib", mismatch.dependencyId)
        assertEquals("[1.5,2)", mismatch.requiredRange)
    }

    @Test
    fun wildcardBreaksAlwaysApplies() {
        val emb = ModMetadataParsers.parseFabric("""{"id":"embeddium"}""")!!.infos.single()
        val issues = ModDependencyGraph.analyze(listOf(parse(iris188), emb), DepLoader.FABRIC)
        assertTrue(issues.any { it is ModIssue.Incompatible && it.mod.id == "iris" && it.other.id == "embeddium" && it.range == null })
    }
}
