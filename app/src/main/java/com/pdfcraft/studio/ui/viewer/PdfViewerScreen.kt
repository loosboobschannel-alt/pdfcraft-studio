package com.pdfcraft.studio.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.pdf.PdfLinkExtractor
import com.pdfcraft.studio.core.pdf.PdfLinkRegion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class PdfSession(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
    val mutex: Mutex = Mutex()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdfUriString: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val uri = remember(pdfUriString) { Uri.parse(pdfUriString) }
    val fileName = remember(pdfUriString) { queryDisplayName(context, uri) }

    var session by remember { mutableStateOf<PdfSession?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var pageHeights by remember { mutableStateOf<List<Float>>(emptyList()) }
    var linkRegions by remember { mutableStateOf<List<PdfLinkRegion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfUriString) {
        isLoading = true
        errorMessage = null
        pageCount = 0
        linkRegions = emptyList()
        session = null
        val failed = context.getString(R.string.open_pdf_failed)
        val opened = withContext(Dispatchers.IO) {
            try {
                val pfd = openPdfDescriptor(context, uri)
                    ?: return@withContext Result.failure<PdfSession>(IllegalStateException("pfd"))
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    try { pfd.close() } catch (_: Exception) {}
                    return@withContext Result.failure(e)
                }
                if (renderer.pageCount <= 0) {
                    try { renderer.close() } catch (_: Exception) {}
                    try { pfd.close() } catch (_: Exception) {}
                    return@withContext Result.failure(IllegalStateException("empty"))
                }
                val heights = mutableListOf<Float>()
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { heights.add(it.height.toFloat()) }
                }
                Result.success(PdfSession(pfd, renderer) to heights.toList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        opened.fold(
            onSuccess = { pair ->
                session = pair.first
                pageHeights = pair.second
                pageCount = pair.first.renderer.pageCount
                isLoading = false
                linkRegions = try {
                    withContext(Dispatchers.IO) {
                        PdfLinkExtractor.extract(context, uri, pair.second)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PdfViewerScreen", "Link extract failed", e)
                    emptyList()
                }
            },
            onFailure = { e ->
                android.util.Log.e("PdfViewerScreen", "Failed to open PDF", e)
                errorMessage = failed
                isLoading = false
            }
        )
    }

    DisposableEffect(pdfUriString) {
        onDispose {
            val s = session
            session = null
            try { s?.renderer?.close() } catch (_: Exception) {}
            try { s?.pfd?.close() } catch (_: Exception) {}
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onBackClick,
            title = { Text(stringResource(R.string.open_pdf)) },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = onBackClick) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    val targetWidthPx = with(density) { 720.dp.roundToPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> { }
                pageCount <= 0 -> {
                    Text(
                        text = stringResource(R.string.open_pdf_empty),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val current = session
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageLazy(
                                session = current,
                                pageIndex = pageIndex,
                                targetWidthPx = targetWidthPx,
                                regions = linkRegions.filter { it.pageIndex == pageIndex },
                                onLinkClick = { url ->
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (e: Exception) {
                                        android.util.Log.e("PdfViewerScreen", "Open link failed", e)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageLazy(
    session: PdfSession?,
    pageIndex: Int,
    targetWidthPx: Int,
    regions: List<PdfLinkRegion>,
    onLinkClick: (String) -> Unit
) {
    var bitmap by remember(session, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        val s = session ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            s.mutex.withLock {
                s.renderer.openPage(pageIndex).use { page ->
                    val scale = targetWidthPx.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val b = Bitmap.createBitmap(targetWidthPx, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                }
            }
        }
        bitmap = bmp
    }

    DisposableEffect(session, pageIndex) {
        onDispose {
            val b = bitmap
            bitmap = null
            if (b != null && !b.isRecycled) b.recycle()
        }
    }

    val bmp = bitmap
    if (bmp == null || bmp.isRecycled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
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
                        if (hit != null) onLinkClick(hit.url)
                    }
                }
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = c.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    val last = uri.lastPathSegment
    if (!last.isNullOrBlank()) return Uri.decode(last).substringAfterLast('/')
    return "PDF"
}

private fun openPdfDescriptor(
    context: android.content.Context,
    uri: Uri
): ParcelFileDescriptor? {
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.let { return it }
    } catch (e: Exception) {
        android.util.Log.w("PdfViewerScreen", "openFileDescriptor failed for $uri", e)
    }
    if (uri.scheme == "file") {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
    }
    return null
}
