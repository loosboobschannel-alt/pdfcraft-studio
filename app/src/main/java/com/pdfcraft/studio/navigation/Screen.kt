package com.pdfcraft.studio.navigation

/**
 * Central registry of navigation destinations. Add new screens here (e.g.
 * Settings, PageSort, ExportPreview) instead of hard-coding route strings
 * elsewhere, so the whole nav graph stays discoverable in one place.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Editor : Screen("editor")
}
