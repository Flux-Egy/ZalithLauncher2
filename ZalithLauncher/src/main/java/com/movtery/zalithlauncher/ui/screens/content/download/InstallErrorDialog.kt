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

package com.movtery.zalithlauncher.ui.screens.content.download

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.fadeEdge
import com.movtery.zalithlauncher.utils.logging.Logger

/**
 * Install/download failure dialog with recovery actions:
 * - Retry: runs the same installation again (resume-capable downloads continue)
 * - Resolve: for network failures, opens the system network settings
 */
@Composable
fun InstallErrorDialog(
    message: String,
    error: Throwable,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val network = error.isNetworkError()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.download_install_error_title)) },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fadeEdge(state = scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = stringResource(R.string.download_install_error_message))
                Text(text = message)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (network) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { Logger.warning("InstallErrorDialog", "Cannot open network settings", it) }
                    }) {
                        MarqueeText(text = stringResource(R.string.download_error_resolve_network))
                    }
                }
                TextButton(onClick = onDismiss) {
                    MarqueeText(text = stringResource(R.string.generic_close))
                }
            }
        },
        confirmButton = {
            if (onRetry != null) {
                Button(onClick = {
                    onDismiss()
                    onRetry()
                }) {
                    MarqueeText(text = stringResource(R.string.download_error_retry))
                }
            }
        }
    )
}

/** Connection-level failures, where checking the device's network is the useful next step */
fun Throwable.isNetworkError(): Boolean = generateSequence(this) { it.cause }.take(8).any {
    it is java.net.UnknownHostException || it is java.net.ConnectException ||
            it is java.net.SocketTimeoutException || it is java.nio.channels.UnresolvedAddressException ||
            it is java.net.NoRouteToHostException || it is javax.net.ssl.SSLException ||
            it::class.simpleName == "HttpRequestTimeoutException"
}
