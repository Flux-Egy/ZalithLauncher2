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

package com.movtery.zalithlauncher.context

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Which of the launcher's processes (see android:process in AndroidManifest.xml) is running.
 */
enum class ProcessRole(val tag: String) {
    /** Launcher UI (default process) */
    MAIN("main"),

    /** ":game" - VMActivity, GameService, TerracottaVPNService */
    GAME("game"),

    /** ":jvm" - JvmService (installers, jar tasks) */
    JVM("jvm"),

    /** ":filemanager" - FileManagerActivity, FileManagerEventService */
    FILE_MANAGER("filemanager"),

    /** Any other process name; treated like MAIN for initialisation (safe default) */
    OTHER("other");

    companion object {
        /**
         * Pure mapping from a process name to a role (unit-testable).
         * Works with any applicationId suffix (".v2", ".debug").
         */
        fun fromProcessName(processName: String?, packageName: String): ProcessRole {
            if (processName.isNullOrBlank() || processName == packageName) return MAIN
            val suffix = processName.substringAfter(':', missingDelimiterValue = "")
            return when (suffix) {
                "game" -> GAME
                "jvm" -> JVM
                "filemanager" -> FILE_MANAGER
                "" -> MAIN
                else -> OTHER
            }
        }

        @Volatile
        private var cached: ProcessRole? = null

        fun current(context: Context): ProcessRole {
            cached?.let { return it }
            return fromProcessName(currentProcessName(), context.packageName).also { cached = it }
        }

        private fun currentProcessName(): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                runCatching {
                    File("/proc/self/cmdline").readText().trimEnd('\u0000').substringBefore('\u0000')
                }.getOrNull()
            }
    }
}
