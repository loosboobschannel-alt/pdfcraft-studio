package com.pdfcraft.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = BrandOnRed,
    secondary = BrandGrey,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = BrandPageSurface,
    error = BrandRedDark
)

private val DarkColors = darkColorScheme(
    primary = BrandRed,
    onPrimary = BrandOnRed,
    secondary = BrandGrey,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    error = BrandRedDark
)

@Composable
fun PDFCraftStudioTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PdfCraftTypography,
        content = content
    )
}
