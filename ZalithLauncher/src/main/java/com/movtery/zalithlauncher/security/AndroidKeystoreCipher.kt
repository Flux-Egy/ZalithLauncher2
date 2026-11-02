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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.movtery.zalithlauncher.utils.logging.Logger
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val TAG = "AndroidKeystoreCipher"

/**
 * AES-256-GCM key held in AndroidKeyStore, used to encrypt account credentials at rest.
 *
 * The key never leaves the keystore and requires no user authentication, so background
 * token refreshes keep working. It is not exportable and is not included in backups;
 * the account database is excluded from backups as well (see backup_rules.xml).
 */
object AndroidKeystoreCipher {
    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "zl_credentials_v1"

    @Volatile
    private var cached: SecretCipher? = null

    @Volatile
    private var unavailable: Throwable? = null

    /**
     * Returns the keystore-backed cipher, or null if AndroidKeyStore cannot provide a key
     * on this device. The result (including failure) is cached for the process lifetime.
     */
    fun getOrNull(): SecretCipher? {
        cached?.let { return it }
        if (unavailable != null) return null
        synchronized(this) {
            cached?.let { return it }
            if (unavailable != null) return null
            return try {
                val key = loadOrCreateKey()
                AesGcmCipher { key }.also { cached = it }
            } catch (t: Throwable) {
                unavailable = t
                Logger.error(TAG, "AndroidKeyStore unavailable; credentials cannot be encrypted at rest", t)
                null
            }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
