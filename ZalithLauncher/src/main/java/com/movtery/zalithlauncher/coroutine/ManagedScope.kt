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

package com.movtery.zalithlauncher.coroutine

import android.util.Log
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Exception handler that logs uncaught coroutine failures instead of letting them
 * reach the thread's default uncaught-exception handler (which crashes the launcher).
 */
fun loggingExceptionHandler(tag: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { context, throwable ->
        val name = context[CoroutineName]?.name ?: tag
        val message = "Uncaught exception in coroutine \"$name\""
        //Logger drops messages until it is initialised, so always mirror to logcat
        Log.e(tag, message, throwable)
        Logger.error(tag, message, throwable)
    }

/**
 * Creates a long-lived scope for process-wide managers (singletons).
 *
 * - [SupervisorJob]: one failed child does not cancel the scope or its siblings,
 *   so later `launch` calls keep working.
 * - [loggingExceptionHandler]: a failure in a background refresh is logged instead
 *   of crashing the whole launcher.
 *
 * Do NOT use this for scopes whose callers rely on a failure terminating the process
 * or on a completion callback always running (e.g. JvmService / JVMSocketServer):
 * catch and report errors explicitly there instead.
 */
fun managedScope(
    name: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): CoroutineScope = CoroutineScope(
    SupervisorJob() + dispatcher + CoroutineName(name) + loggingExceptionHandler(name)
)
