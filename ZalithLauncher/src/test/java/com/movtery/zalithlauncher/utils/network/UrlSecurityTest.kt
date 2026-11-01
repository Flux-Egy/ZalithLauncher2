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

package com.movtery.zalithlauncher.utils.network

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSecurityTest {
    @Test
    fun upgradesYggdrasilTextureUrls() {
        assertEquals(
            "https://textures.minecraft.net/texture/abc123",
            upgradeToHttps("http://textures.minecraft.net/texture/abc123")
        )
        assertEquals(
            "https://ely.by/storage/skins/69c6740d.png?v=1",
            upgradeToHttps("http://ely.by/storage/skins/69c6740d.png?v=1")
        )
    }

    @Test
    fun schemeMatchIsCaseInsensitive() {
        assertEquals("https://Example.com/a", upgradeToHttps("HTTP://Example.com/a"))
    }

    @Test
    fun leavesHttpsAndOtherSchemesUntouched() {
        assertEquals("https://ely.by/x.png", upgradeToHttps("https://ely.by/x.png"))
        assertEquals("file:///sdcard/x.png", upgradeToHttps("file:///sdcard/x.png"))
        assertEquals("", upgradeToHttps(""))
    }

    @Test
    fun leavesLoopbackAndExplicitPortsUntouched() {
        assertEquals("http://localhost:25585/textures/a", upgradeToHttps("http://localhost:25585/textures/a"))
        assertEquals("http://127.0.0.1/textures/a", upgradeToHttps("http://127.0.0.1/textures/a"))
        assertEquals("http://[::1]/a", upgradeToHttps("http://[::1]/a"))
        assertEquals("http://skins.example.org:8080/a.png", upgradeToHttps("http://skins.example.org:8080/a.png"))
    }

    @Test
    fun leavesUnparsableInputUntouched() {
        assertEquals("http://bad host/ x", upgradeToHttps("http://bad host/ x"))
    }
}
