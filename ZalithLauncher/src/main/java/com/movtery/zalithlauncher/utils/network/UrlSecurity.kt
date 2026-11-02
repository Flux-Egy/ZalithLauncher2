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

package com.movtery.zalithlauncher.utils.network

import java.net.URI

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

/**
 * Rewrites an `http://` URL to `https://` (same host, port-less authority, path and query).
 *
 * Cleartext HTTP is denied by the network security config, but some services still hand
 * out `http://` URLs that are also served over HTTPS, notably Yggdrasil texture URLs
 * (`http://textures.minecraft.net/...`, `http://ely.by/storage/skins/...`).
 *
 * Returned unchanged: non-http(s) URLs, loopback hosts (allowed to use cleartext), URLs with
 * an explicit port (an HTTPS endpoint on the same port is unlikely), and unparsable input.
 */
fun upgradeToHttps(url: String): String {
    if (!url.regionMatches(0, "http://", 0, 7, ignoreCase = true)) return url
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase() ?: return url
    if (host in LOOPBACK_HOSTS || uri.port != -1) return url
    return "https://" + url.substring(7)
}
