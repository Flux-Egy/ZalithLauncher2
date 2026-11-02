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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.getAccountTypeName
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import com.movtery.zalithlauncher.ui.theme.style.PixelHeadingFontFamily

/** Navigation actions shared by all launcher shells (provided by MainScreen) */
class ShellNavigation(
    val toHome: () -> Unit,
    val toInstallations: () -> Unit,
    val toAccounts: () -> Unit,
    val toDownload: () -> Unit,
    val toMods: () -> Unit,
    val toModpacks: () -> Unit,
    val toResourcePacks: () -> Unit,
    val toMultiplayer: () -> Unit,
    val toSettings: () -> Unit,
    val openFileManager: () -> Unit,
    val toggleTasks: () -> Unit,
    val onScreenBack: () -> Unit
)

/** Which destination is currently shown, so shells can highlight it */
enum class ShellDestination { HOME, INSTALLATIONS, ACCOUNTS, DOWNLOAD, MODS, MODPACKS, RESOURCE_PACKS, MULTIPLAYER, SETTINGS, OTHER }

@Composable
internal fun goBack(onScreenBack: () -> Unit): () -> Unit {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return { dispatcher?.onBackPressed() ?: onScreenBack() }
}

@Composable
internal fun AccountChip(onClick: () -> Unit, showName: Boolean = true) {
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val current = account
        if (current != null) {
            PlayerFace(account = current, avatarSize = 28.dp)
            if (showName) {
                Column {
                    Text(
                        text = current.username,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = getAccountTypeName(current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        } else {
            Icon(painter = painterResource(R.drawable.ic_person_outlined), contentDescription = null)
            if (showName) Text(text = stringResource(R.string.style_sign_in), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// =================================================================== PC launcher

/**
 * Desktop-launcher layout: left sidebar navigation, a top bar with the account, and the
 * selected screen in the large central area.
 */
@Composable
fun PcLauncherShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(208.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(vertical = 12.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                text = "ZALITH",
                fontFamily = PixelHeadingFontFamily,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            PcSidebarItem(R.drawable.ic_play_arrow_filled, stringResource(R.string.style_nav_play), destination == ShellDestination.HOME, nav.toHome)
            PcSidebarItem(R.drawable.ic_package_2_outlined, stringResource(R.string.style_nav_installations), destination == ShellDestination.INSTALLATIONS, nav.toInstallations)
            PcSidebarItem(R.drawable.ic_person_outlined, stringResource(R.string.style_nav_accounts), destination == ShellDestination.ACCOUNTS, nav.toAccounts)
            PcSidebarItem(R.drawable.ic_download_2_filled, stringResource(R.string.generic_download), destination == ShellDestination.DOWNLOAD || destination == ShellDestination.MODS || destination == ShellDestination.MODPACKS || destination == ShellDestination.RESOURCE_PACKS, nav.toDownload)
            PcSidebarItem(R.drawable.ic_group_filled, stringResource(R.string.terracotta), destination == ShellDestination.MULTIPLAYER, nav.toMultiplayer)
            Spacer(Modifier.weight(1f))
            PcSidebarItem(R.drawable.ic_folder_filled, stringResource(R.string.style_nav_files), false, nav.openFileManager)
            PcSidebarItem(
                icon = R.drawable.ic_assignment_filled,
                label = stringResource(R.string.main_task_menu),
                selected = false,
                onClick = nav.toggleTasks,
                badge = !taskRunning
            )
            PcSidebarItem(R.drawable.ic_settings_filled, stringResource(R.string.generic_setting), destination == ShellDestination.SETTINGS, nav.toSettings)
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!inLauncherScreen) {
                    val back = goBack(nav.onScreenBack)
                    IconButton(onClick = back) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.generic_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                AccountChip(onClick = nav.toAccounts)
            }
            content(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun PcSidebarItem(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (selected) colors.surfaceContainerHigh else colors.surfaceContainerLowest)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // selection accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (selected) colors.primary else colors.surfaceContainerLowest)
        )
        Spacer(Modifier.width(16.dp))
        Box {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.onSurface else colors.onSurfaceVariant
            )
            if (badge) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ================================================================ Modrinth style

/**
 * Modrinth-app-like layout: a top bar with tab navigation, a prominent search entry and
 * the account, above the selected screen.
 */
@Composable
fun ModrinthShell(
    nav: ShellNavigation,
    destination: ShellDestination,
    inLauncherScreen: Boolean,
    taskRunning: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!inLauncherScreen) {
                    val back = goBack(nav.onScreenBack)
                    IconButton(onClick = back) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.generic_back))
                    }
                }
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = "Zalith",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModrinthTab(stringResource(R.string.style_nav_home), destination == ShellDestination.HOME, nav.toHome)
                    ModrinthTab(stringResource(R.string.style_nav_mods), destination == ShellDestination.MODS, nav.toMods)
                    ModrinthTab(stringResource(R.string.style_nav_modpacks), destination == ShellDestination.MODPACKS, nav.toModpacks)
                    ModrinthTab(stringResource(R.string.style_nav_resource_packs), destination == ShellDestination.RESOURCE_PACKS, nav.toResourcePacks)
                    ModrinthTab(stringResource(R.string.style_nav_installations), destination == ShellDestination.INSTALLATIONS, nav.toInstallations)
                    ModrinthTab(stringResource(R.string.terracotta), destination == ShellDestination.MULTIPLAYER, nav.toMultiplayer)
                }
                ModrinthSearchEntry(onClick = nav.toMods)
                ModrinthIconButton(R.drawable.ic_folder_filled, stringResource(R.string.style_nav_files), nav.openFileManager)
                Box {
                    ModrinthIconButton(R.drawable.ic_assignment_filled, stringResource(R.string.main_task_menu), nav.toggleTasks)
                    if (!taskRunning) {
                        Box(
                            modifier = Modifier
                                .padding(10.dp)
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                ModrinthIconButton(R.drawable.ic_settings_filled, stringResource(R.string.generic_setting), nav.toSettings)
                AccountChip(onClick = nav.toAccounts, showName = false)
            }
        }
        content(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun RowScope.ModrinthTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.align(Alignment.CenterVertically),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        onClick = onClick
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ModrinthSearchEntry(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(220.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(modifier = Modifier.size(18.dp), painter = painterResource(R.drawable.ic_search), contentDescription = null)
            Text(
                text = stringResource(R.string.style_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModrinthIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painter = painterResource(icon), contentDescription = description)
    }
}
