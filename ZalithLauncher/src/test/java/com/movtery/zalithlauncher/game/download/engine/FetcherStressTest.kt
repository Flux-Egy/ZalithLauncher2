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

package com.movtery.zalithlauncher.game.download.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 10 simultaneous downloads where every file's first response drops the connection
 * mid-body (what devices logged as "unexpected end of stream"). Every file must still
 * complete with the right content, via retry / Range resume on the OkHttp transport.
 */
class FetcherStressTest {
    private var server: MockWebServer? = null
    private var dir: File? = null

    @After
    fun tearDown() {
        runCatching { server?.close() }
        dir?.deleteRecursively()
    }

    private val files = (0 until 10).associate { i ->
        "/f$i" to ByteArray(512 * 1024).also { Random(100L + i).nextBytes(it) }
    }

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun `ten concurrent downloads survive mid-body disconnects`() = runBlocking {
        val firstSeen = ConcurrentHashMap.newKeySet<String>()
        val total = AtomicInteger()
        val rangeRegex = Regex("""bytes=(\d+)-""")
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    total.incrementAndGet()
                    val path = request.url.encodedPath
                    val content = files[path] ?: return MockResponse.Builder().code(404).build()
                    val etag = "\"${sha1(content).take(16)}\""
                    val range = request.headers["Range"]?.let { rangeRegex.find(it)?.groupValues?.get(1)?.toInt() }
                    val builder = MockResponse.Builder().addHeader("accept-ranges", "bytes").addHeader("ETag", etag)
                    if (firstSeen.add(path)) {
                        // first request for this file: send part of the body, then drop the socket
                        return builder.code(200)
                            .body(Buffer().write(content))
                            .throttleBody(64 * 1024, 5, TimeUnit.MILLISECONDS)
                            .onResponseBody(SocketEffect.CloseSocket())
                            .build()
                    }
                    return if (range != null && range in 1 until content.size) {
                        builder.code(206)
                            .addHeader("Content-Range", "bytes $range-${content.size - 1}/${content.size}")
                            .body(Buffer().write(content, range, content.size - range))
                            .build()
                    } else {
                        builder.code(200).body(Buffer().write(content)).build()
                    }
                }
            }
            start()
        }
        val work = Files.createTempDirectory("fetcher-stress").toFile().also { dir = it }

        withTimeout(120_000) {
            files.map { (path, content) ->
                async(Dispatchers.IO) {
                    val target = File(work, path.removePrefix("/") + ".bin")
                    Fetcher.downloadFile(listOf(server!!.url(path).toString()), target, sha1(content))
                    assertArrayEquals("content of $path", content, target.readBytes())
                }
            }.awaitAll()
        }
        // every file needed at least one extra request after its dropped connection
        assertTrue("requests: ${total.get()}", total.get() >= 20)
        // no temp files left behind
        assertTrue(work.listFiles()!!.all { it.name.endsWith(".bin") })
    }
}
