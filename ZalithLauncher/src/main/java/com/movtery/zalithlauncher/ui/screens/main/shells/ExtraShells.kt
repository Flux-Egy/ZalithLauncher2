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

package com.movtery.zalithlauncher.ui.screens.main.shells

import com.movtery.zalithlauncher.BuildKeys
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.theme.style.PixelHeadingFontFamily
import com.movtery.zalithlauncher.viewmodel.LocalBackgroundViewModel
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/** One navigation entry, shared by the layouts below */
private class ShellEntry(
    @param:DrawableRes val icon: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val badge: Boolean = false
)

@Composable
private fun primaryEntries(nav: ShellNavigation, destination: ShellDestination): List<ShellEntry> {
    val inDownload = destination == ShellDestination.DOWNLOAD || destination == ShellDestination.MODS ||
            destination == ShellDestination.MODPACKS || destination == ShellDestination.RESOURCE_PACKS
    return listOf(
        ShellEntry(R.drawable.ic_play_arrow_filled, stringResource(R.string.style_nav_play), destination == ShellDestination.HOME, nav.toHome),
        ShellEntry(R.drawable.ic_package_2_outlined, stringResource(R.string.style_nav_installations), destination == ShellDestination.INSTALLATIONS, nav.toInstallations),
        ShellEntry(R.drawable.ic_download_2_filled, stringResource(R.string.generic_download), inDownload, nav.toDownload),
        ShellEntry(R.drawable.ic_group_filled, stringResource(R.string.terracotta), destination == ShellDestination.MULTIPLAYER, nav.toMultiplayer),
        ShellEntry(R.drawable.ic_person_outlined, stringResource(R.string.style_nav_accounts), destination == ShellDestination.ACCOUNTS, nav.toAccounts)
    )
}

@Composable
private fun secondaryEntries(nav: ShellNavigation, destination: ShellDestination, taskRunning: Boolean): List<ShellEntry> = listOf(
    ShellEntry(R.drawable.ic_folder_filled, stringResource(R.string.style_nav_files), false, nav.openFileManager),
    ShellEntry(R.drawable.ic_assignment_filled, stringResource(R.string.main_task_menu), false, nav.toggleTasks, badge = !taskRunning),
    ShellEntry(R.drawable.ic_settings_filled, stringResource(R.string.generic_setting), destination == ShellDestination.SETTINGS, nav.toSettings)
)

@Composable
private fun BackButton(nav: ShellNavigation) {
    val back = goBack(nav.onScreenBack)
    IconButton(onClick = back) {
        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.generic_back))
    }
}

@Composable
private fun BadgeDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

// ===================================================================== Console

/**
 * Game-console home layout: account and clock on top, large round buttons along the
 * bottom (easy to hit with thumbs on a phone held in landscape).
 */
@Composable
fun ConsoleShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!inLauncherScreen) BackButton(nav)
            AccountChip(onClick = nav.toAccounts)
            Spacer(Modifier.weight(1f))
            ConsoleClock()
        }

        content(Modifier.fillMaxWidth().weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
        ) {
            (primaryEntries(nav, destination) + secondaryEntries(nav, destination, taskRunning)).forEach {
                ConsoleRoundButton(it)
            }
        }
    }
}

@Composable
private fun ConsoleClock() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val text = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ConsoleRoundButton(entry: ShellEntry) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .then(
                        if (entry.selected) Modifier.border(3.dp, colors.primary, CircleShape) else Modifier
                    ),
                shape = CircleShape,
                color = if (entry.selected) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (entry.selected) colors.onPrimaryContainer else colors.onSurface,
                onClick = entry.onClick
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(modifier = Modifier.size(26.dp), painter = painterResource(entry.icon), contentDescription = entry.label)
                }
            }
            if (entry.badge) BadgeDot(Modifier.align(Alignment.TopEnd))
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================== Material You

