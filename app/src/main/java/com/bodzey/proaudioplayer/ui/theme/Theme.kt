package com.bodzey.proaudioplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ProAudioColors(
    val canvas: Color,
    val canvasDeep: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceInset: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSoft: Color,
    val textMuted: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val blueAccent: Color,
)

private val DarkProAudioColors = ProAudioColors(
    canvas = Color(0xFF080A0E),
    canvasDeep = Color(0xFF071013),
    surface = Color(0xFF0B1215),
    surfaceRaised = Color(0xFF10181B),
    surfaceInset = Color(0xFF080E11),
    border = Color(0x1FFFFFFF),
    borderStrong = Color(0x2BFFFFFF),
    text = Color(0xFFF1F5F6),
    textSoft = Color(0xFFABB7B9),
    textMuted = Color(0xFF738184),
    accent = Color(0xFFFF642A),
    accentStrong = Color(0xFFFF5725),
    accentSoft = Color(0x22FF642A),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    blueAccent = Color(0xFF38BDF8),
)

private val LightProAudioColors = ProAudioColors(
    canvas = Color(0xFFF3F5F7),
    canvasDeep = Color(0xFFEDF1F3),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF8FAFB),
    surfaceInset = Color(0xFFF1F4F6),
    border = Color(0xFFDCE4E8),
    borderStrong = Color(0xFFCBD6DB),
    text = Color(0xFF152127),
    textSoft = Color(0xFF4F5F67),
    textMuted = Color(0xFF7B898F),
    accent = Color(0xFFFF641F),
    accentStrong = Color(0xFFED5517),
    accentSoft = Color(0xFFFFF4ED),
    success = Color(0xFF12966B),
    warning = Color(0xFFB76B13),
    danger = Color(0xFFCF5151),
    blueAccent = Color(0xFF0284C7),
)

val LocalProAudioColors = staticCompositionLocalOf { DarkProAudioColors }

private val DarkMaterialColors = darkColorScheme(
    primary = DarkProAudioColors.accent,
    onPrimary = Color.White,
    background = DarkProAudioColors.canvas,
    onBackground = DarkProAudioColors.text,
    surface = DarkProAudioColors.surface,
    onSurface = DarkProAudioColors.text,
    surfaceVariant = DarkProAudioColors.surfaceRaised,
    onSurfaceVariant = DarkProAudioColors.textSoft,
    outline = DarkProAudioColors.borderStrong,
    error = DarkProAudioColors.danger,
)

private val LightMaterialColors = lightColorScheme(
    primary = LightProAudioColors.accent,
    onPrimary = Color.White,
    background = LightProAudioColors.canvas,
    onBackground = LightProAudioColors.text,
    surface = LightProAudioColors.surface,
    onSurface = LightProAudioColors.text,
    surfaceVariant = LightProAudioColors.surfaceRaised,
    onSurfaceVariant = LightProAudioColors.textSoft,
    outline = LightProAudioColors.borderStrong,
    error = LightProAudioColors.danger,
)

private val ProAudioTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun ProAudioPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val proAudioColors = if (darkTheme) DarkProAudioColors else LightProAudioColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalProAudioColors provides proAudioColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkMaterialColors else LightMaterialColors,
            typography = ProAudioTypography,
            content = content,
        )
    }
}
