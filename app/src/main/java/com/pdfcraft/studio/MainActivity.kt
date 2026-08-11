package com.pdfcraft.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pdfcraft.studio.navigation.PdfCraftNavGraph
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme

/**
 * Single-activity host. All screens (Home, Editor, and future screens such
 * as Settings) live behind Compose destinations in [PdfCraftNavGraph], so
 * this class should stay a thin shell.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFCraftStudioTheme {
                Surface(modifier = Modifier) {
                    PdfCraftNavGraph()
                }
            }
        }
    }
}
