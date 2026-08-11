package com.pdfcraft.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PdfCraftDarkColors = darkColorScheme(
    primary = BrandRed,
    onPrimary = BrandOnRed,
    secondary = BrandGrey,
    background = BrandBlack,
    onBackground = BrandWhite,
    surface = BrandSurface,
    onSurface = BrandWhite,
    surfaceVariant = BrandSurfaceElevated,
    error = BrandRedDark
)

private val PdfCraftLightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = BrandOnRed,
    secondary = BrandGrey,
    background = BrandWhite,
    onBackground = BrandBlack,
    surface = BrandWhite,
    onSurface = BrandBlack,
    error = BrandRedDark
)

/**
 * App-wide theme. The brand is dark-first (matches the launcher icon), but a
 * light scheme is provided too since the spec calls for a clean, adaptable
 * UI. Defaults to following the system setting.
 */
@Composable
fun PDFCraftStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PdfCraftDarkColors else PdfCraftLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfCraftTypography,
        content = content
    )
}
