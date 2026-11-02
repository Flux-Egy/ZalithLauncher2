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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ed25519Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // RFC 8032 section 7.1, TEST 1 and TEST 2
    private val pk1 = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    private val sig1 = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
    private val pk2 = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
    private val sig2 = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")

    // Generated with: openssl genpkey -algorithm ed25519; openssl pkeyutl -sign -rawin
    private val pk3 = hex("d41c7ef7160834c50d04a335b72235e1d1bc326b3a8d5716060bcf47fee29c64")
    private val sig3 = hex("2f3f810d023c99ffe4f023425a51ce470c15a474c36e65fdec4eb3af92a81ad3d82de0b0b52bfab68bb7b3806612d17beca3b6a70a981b0c6aa7f7b6da67eb01")
    private val msg3 = hex("7b2276657273696f6e223a312c2268656c6c6f223a2277c3b6726c64227d0a")

    @Test
    fun rfc8032Vectors() {
        assertTrue(Ed25519.verify(pk1, ByteArray(0), sig1))
        assertTrue(Ed25519.verify(pk2, byteArrayOf(0x72), sig2))
    }

    @Test
    fun opensslSignature() {
        assertTrue(Ed25519.verify(pk3, msg3, sig3))
    }

    @Test
    fun rejectsTampering() {
        assertFalse(Ed25519.verify(pk2, byteArrayOf(0x73), sig2))            // other message
        assertFalse(Ed25519.verify(pk1, byteArrayOf(0x72), sig2))            // other key
        val badSig = sig3.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(Ed25519.verify(pk3, msg3, badSig))                       // flipped bit in R
        val badS = sig3.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        assertFalse(Ed25519.verify(pk3, msg3, badS))                         // flipped bit in S
        val badMsg = msg3.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() }
        assertFalse(Ed25519.verify(pk3, badMsg, sig3))
    }

    @Test
    fun rejectsMalformedInput() {
        assertFalse(Ed25519.verify(ByteArray(31), msg3, sig3))
        assertFalse(Ed25519.verify(pk3, msg3, ByteArray(63)))
        // S >= group order must be rejected (malleability)
        val highS = sig3.copyOf().also { for (i in 32 until 64) it[i] = 0xFF.toByte() }
        assertFalse(Ed25519.verify(pk3, msg3, highS))
    }
}