/** Material 3 navigation rail; colors come from the wallpaper (see LauncherStyleTheme) */
@Composable
fun MaterialYouShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = {
                if (!inLauncherScreen) BackButton(nav)
                AccountChip(onClick = nav.toAccounts, showName = false)
            }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                (primaryEntries(nav, destination) + secondaryEntries(nav, destination, taskRunning)).forEach { entry ->
                    NavigationRailItem(
                        selected = entry.selected,
                        onClick = entry.onClick,
                        icon = {
                            Box {
                                Icon(painter = painterResource(entry.icon), contentDescription = null)
                                if (entry.badge) BadgeDot(Modifier.align(Alignment.TopEnd))
                            }
                        },
                        label = { Text(text = entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

// ====================================================================== Glass

/**
 * Translucent panels floating over a soft gradient. When the user has set a launcher
 * background image, that image shows through instead (and is blurred behind cards when
 * background blur is enabled in the launcher settings).
 */
@Composable
fun GlassShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val hasUserBackground = LocalBackgroundViewModel.current?.isValid == true
    val dark = isLauncherInDarkTheme()
    val panel = if (dark) Color(0x40FFFFFF) else Color(0x73FFFFFF)
    val edge = if (dark) Color(0x4DFFFFFF) else Color(0xB3FFFFFF)

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasUserBackground) GlassBackdrop(dark)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(panel)
                    .border(1.dp, edge, MaterialTheme.shapes.extraLarge)
                    .padding(vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (primaryEntries(nav, destination) + secondaryEntries(nav, destination, taskRunning)).forEach { entry ->
                    Box {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.large,
                            color = if (entry.selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (entry.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            onClick = entry.onClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painter = painterResource(entry.icon), contentDescription = entry.label)
                            }
                        }
                        if (entry.badge) BadgeDot(Modifier.align(Alignment.TopEnd))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(panel)
                        .border(1.dp, edge, MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!inLauncherScreen) BackButton(nav)
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        text = BuildKeys.LAUNCHER_SHORT_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    AccountChip(onClick = nav.toAccounts)
                }
                content(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** Soft color blobs over a gradient (no image assets) */
@Composable
private fun GlassBackdrop(dark: Boolean) {
    val base = if (dark) listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    else listOf(Color(0xFFE0EAFC), Color(0xFFCFDEF3), Color(0xFFE8E2F7))
    val blobs = if (dark) listOf(Color(0x997F5AF0), Color(0x8022D3EE), Color(0x66F472B6))
    else listOf(Color(0x99A78BFA), Color(0x8067E8F9), Color(0x80F9A8D4))
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(base, start = Offset.Zero, end = Offset(size.width, size.height)))
        val r = size.minDimension * 0.65f
        listOf(
            Offset(size.width * 0.15f, size.height * 0.2f),
            Offset(size.width * 0.85f, size.height * 0.35f),
            Offset(size.width * 0.5f, size.height * 1.0f)
        ).forEachIndexed { i, c ->
            drawCircle(Brush.radialGradient(listOf(blobs[i], Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
}

// ================================================================ Retro pixel

/** Classic block-game menu vibe: beveled square buttons on an earthy pixel backdrop */
@Composable
fun RetroPixelShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RetroBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp)
            ) {
                Row(modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                    if (!inLauncherScreen) BackButton(nav)
                }
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = BuildKeys.LAUNCHER_SHORT_NAME.uppercase(),
                    style = TextStyle(
                        fontFamily = PixelHeadingFontFamily,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        color = Color(0xFFFFFF55),
                        shadow = Shadow(color = Color(0xFF3F3F15), offset = Offset(4f, 4f))
                    )
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    AccountChip(onClick = nav.toAccounts)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .width(196.dp)
                        .fillMaxHeight()
                        .padding(start = 10.dp, end = 6.dp, bottom = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (primaryEntries(nav, destination) + secondaryEntries(nav, destination, taskRunning)).forEach {
                        RetroButton(it)
                    }
                }
                content(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun RetroButton(entry: ShellEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind {
                val b = 2.dp.toPx()
                //外框
                drawRect(if (entry.selected) Color.White else Color.Black)
                //主体
                drawRect(Color(0xFF6F6F6F), topLeft = Offset(b, b), size = Size(size.width - 2 * b, size.height - 2 * b))
                //左上高光
                drawRect(Color(0xFFA8A8A8), topLeft = Offset(b, b), size = Size(size.width - 2 * b, b))
                drawRect(Color(0xFFA8A8A8), topLeft = Offset(b, b), size = Size(b, size.height - 2 * b))
                //右下阴影
                drawRect(Color(0xFF3A3A3A), topLeft = Offset(b, size.height - 2 * b), size = Size(size.width - 2 * b, b))
                drawRect(Color(0xFF3A3A3A), topLeft = Offset(size.width - 2 * b, b), size = Size(b, size.height - 2 * b))
            }
            .clickable(onClick = entry.onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = entry.label + if (entry.badge) " •" else "",
            style = TextStyle(
                fontFamily = PixelHeadingFontFamily,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                color = if (entry.selected) Color(0xFFFFFF55) else Color(0xFFE0E0E0),
                shadow = Shadow(color = Color(0xFF383838), offset = Offset(3f, 3f))
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Procedural earthy pixel noise, scaled up without filtering; darkened for readability */
@Composable
private fun RetroBackdrop() {
    val palette = remember {
        intArrayOf(0xFF5B3F2B.toInt(), 0xFF6B4A33.toInt(), 0xFF4E3524.toInt(), 0xFF7A563B.toInt(), 0xFF3F2A1C.toInt(), 0xFF664530.toInt())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val block = 6.dp.toPx().coerceAtLeast(1f)
                val w = (size.width / block).roundToInt().coerceAtLeast(1)
                val h = (size.height / block).roundToInt().coerceAtLeast(1)
                val pixels = IntArray(w * h) { i ->
                    val x = i % w
                    val y = i / w
                    //确定性哈希，保证每次绘制图案一致
                    var n = x * 374761393 + y * 668265263
                    n = (n xor (n ushr 13)) * 1274126177
                    palette[((n xor (n ushr 16)) and 0x7fffffff) % palette.size]
                }
                val image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                onDrawBehind {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(w, h),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.None
                    )
                    drawRect(Color(0x8C000000))
                }
            }
    )
}
