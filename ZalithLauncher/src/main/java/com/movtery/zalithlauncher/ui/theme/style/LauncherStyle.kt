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

package com.movtery.zalithlauncher.ui.theme.style

import com.movtery.zalithlauncher.ui.theme.customLight
import com.movtery.zalithlauncher.ui.theme.customDark
import com.movtery.zalithlauncher.setting.AllSettings
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.enums.isLauncherInDarkTheme
import com.movtery.zalithlauncher.ui.theme.AppTypography

/**
 * Overall launcher look. Selected in Settings > Launcher > Appearance.
 *
 * - [ZALITH_DEFAULT]: the original Zalith UI, unchanged.
 * - [PC_LAUNCHER]: desktop-launcher layout (left sidebar + account bar), always dark,
 *   pixel-style headings.
 * - [MODRINTH_STYLE]: top tab bar with a search entry, clean surfaces, green accent,
 *   large rounded corners; follows the light/dark setting.
 * - [CONSOLE]: game-console home screen: account and clock on top, large round
 *   navigation buttons along the bottom, big touch targets; always dark.
 * - [MATERIAL_YOU]: Material 3 navigation rail with colors taken from the wallpaper
 *   (Android 12+; older versions keep the launcher's color theme).
 * - [GLASS]: translucent panels over a soft gradient or the user's background image
 *   (the image is blurred behind panels when background blur is enabled).
 * - [RETRO_PIXEL]: classic block-game menu vibe: pixel font, square beveled buttons,
 *   procedurally drawn earthy backdrop. No game assets are used.
 * - [CUSTOM]: user-defined: any of the layouts above with the user's primary/secondary
 *   colors, corner radius and font; exportable as JSON (see CustomThemeCodec).
 *
 * All styles host the same screens, so every feature works in every style.
 */
enum class LauncherStyle {
    ZALITH_DEFAULT,
    PC_LAUNCHER,
    MODRINTH_STYLE,
    CONSOLE,
    MATERIAL_YOU,
    GLASS,
    RETRO_PIXEL,
    CUSTOM
}

/** The layout actually shown: for [LauncherStyle.CUSTOM], the layout chosen in the custom theme */
@Composable
fun effectiveLauncherLayout(style: LauncherStyle): LauncherStyle =
    if (style == LauncherStyle.CUSTOM) {
        AllSettings.customThemeLayout.state.takeIf { it != LauncherStyle.CUSTOM } ?: LauncherStyle.ZALITH_DEFAULT
    } else style

fun ThemeFont.toFontFamily(): FontFamily = when (this) {
    ThemeFont.DEFAULT -> FontFamily.Default
    ThemeFont.SANS_SERIF -> FontFamily.SansSerif
    ThemeFont.SERIF -> FontFamily.Serif
    ThemeFont.MONOSPACE -> FontFamily.Monospace
    ThemeFont.CURSIVE -> FontFamily.Cursive
    ThemeFont.PIXEL -> PixelHeadingFontFamily
}

private fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

/** Material shapes scaled from one radius (extraLarge = 2x, extraSmall = 1/3) */
private fun shapesFromRadius(radius: Int): Shapes {
    val r = radius.coerceIn(0, 32)
    return Shapes(
        extraSmall = RoundedCornerShape((r / 3f).dp),
        small = RoundedCornerShape((r * 2 / 3f).dp),
        medium = RoundedCornerShape(r.dp),
        large = RoundedCornerShape((r * 4 / 3f).dp),
        extraLarge = RoundedCornerShape((r * 2).dp)
    )
}

/** Current launcher style for the subtree (provided by MainScreen) */
val LocalLauncherStyle = staticCompositionLocalOf { LauncherStyle.ZALITH_DEFAULT }

/**
 * When non-null, forces [isLauncherInDarkTheme] for the subtree. Used by styles with a
 * fixed light/dark appearance, so helpers such as itemColor() stay consistent with the
 * style's color scheme.
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/** Pixel-style heading font (Pixelify Sans, SIL Open Font License 1.1) */
val PixelHeadingFontFamily = FontFamily(Font(R.font.pixelify_sans, FontWeight.Normal))

// ---------------------------------------------------------------- PC launcher

