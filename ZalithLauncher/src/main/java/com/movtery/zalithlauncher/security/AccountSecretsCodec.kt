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

package com.movtery.zalithlauncher.security

/**
 * The secret fields of an account row. Kept separate from the Room entity so the
 * seal/open/migration rules can be unit tested on the JVM.
 */
data class AccountSecrets(
    val accessToken: String,
    val refreshToken: String,
    val clientToken: String,
    val otherPassword: String?
)

/**
 * Applies [CredentialCodec] to every secret field of an account.
 *
 * Rules:
 * - [seal]: encrypts all non-placeholder values; [Sealed.isInsecure] is true if any value
 *   had to be stored in plaintext because secure storage is unavailable.
 * - [open]: decrypts encrypted values; legacy plaintext is passed through and flagged via
 *   [Opened.needsReencrypt] so the caller can migrate the row. A value that cannot be
 *   decrypted is replaced by "" (forcing a normal re-login for that account) and its field
 *   name is reported in [Opened.failedFields]; nothing else about the account is lost.
 */
class AccountSecretsCodec(private val codec: CredentialCodec) {

    data class Sealed(val secrets: AccountSecrets, val isInsecure: Boolean)

    data class Opened(
        val secrets: AccountSecrets,
        val needsReencrypt: Boolean,
        val failedFields: List<String>
    )

    fun seal(secrets: AccountSecrets): Sealed {
        var insecure = false
        fun enc(value: String): String {
            val encoded = codec.encode(value)!!
            if (encoded.isInsecure) insecure = true
            return encoded.stored
        }
        val sealed = AccountSecrets(
            accessToken = enc(secrets.accessToken),
            refreshToken = enc(secrets.refreshToken),
            clientToken = enc(secrets.clientToken),
            otherPassword = secrets.otherPassword?.let { enc(it) }
        )
        return Sealed(sealed, insecure)
    }

    fun open(stored: AccountSecrets): Opened {
        var legacy = false
        val failed = mutableListOf<String>()
        fun dec(field: String, value: String): String =
            when (val result = codec.decode(value)!!) {
                is CredentialCodec.Decoded.Value -> {
                    if (result.isLegacyPlaintext) legacy = true
                    result.plaintext
                }
                is CredentialCodec.Decoded.Failed -> {
                    failed += field
                    ""
                }
            }
        val opened = AccountSecrets(
            accessToken = dec("accessToken", stored.accessToken),
            refreshToken = dec("refreshToken", stored.refreshToken),
            clientToken = dec("clientToken", stored.clientToken),
            otherPassword = stored.otherPassword?.let { dec("otherPassword", it) }
        )
        return Opened(opened, needsReencrypt = legacy, failedFields = failed)
    }
}
