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

package com.movtery.zalithlauncher.utils.logging

import com.movtery.zalithlauncher.utils.logging.LogRedactor.MASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlX3ZhbHVl"
    private val opaqueToken = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
    private val uuid = "0123456789abcdef0123456789abcdef"

    @Test
    fun masksValueAfterSecretFlags() {
        val args = listOf("--username", "Steve", "--accessToken", opaqueToken, "--session", opaqueToken, "--xuid", "2535400000000000", "--uuid", uuid)
        val out = LogRedactor.redactArgs(args)
        assertEquals(
            listOf("--username", "Steve", "--accessToken", MASK, "--session", MASK, "--xuid", MASK, "--uuid", uuid),
            out
        )
    }

    @Test
    fun flagMatchingIsCaseInsensitiveAndSupportsEqualsForm() {
        val out = LogRedactor.redactArgs(listOf("--ACCESSTOKEN=$opaqueToken", "--accesstoken", opaqueToken))
        assertEquals(listOf("--ACCESSTOKEN=$MASK", "--accesstoken", MASK), out)
    }

    @Test
    fun masksLegacyPositionalSessionWhenSecretIsKnown() {
        // pre-1.6 minecraftArguments: "${auth_player_name} ${auth_session}"
        val args = listOf("net.minecraft.client.Minecraft", "Steve", opaqueToken)
        val out = LogRedactor.redactArgs(args, secrets = listOf(opaqueToken))
        assertEquals(listOf("net.minecraft.client.Minecraft", "Steve", MASK), out)
    }

    @Test
    fun masksLegacySessionTokenFormat() {
        val out = LogRedactor.redactArgs(listOf("--session", "token:$opaqueToken:$uuid", "token:$opaqueToken:$uuid"))
        assertEquals("--session", out[0])
        assertEquals(MASK, out[1])
        assertEquals("token:$MASK:$uuid", out[2])
    }

    @Test
    fun doesNotMaskPlaceholderSecrets() {
        // offline accounts use "0" for tokens; masking it would hide unrelated "0" args
        val args = listOf("-Xmx2048M", "0", "--width", "1280")
        assertEquals(args, LogRedactor.redactArgs(args, secrets = listOf("0", "", null, "NULL")))
    }

    @Test
    fun outputHasSameSizeAndInputIsNotModified() {
        val args = mutableListOf("--accessToken", opaqueToken, "-Dfoo=bar")
        val copy = args.toList()
        val out = LogRedactor.redactArgs(args)
        assertEquals(args.size, out.size)
        assertEquals(copy, args)
    }

    @Test
    fun trailingSecretFlagWithoutValueIsKept() {
        assertEquals(listOf("--accessToken"), LogRedactor.redactArgs(listOf("--accessToken")))
    }

    @Test
    fun redactsBearerJwtJsonAndQueryParams() {
        val text = """
            Authorization: Bearer $opaqueToken
            raw jwt $token end
            {"accessToken":"$opaqueToken","refresh_token": "$opaqueToken","password":"hunter22","username":"Steve"}
            https://login.example/cb?code=1&access_token=$opaqueToken&state=x
        """.trimIndent()
        val out = LogRedactor.redact(text)
        assertFalse(out, out.contains(opaqueToken))
        assertFalse(out, out.contains(token))
        assertFalse(out, out.contains("hunter22"))
        assertTrue(out, out.contains("\"username\":\"Steve\""))
        assertTrue(out, out.contains("Bearer $MASK"))
        assertTrue(out, out.contains("state=x"))
    }

    @Test
    fun leavesOrdinaryTextUntouched() {
        val text = "[12:00:00] [Launcher/INFO] Renderer loaded: NG-GL4ES (opengles3_ng) java=-Xmx2048M"
        assertEquals(text, LogRedactor.redact(text))
    }
}
