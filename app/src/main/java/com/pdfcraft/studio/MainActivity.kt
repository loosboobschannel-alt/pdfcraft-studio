package com.pdfcraft.studio

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pdfcraft.studio.navigation.PdfCraftNavGraph
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme

val LocalIsDarkTheme = compositionLocalOf { false }
val LocalToggleTheme = compositionLocalOf { {} }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("pdfcraft_prefs", Context.MODE_PRIVATE)

        setContent {
            var isDark by remember {
                mutableStateOf(prefs.getBoolean("dark_theme", false))
            }

            val toggleTheme = {
                isDark = !isDark
                prefs.edit().putBoolean("dark_theme", isDark).apply()
            }

            CompositionLocalProvider(
                LocalIsDarkTheme provides isDark,
                LocalToggleTheme provides toggleTheme
            ) {
                PDFCraftStudioTheme(darkTheme = isDark) {
                    Surface(modifier = Modifier) {
                        PdfCraftNavGraph()
                    }
                }
            }
        }
    }
}
