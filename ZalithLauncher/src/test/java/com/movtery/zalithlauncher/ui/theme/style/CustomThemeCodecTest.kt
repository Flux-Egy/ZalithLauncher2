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

package com.movtery.zalithlauncher.ui.theme.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomThemeCodecTest {
    private val layouts = setOf("ZALITH_DEFAULT", "PC_LAUNCHER", "MODRINTH_STYLE", "RETRO_PIXEL")
    private val palettes = setOf("TonalSpot", "Vibrant", "Expressive")

    private fun decode(text: String) = CustomThemeCodec.decode(text, layouts, palettes)

    @Test
    fun roundTrip() {
        val spec = CustomThemeSpec(
            name = "Night forest",
            layout = "PC_LAUNCHER",
            primary = 0xFF3C8527.toInt(),
            secondary = 0x80112233.toInt(),
            paletteStyle = "Vibrant",
            cornerRadius = 4,
            font = ThemeFont.PIXEL,
            darkMode = "Enable",
            backgroundOpacity = 60,
            backgroundBlur = 12
        )
        assertEquals(spec, decode(CustomThemeCodec.encode(spec)))
    }

    @Test
    fun secondaryIsOptional() {
        val spec = CustomThemeSpec(secondary = null)
        assertNull(decode(CustomThemeCodec.encode(spec)).secondary)
    }

    @Test
    fun valuesAreClampedAndUnknownNamesFallBack() {
        val spec = decode(
            """{"format":"zalith-theme","version":1,"primary":"#ff0000",
               "layout":"HACKER_MODE","paletteStyle":"Nope","font":"COMIC",
               "cornerRadius":9999,"backgroundOpacity":-5,"backgroundBlur":1e9,
               "darkMode":"Sometimes","unknownField":{"a":1}}"""
        )
        assertEquals(0xFFFF0000.toInt(), spec.primary)
        assertEquals("ZALITH_DEFAULT", spec.layout)
        assertEquals("TonalSpot", spec.paletteStyle)
        assertEquals(ThemeFont.DEFAULT, spec.font)
        assertEquals(32, spec.cornerRadius)
        assertEquals(20, spec.backgroundOpacity)
        assertEquals(40, spec.backgroundBlur)
        assertEquals("FollowSystem", spec.darkMode)
    }

    @Test
    fun nameIsSanitized() {
        val spec = decode("""{"format":"zalith-theme","version":1,"primary":"#000000","name":"a\u0000b${"x".repeat(200)}"}""")
        assertEquals(64, spec.name.length)
        assertEquals('a', spec.name[0])
        assertEquals('b', spec.name[1])
    }

    @Test
    fun rejectsNonThemeFiles() {
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) { decode("not json") }
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) { decode("[1,2]") }
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) { decode("""{"format":"other","version":1,"primary":"#000000"}""") }
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) { decode("""{"format":"zalith-theme","version":99,"primary":"#000000"}""") }
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) { decode("""{"format":"zalith-theme","version":1,"primary":"red"}""") }
        assertThrows(CustomThemeCodec.InvalidThemeException::class.java) {
            decode("""{"format":"zalith-theme","version":1,"primary":"#000000","pad":"${"x".repeat(CustomThemeCodec.MAX_BYTES)}"}""")
        }
    }

    @Test
    fun colorParsing() {
        assertEquals(0xFF123456.toInt(), CustomThemeCodec.parseColor("#123456"))
        assertEquals(0x80123456.toInt(), CustomThemeCodec.parseColor("80123456"))
        assertNull(CustomThemeCodec.parseColor("#12345"))
        assertNull(CustomThemeCodec.parseColor("#GGGGGG"))
        assertEquals("#123456", CustomThemeCodec.colorToHex(0xFF123456.toInt()))
        assertEquals("#80123456", CustomThemeCodec.colorToHex(0x80123456.toInt()))
    }
}
