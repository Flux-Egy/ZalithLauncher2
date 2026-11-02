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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.crypto.KeyGenerator

class AccountSecretsCodecTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val codec = AccountSecretsCodec(CredentialCodec { AesGcmCipher { key } })

    private val microsoft = AccountSecrets(
        accessToken = "eyJ.access.token-value",
        refreshToken = "M.C123_refresh-token-value",
        clientToken = "0",
        otherPassword = null
    )
    private val elyBy = AccountSecrets(
        accessToken = "ely-access-token-0123456789",
        refreshToken = "0",
        clientToken = "client-token-0123456789",
        otherPassword = "hunter2-correct-horse"
    )
    private val offline = AccountSecrets(accessToken = "0", refreshToken = "0", clientToken = "0", otherPassword = null)

    @Test
    fun sealThenOpenRoundTrips() {
        for (secrets in listOf(microsoft, elyBy, offline)) {
            val sealed = codec.seal(secrets)
            assertFalse(sealed.isInsecure)
            val opened = codec.open(sealed.secrets)
            assertEquals(secrets, opened.secrets)
            assertFalse(opened.needsReencrypt)
            assertTrue(opened.failedFields.isEmpty())
        }
    }

    @Test
    fun sealedRowContainsNoPlaintextSecrets() {
        val sealed = codec.seal(elyBy).secrets
        listOf(sealed.accessToken, sealed.clientToken, sealed.otherPassword!!).forEach {
            assertTrue(it.startsWith(CredentialCodec.PREFIX))
        }
        assertEquals("0", sealed.refreshToken) // placeholder untouched
        assertFalse(sealed.toString().contains("hunter2"))
    }

    @Test
    fun legacyPlaintextRowOpensUnchangedAndRequestsMigration() {
        // Rows written before this change: plaintext in every column
        val opened = codec.open(elyBy)
        assertEquals(elyBy, opened.secrets)            // user stays logged in
        assertTrue(opened.needsReencrypt)               // caller re-saves encrypted
        assertTrue(opened.failedFields.isEmpty())

        // after migration the row is encrypted and no further migration is requested
        val migrated = codec.open(codec.seal(opened.secrets).secrets)
        assertEquals(elyBy, migrated.secrets)
        assertFalse(migrated.needsReencrypt)
    }

    @Test
    fun offlineAccountNeverRequestsMigration() {
        val opened = codec.open(offline)
        assertFalse(opened.needsReencrypt)
        assertEquals(offline, opened.secrets)
    }

    @Test
    fun undecryptableFieldsAreBlankedAndReportedIndividually() {
        val sealedWithOtherKey = AccountSecretsCodec(
            CredentialCodec { AesGcmCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() } }
        ).seal(elyBy).secrets
        val opened = codec.open(sealedWithOtherKey)
        assertEquals(listOf("accessToken", "clientToken", "otherPassword"), opened.failedFields)
        assertEquals("", opened.secrets.accessToken)
        assertEquals("", opened.secrets.otherPassword)
        assertEquals("0", opened.secrets.refreshToken) // placeholder still readable
    }

    @Test
    fun keystoreUnavailableKeepsPlaintextAndFlagsInsecure() {
        val noKeystore = AccountSecretsCodec(CredentialCodec { null })
        val sealed = noKeystore.seal(microsoft)
        assertTrue(sealed.isInsecure)
        assertEquals(microsoft, sealed.secrets)
        assertEquals(microsoft, noKeystore.open(sealed.secrets).secrets)
    }

    @Test
    fun placeholderOnlyAccountIsNotInsecureWithoutKeystore() {
        assertFalse(AccountSecretsCodec(CredentialCodec { null }).seal(offline).isInsecure)
    }

    @Test
    fun nullPasswordStaysNull() {
        assertNull(codec.open(codec.seal(microsoft).secrets).secrets.otherPassword)
    }

    /**
     * Guard: credentials are only encrypted inside AccountsManager, so nothing else may obtain
     * the DAO. (Room requires AppDatabase.accountDao() to stay public, hence this check.)
     */
    @Test
    fun accountDaoIsOnlyUsedByAccountsManager() {
        val srcRoot = listOf(File("src/main/java"), File("ZalithLauncher/src/main/java")).firstOrNull { it.isDirectory }
            ?: return // not running from a known working directory
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { it.name != "AccountsManager.kt" && it.name != "AppDatabase.kt" }
            .filter { it.readText().contains(".accountDao()") }
            .map { it.path }
            .toList()
        assertTrue("accountDao() must only be used by AccountsManager: $offenders", offenders.isEmpty())
    }
}