/** Dark, game-like palette with the classic green "play" accent */
private val pcLauncherColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF3C8527),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A5E1B),
    onPrimaryContainer = Color(0xFFD6F5C8),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFE8C547),
    onTertiary = Color(0xFF241A00),
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF222222),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF313131),
    onSurfaceVariant = Color(0xFFC8C8C8),
    surfaceDim = Color(0xFF181818),
    surfaceBright = Color(0xFF2E2E2E),
    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF)
)

/** Blocky, low-radius shapes */
private val pcLauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

/** Pixel font for headings and titles; body text keeps the readable default font */
private val pcLauncherTypography: Typography = AppTypography.copy(
    displayLarge = AppTypography.displayLarge.copy(fontFamily = PixelHeadingFontFamily),
    displayMedium = AppTypography.displayMedium.copy(fontFamily = PixelHeadingFontFamily),
    displaySmall = AppTypography.displaySmall.copy(fontFamily = PixelHeadingFontFamily),
    headlineLarge = AppTypography.headlineLarge.copy(fontFamily = PixelHeadingFontFamily),
    headlineMedium = AppTypography.headlineMedium.copy(fontFamily = PixelHeadingFontFamily),
    headlineSmall = AppTypography.headlineSmall.copy(fontFamily = PixelHeadingFontFamily),
    titleLarge = AppTypography.titleLarge.copy(fontFamily = PixelHeadingFontFamily),
    titleMedium = AppTypography.titleMedium.copy(fontFamily = PixelHeadingFontFamily)
)

// ----------------------------------------------------------- Modrinth style

/** Brand-like green on clean light surfaces */
private val modrinthLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF00AF5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F8E2),
    onPrimaryContainer = Color(0xFF00391D),
    secondary = Color(0xFF5B6270),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E9EF),
    onSecondaryContainer = Color(0xFF1A1D23),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1D23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF5B6270),
    surfaceDim = Color(0xFFE6E8EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFEEF0F3),
    surfaceContainerHighest = Color(0xFFE6E9EE),
    outline = Color(0xFFC8CDD5),
    outlineVariant = Color(0xFFE1E4E9)
)

/** Same identity on dark surfaces */
private val modrinthDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF1BD96A),
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF0E5A30),
    onPrimaryContainer = Color(0xFFB6F5CF),
    secondary = Color(0xFFB0BAC5),
    onSecondary = Color(0xFF16181C),
    secondaryContainer = Color(0xFF33363D),
    onSecondaryContainer = Color(0xFFE6E8EB),
    background = Color(0xFF16181C),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1E2025),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF33363D),
    onSurfaceVariant = Color(0xFFB0BAC5),
    surfaceDim = Color(0xFF121418),
    surfaceBright = Color(0xFF26292F),
    surfaceContainerLowest = Color(0xFF101215),
    surfaceContainerLow = Color(0xFF1A1C21),
    surfaceContainer = Color(0xFF16181C),
    surfaceContainerHigh = Color(0xFF2A2D33),
    surfaceContainerHighest = Color(0xFF33363D),
    outline = Color(0xFF4A4F59),
    outlineVariant = Color(0xFF33363D)
)

/** Generous rounding */
private val modrinthShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Default font with bolder titles */
private val modrinthTypography: Typography = AppTypography.copy(
    titleLarge = AppTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = AppTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = AppTypography.headlineSmall.copy(fontWeight = FontWeight.Bold)
)

// ------------------------------------------------------------------ Console

/** Deep charcoal with a bright accent, high contrast for living-room readability */
private val consoleColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF2FD566),
    onPrimary = Color(0xFF00210C),
    primaryContainer = Color(0xFF145C2E),
    onPrimaryContainer = Color(0xFFC6F7D5),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF062E6F),
    secondaryContainer = Color(0xFF2B3A55),
    onSecondaryContainer = Color(0xFFD8E2FF),
    background = Color(0xFF101114),
    onBackground = Color(0xFFF1F2F4),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFF1F2F4),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFC4C7CE),
    surfaceDim = Color(0xFF0C0D10),
    surfaceBright = Color(0xFF24262B),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF141519),
    surfaceContainer = Color(0xFF101114),
    surfaceContainerHigh = Color(0xFF22242A),
    surfaceContainerHighest = Color(0xFF2C2E34),
    outline = Color(0xFF5C6068),
    outlineVariant = Color(0xFF34373D)
)

