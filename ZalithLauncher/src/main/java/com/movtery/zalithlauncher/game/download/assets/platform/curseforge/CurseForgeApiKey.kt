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

package com.movtery.zalithlauncher.game.download.assets.platform.curseforge

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.security.AndroidKeystoreCipher
import com.movtery.zalithlauncher.security.CredentialCodec
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger

/**
 * Resolves the CurseForge API key.
 *
 * The CurseForge API rejects every request without a valid key (HTTP 403
 * "API Key missing or invalid"). Official builds embed one at build time; builds made
 * without it (forks, local builds) have none. Users can supply their own key, which is
 * stored encrypted with the Android Keystore (same scheme as account tokens) and takes
 * precedence over the built-in key.
 */
object CurseForgeApiKey {
    private const val TAG = "CurseForgeApiKey"

    private val codec = CredentialCodec { AndroidKeystoreCipher.getOrNull() }

    /** Cache of the decrypted user key, keyed by the stored (encrypted) value */
    @Volatile private var cache: Pair<String, String>? = null

    enum class Source { USER, BUILT_IN, NONE }

    /** The user's own key, or "" */
    fun userKey(): String {
        val stored = AllSettings.curseForgeUserApiKey.getValue()
        if (stored.isBlank()) return ""
        cache?.let { (s, plain) -> if (s == stored) return plain }
        val plain = when (val decoded = codec.decode(stored)) {
            is CredentialCodec.Decoded.Value -> decoded.plaintext.trim()
            is CredentialCodec.Decoded.Failed -> {
                Logger.warning(TAG, "Stored CurseForge API key could not be decrypted; ignoring it", decoded.cause)
                ""
            }
            null -> ""
        }
        cache = stored to plain
        return plain
    }

    /** The key to send: the user's key if set, otherwise the built-in key (may be blank) */
    fun effective(): String = userKey().ifBlank { BuildKeys.CURSEFORGE_API.trim() }

    fun source(): Source = when {
        userKey().isNotBlank() -> Source.USER
        BuildKeys.CURSEFORGE_API.isNotBlank() -> Source.BUILT_IN
        else -> Source.NONE
    }

    /** Saves (encrypted) or clears the user's key */
    fun saveUserKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            AllSettings.curseForgeUserApiKey.save("")
        } else {
            val encoded = codec.encode(trimmed) ?: return
            if (encoded.isInsecure) {
                Logger.warning(TAG, "Secure storage unavailable; the CurseForge API key is stored unencrypted")
            }
            AllSettings.curseForgeUserApiKey.save(encoded.stored)
        }
        cache = null
    }
}
