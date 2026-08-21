package com.pdfcraft.studio.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.core.pdf.PdfLinkExtractor
import com.pdfcraft.studio.core.pdf.PdfLinkRegion

private data class RenderedPage(val bitmap: Bitmap, val heightPoints: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdfUriString: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uri = remember(pdfUriString) { Uri.parse(pdfUriString) }

    var pages by remember { mutableStateOf<List<RenderedPage>>(emptyList()) }
    var linkRegions by remember { mutableStateOf<List<PdfLinkRegion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        isLoading = true
        val rendered = mutableListOf<RenderedPage>()
        val targetWidthPx = with(density) { 720.dp.roundToPx() }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = targetWidthPx.toFloat() / page.width.toFloat()
                            val bmpWidth = targetWidthPx
                            val bmpHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(
                                bmpWidth,
                                bmpHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            rendered.add(RenderedPage(bitmap, page.height.toFloat()))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfViewerScreen", "Failed to render PDF", e)
        }

        pages = rendered
        linkRegions = PdfLinkExtractor.extract(
            context = context,
            uri = uri,
            pageHeightsPoints = rendered.map { it.heightPoints }
        )
        isLoading = false
    }

    DisposableEffect(uri) {
        onDispose {
            pages.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View PDF") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(pages.size) { pageIndex ->
                        PdfPageView(
                            page = pages[pageIndex],
                            regions = linkRegions.filter { it.pageIndex == pageIndex },
                            onLinkClick = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: Exception) {
                                    android.util.Log.e("PdfViewerScreen", "Failed to open link: $url", e)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(
    page: RenderedPage,
    regions: List<PdfLinkRegion>,
    onLinkClick: (String) -> Unit
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Column {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { sizePx = it }
                .pointerInput(regions) {
                    detectTapGestures { offset ->
                        if (sizePx.width <= 0 || sizePx.height <= 0) return@detectTapGestures
                        val xFraction = offset.x / sizePx.width
                        val yFraction = offset.y / sizePx.height
                        val hit = regions.firstOrNull { region ->
                            xFraction in region.leftFraction..region.rightFraction &&
                                yFraction in region.topFraction..region.bottomFraction
                        }
                        if (hit != null) {
                            onLinkClick(hit.url)
                        }
                    }
                }
        )
    }
}
