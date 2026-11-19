// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7882), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0F2), onPrimaryContainer = Color(0xFF0A4B51),
    secondary = Color(0xFF586361), onSecondary = Color.White,
    background = Color(0xFFF7F8F6), onBackground = Color(0xFF1D2020),
    surface = Color.White, onSurface = Color(0xFF1D2020),
    surfaceVariant = Color(0xFFEFF4F3), onSurfaceVariant = Color(0xFF586361),
    outline = Color(0xFF778582), outlineVariant = Color(0xFFDDE5E3),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18),
    surfaceTint = Color(0xFF0F7882),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBF0ED), surfaceContainerHigh = Color(0xFFE5ECE8),
    surfaceContainerHighest = Color(0xFFDEE7E2),
    inverseSurface = Color(0xFF27332F), inverseOnSurface = Color(0xFFE0E9E6),
    inversePrimary = Color(0xFF83D3DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D3DC), onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF155059), onPrimaryContainer = Color(0xFFBCEBF0),
    secondary = Color(0xFFB7C8C4), onSecondary = Color(0xFF223330),
    background = Color(0xFF101817), onBackground = Color(0xFFE0E9E6),
    surface = Color(0xFF192321), onSurface = Color(0xFFE0E9E6),
    surfaceVariant = Color(0xFF25332F), onSurfaceVariant = Color(0xFFB7C8C4),
    outline = Color(0xFF81948E), outlineVariant = Color(0xFF384B44),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF64251F),
    onErrorContainer = Color(0xFFFFDAD5),
    surfaceTint = Color(0xFF83D3DC),
    surfaceContainerLowest = Color(0xFF0C1210), surfaceContainerLow = Color(0xFF151E1B),
    surfaceContainer = Color(0xFF192321), surfaceContainerHigh = Color(0xFF222E29),
    surfaceContainerHighest = Color(0xFF2D3A34),
    inverseSurface = Color(0xFFE0E9E6), inverseOnSurface = Color(0xFF27332F),
    inversePrimary = Color(0xFF0F7882),
)

// Semantic accessors keep shared components aligned with the active theme.
val MementoText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val MementoMutedText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val MementoSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val MementoSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val MementoOutline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val MementoPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val MementoOnPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
val MementoDanger: Color @Composable get() = MaterialTheme.colorScheme.error
val MementoDangerContainer: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
private val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }
val MementoSuccess: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF9DD9B4) else Color(0xFF256640)
val MementoSuccessContainer: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF193D2A) else Color(0xFFE5F3EA)
val MementoWarning: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFF1CD84) else Color(0xFF79520F)
val MementoWarningContainer: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF453519) else Color(0xFFFFF1D2)

@Composable
fun MementoCarTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val baseTypography = Typography()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        typography = Typography(
            headlineSmall = baseTypography.headlineSmall.copy(
                fontSize = 28.sp, lineHeight = 34.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
            ),
            headlineMedium = baseTypography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 40.sp,
                letterSpacing = (-0.6).sp,
            ),
            titleLarge = baseTypography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            titleMedium = baseTypography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            bodyLarge = baseTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
            bodyMedium = baseTypography.bodyMedium.copy(letterSpacing = 0.sp),
            bodySmall = baseTypography.bodySmall.copy(letterSpacing = 0.sp),
            labelLarge = baseTypography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        ),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides darkTheme, content = content)
        },
    )
}