/** Pill-like, very round shapes */
private val consoleShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Larger labels for distance reading */
private val consoleTypography: Typography = AppTypography.copy(
    titleLarge = AppTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = AppTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

// -------------------------------------------------------------------- Glass

/** Translucent surfaces; the shell draws a backdrop behind them */
private fun glassColors(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFF9FD4FF),
        onPrimary = Color(0xFF00324F),
        primaryContainer = Color(0x664A8CC8),
        onPrimaryContainer = Color(0xFFE2F2FF),
        secondary = Color(0xFFE0B8FF),
        onSecondary = Color(0xFF3B0A5A),
        secondaryContainer = Color(0x667A4FA0),
        onSecondaryContainer = Color(0xFFF5E6FF),
        background = Color(0x99101622),
        onBackground = Color(0xFFF4F6FA),
        surface = Color(0x59101622),
        onSurface = Color(0xFFF4F6FA),
        surfaceVariant = Color(0x4DFFFFFF),
        onSurfaceVariant = Color(0xFFDCE2EC),
        surfaceDim = Color(0x40000000),
        surfaceBright = Color(0xCC1C2433),
        surfaceContainerLowest = Color(0x1AFFFFFF),
        surfaceContainerLow = Color(0x26FFFFFF),
        surfaceContainer = Color(0x66101622),
        surfaceContainerHigh = Color(0x33FFFFFF),
        surfaceContainerHighest = Color(0x40FFFFFF),
        outline = Color(0x66FFFFFF),
        outlineVariant = Color(0x33FFFFFF)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF1F5FA8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0x80BFDBFF),
        onPrimaryContainer = Color(0xFF00213F),
        secondary = Color(0xFF6B3FA0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0x80E4CCFF),
        onSecondaryContainer = Color(0xFF250046),
        background = Color(0x99FFFFFF),
        onBackground = Color(0xFF111827),
        surface = Color(0x99FFFFFF),
        onSurface = Color(0xFF111827),
        surfaceVariant = Color(0x80FFFFFF),
        onSurfaceVariant = Color(0xFF374151),
        surfaceDim = Color(0x66FFFFFF),
        surfaceBright = Color(0xD9FFFFFF),
        surfaceContainerLowest = Color(0x4DFFFFFF),
        surfaceContainerLow = Color(0x66FFFFFF),
        surfaceContainer = Color(0x80FFFFFF),
        surfaceContainerHigh = Color(0x8CFFFFFF),
        surfaceContainerHighest = Color(0x99FFFFFF),
        outline = Color(0x99FFFFFF),
        outlineVariant = Color(0x66FFFFFF)
    )
}

private val glassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// --------------------------------------------------------------- Retro pixel

/** Stone-gray UI over earthy tones, yellow highlight text */
private val retroColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFFF55),
    onPrimary = Color(0xFF1E1E1E),
    primaryContainer = Color(0xFF6E6E6E),
    onPrimaryContainer = Color(0xFFFFFF55),
    secondary = Color(0xFF7FB238),
    onSecondary = Color(0xFF102000),
    secondaryContainer = Color(0xFF4A6B22),
    onSecondaryContainer = Color(0xFFE6FFC8),
    background = Color(0xFF3B2A1E),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF2B2B2B),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF555555),
    onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceDim = Color(0xFF1E1E1E),
    surfaceBright = Color(0xE6373737),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xE6262626),
    surfaceContainer = Color(0xF02B2B2B),
    surfaceContainerHigh = Color(0xE6404040),
    surfaceContainerHighest = Color(0xE64C4C4C),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFF1E1E1E),
    error = Color(0xFFFF5555),
    onError = Color(0xFF000000)
)

/** Square corners everywhere */
private val retroShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

/** Pixel font throughout (headings and body) */
private val retroTypography: Typography = with(AppTypography) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = PixelHeadingFontFamily),
        displayMedium = displayMedium.copy(fontFamily = PixelHeadingFontFamily),
        displaySmall = displaySmall.copy(fontFamily = PixelHeadingFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = PixelHeadingFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = PixelHeadingFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = PixelHeadingFontFamily),
        titleLarge = titleLarge.copy(fontFamily = PixelHeadingFontFamily),
        titleMedium = titleMedium.copy(fontFamily = PixelHeadingFontFamily),
        titleSmall = titleSmall.copy(fontFamily = PixelHeadingFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = PixelHeadingFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = PixelHeadingFontFamily),
        bodySmall = bodySmall.copy(fontFamily = PixelHeadingFontFamily),
        labelLarge = labelLarge.copy(fontFamily = PixelHeadingFontFamily),
        labelMedium = labelMedium.copy(fontFamily = PixelHeadingFontFamily),
        labelSmall = labelSmall.copy(fontFamily = PixelHeadingFontFamily)
    )
}

