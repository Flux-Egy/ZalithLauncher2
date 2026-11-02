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

/**
 * Central redaction of credentials before anything is written to launcher or game logs.
 * Users routinely upload these logs to public paste sites, so anything that grants
 * account access must never reach them.
 *
 * Pure Kotlin (no Android APIs) so it can be unit tested on the JVM.
 */
object LogRedactor {
    const val MASK = "********************"

    /** Launch arguments whose following value is a secret or personal identifier */
    private val SECRET_VALUE_FLAGS = setOf(
        "--accesstoken",
        "--session",
        "--xuid"
    )

    /**
     * Known placeholder values that are not secrets (offline/default accounts).
     * Masking them would only make logs harder to read.
     */
    private val NON_SECRET_VALUES = setOf("", "0", "null", "none", "-", "{}")

    /** Values shorter than this are never treated as secrets (avoids masking common words) */
    private const val MIN_SECRET_LENGTH = 8

    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        // Authorization: Bearer <token>
        Regex("""(?i)(\bBearer\s+)[A-Za-z0-9._~+/=-]+""") to "$1$MASK",
        // JWT-shaped tokens (Minecraft / Xbox / OAuth access tokens)
        Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{4,}""") to MASK,
        // JSON fields: "accessToken": "...", "refresh_token":"...", "password": "..."
        Regex(
            """(?i)("(?:access_?token|refresh_?token|client_?token|id_?token|password)"\s*:\s*")[^"]*(")"""
        ) to "$1$MASK$2",
        // Query/form parameters: access_token=..., refresh_token=..., password=...
        Regex("""(?i)(\b(?:access_token|refresh_token|password)=)[^&\s"]+""") to "$1$MASK",
        // Legacy (1.6.x) session argument format: token:<accessToken>:<uuid>
        Regex("""(?i)\btoken:[^:\s]+:""") to "token:$MASK:"
    )

    /**
     * Redacts known secret shapes from free text.
     */
    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        for ((regex, replacement) in PATTERNS) {
            result = regex.replace(result, replacement)
        }
        return result
    }

    /**
     * Redacts a launch argument list for logging. The returned list has the same size
     * and order as [args]; the real arguments passed to the JVM are never modified.
     *
     * @param secrets exact secret values to mask wherever they appear (for example the
     * current account's access token). This catches legacy versions that pass the
     * session token as a positional argument (`${'$'}{auth_session}`).
     */
    fun redactArgs(args: List<String>, secrets: Collection<String?> = emptyList()): List<String> {
        val knownSecrets = secrets.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.length >= MIN_SECRET_LENGTH && it.lowercase() !in NON_SECRET_VALUES }
            .distinct()
            //longest first, so a secret that contains another is fully masked
            .sortedByDescending { it.length }
            .toList()

        val output = ArrayList<String>(args.size)
        var maskNext = false
        for (arg in args) {
            if (maskNext) {
                output += MASK
                maskNext = false
                continue
            }

            val flag = arg.substringBefore('=').lowercase()
            if (flag in SECRET_VALUE_FLAGS) {
                if (arg.contains('=')) {
                    output += "${arg.substringBefore('=')}=$MASK"
                } else {
                    output += arg
                    maskNext = true
                }
                continue
            }

            var masked = arg
            for (secret in knownSecrets) {
                masked = masked.replace(secret, MASK)
            }
            output += redact(masked)
        }
        return output
    }
}
