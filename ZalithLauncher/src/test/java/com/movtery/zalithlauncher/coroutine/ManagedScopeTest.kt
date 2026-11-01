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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedScopeTest {
    @Test
    fun failedChildDoesNotCancelScopeOrLaterLaunches() = runBlocking {
        val scope = managedScope("ManagedScopeTest", Dispatchers.Default)
        try {
            scope.launch { throw IllegalStateException("boom") }.join()

            assertTrue("scope must stay active after a child failure", scope.isActive)

            val result = CompletableDeferred<Int>()
            scope.launch { result.complete(42) }
            assertEquals(42, withTimeout(5_000) { result.await() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failingChildDoesNotCancelRunningSibling() = runBlocking {
        val scope = managedScope("ManagedScopeTest", Dispatchers.Default)
        try {
            val siblingStarted = CompletableDeferred<Unit>()
            val siblingMayFinish = CompletableDeferred<Unit>()
            val sibling = scope.launch {
                siblingStarted.complete(Unit)
                siblingMayFinish.await()
            }
            withTimeout(5_000) { siblingStarted.await() }

            scope.launch { throw IllegalStateException("boom") }.join()

            assertTrue("sibling must not be cancelled", sibling.isActive)
            siblingMayFinish.complete(Unit)
            withTimeout(5_000) { sibling.join() }
            assertTrue(!sibling.isCancelled)
        } finally {
            scope.cancel()
        }
    }
}
