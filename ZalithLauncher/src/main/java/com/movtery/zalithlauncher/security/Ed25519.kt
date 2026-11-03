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

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signature verification (RFC 8032), ported from the reference
 * implementation in RFC 8032 section 6.
 *
 * Android only ships Ed25519 in recent releases, and this project supports API 26,
 * so a small self-contained verifier is used. It is not constant-time, which does
 * not matter for verifying public data (no secrets are involved). Only verification
 * is implemented; signing happens offline (see scripts/sign-recommendations.sh).
 */
object Ed25519 {
    // BigInteger.TWO is not available on older Android versions
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = TWO.pow(255) - BigInteger.valueOf(19)
    private val Q: BigInteger = TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")
    private val D: BigInteger = (BigInteger.valueOf(-121665) * inv(BigInteger.valueOf(121666))).mod(P)
    private val SQRT_M1: BigInteger = TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

    /** Extended homogeneous coordinates (X, Y, Z, T) */
    private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val G: Point = run {
        val gy = (BigInteger.valueOf(4) * inv(BigInteger.valueOf(5))).mod(P)
        val gx = recoverX(gy, 0)!!
        Point(gx, gy, BigInteger.ONE, (gx * gy).mod(P))
    }

    private fun inv(x: BigInteger): BigInteger = x.modPow(P - TWO, P)

    private fun add(a: Point, b: Point): Point {
        val aa = ((a.y - a.x) * (b.y - b.x)).mod(P)
        val bb = ((a.y + a.x) * (b.y + b.x)).mod(P)
        val cc = (TWO * a.t * b.t * D).mod(P)
        val dd = (TWO * a.z * b.z).mod(P)
        val e = bb - aa
        val f = dd - cc
        val g = dd + cc
        val h = bb + aa
        return Point((e * f).mod(P), (g * h).mod(P), (f * g).mod(P), (e * h).mod(P))
    }

    private fun mul(scalar: BigInteger, point: Point): Point {
        var s = scalar
        var p = point
        var q = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
        while (s.signum() > 0) {
            if (s.testBit(0)) q = add(q, p)
            p = add(p, p)
            s = s.shiftRight(1)
        }
        return q
    }

    private fun equal(a: Point, b: Point): Boolean =
        (a.x * b.z - b.x * a.z).mod(P).signum() == 0 &&
                (a.y * b.z - b.y * a.z).mod(P).signum() == 0

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val x2 = ((y * y - BigInteger.ONE) * inv(D * y * y + BigInteger.ONE)).mod(P)
        if (x2.signum() == 0) return if (sign != 0) null else BigInteger.ZERO
        var x = x2.modPow((P + BigInteger.valueOf(3)) / BigInteger.valueOf(8), P)
        if ((x * x - x2).mod(P).signum() != 0) x = (x * SQRT_M1).mod(P)
        if ((x * x - x2).mod(P).signum() != 0) return null
        if ((if (x.testBit(0)) 1 else 0) != sign) x = P - x
        return x
    }

    private fun decompress(bytes: ByteArray): Point? {
        if (bytes.size != 32) return null
        var y = littleEndian(bytes)
        val sign = if (y.testBit(255)) 1 else 0
        y = y.clearBit(255)
        val x = recoverX(y, sign) ?: return null
        return Point(x, y, BigInteger.ONE, (x * y).mod(P))
    }

    private fun littleEndian(bytes: ByteArray): BigInteger = BigInteger(1, bytes.reversedArray())

    /**
     * @param publicKey 32-byte public key
     * @param signature 64-byte signature
     * @return true only if [signature] is a valid signature of [message] by [publicKey]
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val a = decompress(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decompress(rBytes) ?: return false
        val s = littleEndian(signature.copyOfRange(32, 64))
        if (s >= Q) return false
        val digest = MessageDigest.getInstance("SHA-512").apply {
            update(rBytes)
            update(publicKey)
            update(message)
        }.digest()
        val h = littleEndian(digest).mod(Q)
        return equal(mul(s, G), add(r, mul(h, a)))
    }
}
