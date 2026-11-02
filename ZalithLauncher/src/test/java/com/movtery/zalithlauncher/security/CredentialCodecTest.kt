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

import com.movtery.zalithlauncher.security.CredentialCodec.Decoded
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CredentialCodecTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmCipher { key }
    private val codec = CredentialCodec { cipher }

    private val token = "eyJhbGciOiJIUzI1NiJ9.payload.signature-0123456789"

    @Test
    fun roundTripProducesPrefixedCiphertext() {
        val encoded = codec.encode(token)!!
        assertFalse(encoded.isInsecure)
        assertTrue(encoded.stored.startsWith(CredentialCodec.PREFIX))
        assertFalse("plaintext must not appear in storage", encoded.stored.contains(token))

        val decoded = codec.decode(encoded.stored) as Decoded.Value
        assertEquals(token, decoded.plaintext)
        assertFalse(decoded.isLegacyPlaintext)
    }

    @Test
    fun unicodePasswordRoundTrips() {
        val password = "كلمة-سر-密码-🔑"
        val decoded = codec.decode(codec.encode(password)!!.stored) as Decoded.Value
        assertEquals(password, decoded.plaintext)
    }

    @Test
    fun encryptionIsRandomized() {
        assertNotEquals(codec.encode(token)!!.stored, codec.encode(token)!!.stored)
    }

    @Test
    fun blobLayoutIsIvCiphertextTag() {
        val blob = Base64.getDecoder().decode(codec.encode("abc")!!.stored.removePrefix(CredentialCodec.PREFIX))
        assertEquals(AesGcmCipher.IV_LENGTH + 3 + AesGcmCipher.TAG_LENGTH_BYTES, blob.size)
        assertArrayEquals("abc".toByteArray(), cipher.decrypt(blob))
    }

    @Test
    fun legacyPlaintextIsReturnedAndFlagged() {
        val decoded = codec.decode(token) as Decoded.Value
        assertEquals(token, decoded.plaintext)
        assertTrue(decoded.isLegacyPlaintext)
    }

    @Test
    fun placeholdersAreNeverEncryptedNorFlaggedAsLegacy() {
        for (placeholder in listOf("", "0", "NULL", "null")) {
            val encoded = codec.encode(placeholder)!!
            assertEquals(placeholder, encoded.stored)
            assertFalse(encoded.isInsecure)
            val decoded = codec.decode(placeholder) as Decoded.Value
            assertEquals(placeholder, decoded.plaintext)
            assertFalse("placeholder '$placeholder' is not legacy", decoded.isLegacyPlaintext)
        }
    }

    @Test
    fun alreadyEncryptedValueIsNotDoubleEncrypted() {
        val stored = codec.encode(token)!!.stored
        assertEquals(stored, codec.encode(stored)!!.stored)
    }

    @Test
    fun nullPassesThrough() {
        assertNull(codec.encode(null))
        assertNull(codec.decode(null))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val stored = codec.encode(token)!!.stored
        val blob = Base64.getDecoder().decode(stored.removePrefix(CredentialCodec.PREFIX))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        val tampered = CredentialCodec.PREFIX + Base64.getEncoder().encodeToString(blob)
        assertTrue(codec.decode(tampered) is Decoded.Failed)
    }

    @Test
    fun wrongKeyFails() {
        val stored = codec.encode(token)!!.stored
        val other = CredentialCodec { AesGcmCipher { newKey() } }
        assertTrue(other.decode(stored) is Decoded.Failed)
    }

    @Test
    fun malformedOrTruncatedPayloadFails() {
        assertTrue(codec.decode(CredentialCodec.PREFIX + "%%%not-base64%%%") is Decoded.Failed)
        assertTrue(codec.decode(CredentialCodec.PREFIX + Base64.getEncoder().encodeToString(ByteArray(10))) is Decoded.Failed)
        assertTrue(codec.decode(CredentialCodec.PREFIX) is Decoded.Failed)
    }

    @Test
    fun unavailableKeystoreFallsBackToPlaintextAndFlagsInsecure() {
        val noKeystore = CredentialCodec { null }
        val encoded = noKeystore.encode(token)!!
        assertEquals(token, encoded.stored)
        assertTrue(encoded.isInsecure)
        // reading back plaintext still works (login flow not broken)
        assertEquals(token, (noKeystore.decode(encoded.stored) as Decoded.Value).plaintext)
    }

    @Test
    fun throwingCipherProviderFallsBackToPlaintext() {
        val broken = CredentialCodec { throw IllegalStateException("keystore exploded") }
        val encoded = broken.encode(token)!!
        assertEquals(token, encoded.stored)
        assertTrue(encoded.isInsecure)
    }

    @Test
    fun cipherFailingAtEncryptTimeFallsBackToPlaintext() {
        val failing = CredentialCodec {
            object : SecretCipher {
                override fun encrypt(plaintext: ByteArray): ByteArray = throw java.security.ProviderException("hw")
                override fun decrypt(blob: ByteArray): ByteArray = throw java.security.ProviderException("hw")
            }
        }
        val encoded = failing.encode(token)!!
        assertEquals(token, encoded.stored)
        assertTrue(encoded.isInsecure)
    }

    @Test
    fun encryptedValueWithoutKeystoreFailsInsteadOfLeakingCiphertext() {
        val stored = codec.encode(token)!!.stored
        assertTrue(CredentialCodec { null }.decode(stored) is Decoded.Failed)
    }
}
