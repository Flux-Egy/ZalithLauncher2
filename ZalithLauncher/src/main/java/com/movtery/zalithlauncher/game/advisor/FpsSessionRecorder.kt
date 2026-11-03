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

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

/**
 * FPS measured during the last session of an instance (not an estimate).
 *
 * @param median median of the samples taken after warm-up
 * @param low 10th percentile ("1 in 10 seconds was this slow or slower")
 * @param config renderer / render scale / upscaler the numbers were measured with
 */
data class FpsRecord(
    val median: Int,
    val low: Int,
    val samples: Int,
    val config: String,
    val timestamp: Long
) {
    companion object {
        /** Median and 10th percentile; null when there are too few samples to be meaningful */
        fun fromSamples(samples: List<Int>, config: String, timestamp: Long, minSamples: Int = 10): FpsRecord? {
            val valid = samples.filter { it > 0 }.sorted()
            if (valid.size < minSamples) return null
            val median = if (valid.size % 2 == 1) valid[valid.size / 2]
            else (valid[valid.size / 2 - 1] + valid[valid.size / 2]) / 2
            val low = valid[((valid.size - 1) * 10) / 100]
            return FpsRecord(median, low, valid.size, config, timestamp)
        }

        fun toJson(r: FpsRecord): String = JsonObject().apply {
            addProperty("median", r.median)
            addProperty("low", r.low)
            addProperty("samples", r.samples)
            addProperty("config", r.config)
            addProperty("timestamp", r.timestamp)
        }.toString()

        fun fromJson(text: String): FpsRecord? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            FpsRecord(
                median = o.get("median").asInt,
                low = o.get("low").asInt,
                samples = o.get("samples").asInt,
                config = o.get("config").asString,
                timestamp = o.get("timestamp").asLong
            )
        }.getOrNull()
    }
}

/**
 * Stores the last measured session per instance in filesDir/perf (shared by the game
 * and launcher processes; written by the game process only).
 */
object FpsSessionStore {
    private const val TAG = "FpsSessionStore"

    private fun file(context: Context, versionName: String): File {
        val safe = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        return File(File(context.filesDir, "perf"), "$safe.json")
    }

    fun save(context: Context, versionName: String, record: FpsRecord) {
        runCatching {
            val f = file(context, versionName)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(FpsRecord.toJson(record))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Logger.warning(TAG, "Could not save FPS record", it) }
    }

    fun load(context: Context, versionName: String): FpsRecord? =
        runCatching { file(context, versionName).takeIf { it.isFile }?.readText() }.getOrNull()
            ?.let { FpsRecord.fromJson(it) }
}
