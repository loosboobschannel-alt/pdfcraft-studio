package com.pdfcraft.studio.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfcraft.studio.ui.editor.EditorScreen
import com.pdfcraft.studio.ui.home.HomeScreen

/**
 * App-wide navigation graph. Each screen is its own self-contained
 * composable module (see ui/home, ui/editor). Future stages should add new
 * `composable(Screen.X.route) { ... }` entries here rather than nesting
 * screens inside one another.
 */
@Composable
fun PdfCraftNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCreatePdfClick = {
                    navController.navigate(Screen.Editor.route)
                }
            )
        }
        composable(Screen.Editor.route) {
            EditorScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
