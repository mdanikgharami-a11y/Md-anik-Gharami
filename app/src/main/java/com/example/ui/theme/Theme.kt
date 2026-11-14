package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val failed: Color,
    val onFailed: Color,
    val failedContainer: Color,
    val star: Color,
    val starContainer: Color
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = SuccessGreenLight,
        onSuccess = Color.White,
        successContainer = SuccessSurfaceLight,
        failed = FailedRedLight,
        onFailed = Color.White,
        failedContainer = FailedSurfaceLight,
        star = StarGold,
        starContainer = StarGoldContainerLight
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = CyanTertiaryDark,
    tertiaryContainer = CyanContainerDark,
    onTertiaryContainer = CyanOnContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = FailedRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = FailedSurfaceDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateOnSecondaryContainerLight,
    tertiary = CyanTertiaryLight,
    tertiaryContainer = CyanContainerLight,
    onTertiaryContainer = CyanOnContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = FailedRedLight,
    onError = Color.White,
    errorContainer = FailedSurfaceLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun FaceAttendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) {
        SemanticColors(
            success = SuccessGreenDark,
            onSuccess = Color(0xFF022C22),
            successContainer = SuccessSurfaceDark,
            failed = FailedRedDark,
            onFailed = Color(0xFF450A0A),
            failedContainer = FailedSurfaceDark,
            star = StarGold,
            starContainer = StarGoldContainerDark
        )
    } else {
        SemanticColors(
            success = SuccessGreenLight,
            onSuccess = Color.White,
            successContainer = SuccessSurfaceLight,
            failed = FailedRedLight,
            onFailed = Color.White,
            failedContainer = FailedSurfaceLight,
            star = StarGold,
            starContainer = StarGoldContainerLight
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
