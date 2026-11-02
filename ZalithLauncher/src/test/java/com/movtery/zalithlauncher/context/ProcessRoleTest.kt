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

package com.movtery.zalithlauncher.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessRoleTest {
    private val pkg = "com.movtery.zalithlauncher.v2"

    @Test
    fun mapsManifestProcesses() {
        assertEquals(ProcessRole.MAIN, ProcessRole.fromProcessName(pkg, pkg))
        assertEquals(ProcessRole.GAME, ProcessRole.fromProcessName("$pkg:game", pkg))
        assertEquals(ProcessRole.JVM, ProcessRole.fromProcessName("$pkg:jvm", pkg))
        assertEquals(ProcessRole.FILE_MANAGER, ProcessRole.fromProcessName("$pkg:filemanager", pkg))
    }

    @Test
    fun worksWithDebugSuffix() {
        val debug = "com.movtery.zalithlauncher.v2.debug"
        assertEquals(ProcessRole.GAME, ProcessRole.fromProcessName("$debug:game", debug))
        assertEquals(ProcessRole.MAIN, ProcessRole.fromProcessName(debug, debug))
    }

    @Test
    fun unknownOrMissingNamesFallBackSafely() {
        assertEquals(ProcessRole.MAIN, ProcessRole.fromProcessName(null, pkg))
        assertEquals(ProcessRole.MAIN, ProcessRole.fromProcessName("", pkg))
        assertEquals(ProcessRole.OTHER, ProcessRole.fromProcessName("$pkg:something_new", pkg))
    }
}
