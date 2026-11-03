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

package com.movtery.zalithlauncher.game.advisor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.movtery.zalithlauncher.game.renderer.auto.GpuDetector
import com.movtery.zalithlauncher.path.createOkHttpClientBuilder
import com.movtery.zalithlauncher.utils.logging.Logger
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/** Collects the device facts the advisor needs */
object HardwareProfiler {
    fun snapshot(context: Context): HardwareSnapshot {
        val gpu = GpuDetector.cached()
        val memInfo = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        return HardwareSnapshot(
            gpuVendor = gpu?.vendor?.name ?: "UNKNOWN",
            gpuModel = gpu?.model,
            totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt(),
            vulkanSupported = gpu?.vulkanSupported,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            androidSdk = Build.VERSION.SDK_INT,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
        )
    }
}

/**
 * The recommendation matrix: the copy bundled in the APK, replaced by a newer copy
 * downloaded from the project's GitHub repository only if its Ed25519 signature
 * verifies against [PUBLIC_KEY]. The private key never leaves the maintainer's machine
 * (see scripts/sign-recommendations.sh).
 */
object RecommendationsRepository {
    private const val TAG = "Recommendations"

    /** Raw 32-byte Ed25519 public key, Base64 */
    private const val PUBLIC_KEY = "7BcJlsTHYxnttodpFWfZ6GjvAXt3MW/XbpPJ3t5UItQ="

    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/Flux-Egy/ZalithLauncher2/main/recommendations/recommendations.json"
    private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val publicKey: ByteArray by lazy { Base64.getDecoder().decode(PUBLIC_KEY) }

    private fun cacheDir(context: Context) = File(context.filesDir, "advisor")
    private fun cachedJson(context: Context) = File(cacheDir(context), "recommendations.json")
    private fun cachedSig(context: Context) = File(cacheDir(context), "recommendations.json.sig")

    private fun bundled(context: Context): Recommendations =
        context.assets.open("recommendations.json").use { input ->
            Recommendations.parse(input.readBytes().toString(Charsets.UTF_8))
        } ?: error("Bundled recommendations.json is invalid")

    /** The newest valid matrix available locally. Call off the main thread. */
    fun load(context: Context): Recommendations {
        val bundled = bundled(context)
        val cached = runCatching {
            val json = cachedJson(context).takeIf { it.isFile }?.readBytes() ?: return@runCatching null
            val sig = cachedSig(context).takeIf { it.isFile }?.readText() ?: return@runCatching null
            // re-verified on every load: a modified cache file is ignored
            Recommendations.parseSigned(json, sig, publicKey)
        }.getOrNull()
        return if (cached != null && cached.issued >= bundled.issued) cached else bundled
    }

    /**
     * Downloads the matrix and its signature at most once per [REFRESH_INTERVAL_MS].
     * Only a correctly signed, not older matrix is kept. Call off the main thread.
     */
    fun refreshIfStale(context: Context) {
        val json = cachedJson(context)
        val marker = File(cacheDir(context), "last_check")
        if (System.currentTimeMillis() - marker.lastModified() < REFRESH_INTERVAL_MS) return
        runCatching {
            cacheDir(context).mkdirs()
            marker.writeText(System.currentTimeMillis().toString())
            val body = fetch(REMOTE_URL) ?: return
            val sig = fetch("$REMOTE_URL.sig")?.toString(Charsets.UTF_8) ?: return
            val parsed = Recommendations.parseSigned(body, sig, publicKey)
            if (parsed == null) {
                Logger.warning(TAG, "Downloaded recommendations rejected: bad signature or format")
                return
            }
            if (parsed.issued < load(context).issued) return
            json.writeBytes(body)
            cachedSig(context).writeText(sig)
            Logger.info(TAG, "Recommendations updated (issued ${parsed.issued})")
        }.onFailure { Logger.info(TAG, "Recommendations not refreshed: ${it.message}") }
    }

    private fun fetch(url: String): ByteArray? {
        val client = createOkHttpClientBuilder().build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val out = ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > Recommendations.MAX_BYTES) return null
                }
            }
            return out.toByteArray()
        }
    }
}