/**
 * Applies the colors, shapes and typography of [style] to [content].
 * [LauncherStyle.ZALITH_DEFAULT] leaves the existing theme untouched.
 */
@Composable
fun LauncherStyleTheme(style: LauncherStyle, content: @Composable () -> Unit) {
    when (style) {
        LauncherStyle.ZALITH_DEFAULT -> CompositionLocalProvider(
            LocalLauncherStyle provides style,
            content = content
        )
        LauncherStyle.PC_LAUNCHER -> CompositionLocalProvider(
            LocalLauncherStyle provides style,
            LocalForcedDarkTheme provides true
        ) {
            MaterialTheme(
                colorScheme = pcLauncherColors,
                shapes = pcLauncherShapes,
                typography = pcLauncherTypography,
                content = content
            )
        }
        LauncherStyle.MODRINTH_STYLE -> {
            val dark = isLauncherInDarkTheme()
            CompositionLocalProvider(LocalLauncherStyle provides style) {
                MaterialTheme(
                    colorScheme = if (dark) modrinthDarkColors else modrinthLightColors,
                    shapes = modrinthShapes,
                    typography = modrinthTypography,
                    content = content
                )
            }
        }
        LauncherStyle.CONSOLE -> CompositionLocalProvider(
            LocalLauncherStyle provides style,
            LocalForcedDarkTheme provides true
        ) {
            MaterialTheme(
                colorScheme = consoleColors,
                shapes = consoleShapes,
                typography = consoleTypography,
                content = content
            )
        }
        LauncherStyle.MATERIAL_YOU -> {
            val dark = isLauncherInDarkTheme()
            val context = LocalContext.current
            //Android 12 以下没有壁纸取色，沿用启动器当前配色
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else MaterialTheme.colorScheme
            CompositionLocalProvider(LocalLauncherStyle provides style) {
                MaterialTheme(colorScheme = scheme, content = content)
            }
        }
        LauncherStyle.GLASS -> {
            val dark = isLauncherInDarkTheme()
            CompositionLocalProvider(LocalLauncherStyle provides style) {
                MaterialTheme(
                    colorScheme = glassColors(dark),
                    shapes = glassShapes,
                    typography = MaterialTheme.typography,
                    content = content
                )
            }
        }
        LauncherStyle.CUSTOM -> {
            val dark = isLauncherInDarkTheme()
            val primary = Color(AllSettings.launcherCustomColor.state)
            val secondaryInt = AllSettings.customThemeSecondary.state
            val paletteStyle = AllSettings.launcherCustomPaletteStyle.state
            val radius = AllSettings.customThemeCornerRadius.state
            val font = AllSettings.customThemeFont.state
            val scheme = remember(dark, primary, secondaryInt, paletteStyle) {
                val secondary = secondaryInt.takeIf { it != 0 }?.let { Color(it) }
                if (dark) customDark(primary, paletteStyle, secondary) else customLight(primary, paletteStyle, secondary)
            }
            val typography = MaterialTheme.typography
            val customTypography = remember(font, typography) {
                if (font == ThemeFont.DEFAULT) typography else typography.withFontFamily(font.toFontFamily())
            }
            //布局相关的判断（如 Modrinth 网格）以自定义主题选用的布局为准
            CompositionLocalProvider(LocalLauncherStyle provides effectiveLauncherLayout(style)) {
                MaterialTheme(
                    colorScheme = scheme,
                    shapes = remember(radius) { shapesFromRadius(radius) },
                    typography = customTypography,
                    content = content
                )
            }
        }
        LauncherStyle.RETRO_PIXEL -> CompositionLocalProvider(
            LocalLauncherStyle provides style,
            LocalForcedDarkTheme provides true
        ) {
            MaterialTheme(
                colorScheme = retroColors,
                shapes = retroShapes,
                typography = retroTypography,
                content = content
            )
        }
    }
}
