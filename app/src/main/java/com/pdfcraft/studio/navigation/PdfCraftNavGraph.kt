package com.pdfcraft.studio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pdfcraft.studio.ui.editor.EditorScreen
import com.pdfcraft.studio.ui.home.HomeScreen
import com.pdfcraft.studio.ui.home.MyProjectsScreen
import com.pdfcraft.studio.ui.viewer.PdfViewerScreen

@Composable
fun PdfCraftNavGraph(
    openPdfUri: String? = null,
    navController: NavHostController = rememberNavController()
) {
    LaunchedEffect(openPdfUri) {
        val uri = openPdfUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        navController.navigate(Screen.PdfViewer.routeWithUri(uri)) {
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCreatePdfClick = {
                    navController.navigate(Screen.Editor.routeNew())
                },
                onMyProjectsClick = {
                    navController.navigate(Screen.MyProjects.route)
                },
                onRecoverDraft = { path ->
                    navController.navigate(Screen.Editor.routeOpen(path))
                },
                onOpenPdf = { uri ->
                    navController.navigate(Screen.PdfViewer.routeWithUri(uri))
                }
            )
        }
        composable(Screen.MyProjects.route) {
            MyProjectsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenProject = { path ->
                    navController.navigate(Screen.Editor.routeOpen(path))
                }
            )
        }
        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("project") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("project").orEmpty()
            val projectPath = Screen.Editor.decodeProject(encoded)
            EditorScreen(
                projectPath = projectPath,
                onBackClick = { navController.popBackStack() },
                onViewPdfClick = { uriString ->
                    navController.navigate(Screen.PdfViewer.routeWithUri(uriString))
                }
            )
        }
        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("uri").orEmpty()
            val uriString = Screen.PdfViewer.decodeUriArg(encoded)
            PdfViewerScreen(
                pdfUriString = uriString,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
