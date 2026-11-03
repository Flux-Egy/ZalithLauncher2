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

package com.movtery.zalithlauncher.game.download.assets.platform.curseforge

import java.net.URI

/**
 * A user-configured CurseForge API proxy: a server that forwards requests to
 * api.curseforge.com and adds the API key itself (see tools/curseforge-proxy). The
 * key then never has to be stored in the app. The launcher sends no key to the proxy.
 */
object CurseForgeProxy {

    /**
     * Validates a proxy base URL (the equivalent of "https://api.curseforge.com") and
     * returns the API root ("<base>/v1"), or null if the URL is not acceptable.
     * Only https, no user info, query or fragment.
     */
    fun apiRoot(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }
}
