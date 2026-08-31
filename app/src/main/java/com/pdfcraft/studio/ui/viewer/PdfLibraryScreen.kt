package com.pdfcraft.studio.ui.viewer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.pdf.PdfLibraryStore
import com.pdfcraft.studio.ui.common.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibraryTab { All, Folders, Recent }
private val Accent = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfLibraryScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var tab by remember { mutableStateOf(LibraryTab.All) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var allPdfs by remember { mutableStateOf(emptyList<PdfLibraryStore.PdfItem>()) }
    var recent by remember { mutableStateOf(emptyList<PdfLibraryStore.PdfItem>()) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var askedPermission by remember { mutableStateOf(false) }

    fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            permissionDenied = false
            reload()
        } else {
            loading = false
        }
    }

    fun openSystemAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
            loading = false
            permissionDenied = true
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) { PdfLibraryStore.scanDevicePdfs(context) }
            val rec = withContext(Dispatchers.IO) { PdfLibraryStore.listRecent(context) }
            allPdfs = list
            recent = rec
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (hasStorageAccess()) {
            reload()
        } else if (!askedPermission) {
            askedPermission = true
            openSystemAccess()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasStorageAccess()) {
                    permissionDenied = false
                    reload()
                } else if (askedPermission) {
                    permissionDenied = true
                    loading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val q = query.trim()
    fun matches(item: PdfLibraryStore.PdfItem): Boolean {
        return q.isEmpty() || item.name.contains(q, ignoreCase = true)
    }
    val visiblePdfs = when (tab) {
        LibraryTab.All -> allPdfs.filter(::matches)
        LibraryTab.Recent -> recent.filter(::matches)
        LibraryTab.Folders -> {
            val folder = selectedFolder
            if (folder == null) emptyList()
            else allPdfs.filter { it.folder == folder }.filter(::matches)
        }
    }
    val folders = allPdfs.groupBy { it.folder }.toList()
        .filter { pair ->
            q.isEmpty() ||
                pair.first.contains(q, ignoreCase = true) ||
                pair.second.any { it.name.contains(q, ignoreCase = true) }
        }
        .sortedBy { it.first.lowercase() }
    val emptyMsg = if (q.isNotEmpty()) {
        stringResource(R.string.pdf_library_no_match)
    } else {
        stringResource(R.string.pdf_library_empty)
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == LibraryTab.Folders && selectedFolder != null) selectedFolder!!
                        else stringResource(R.string.view_pdf),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (tab == LibraryTab.Folders && selectedFolder != null) {
                            selectedFolder = null
                        } else onBackClick()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pdf_library_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabChip(stringResource(R.string.pdf_library_all), tab == LibraryTab.All) {
                    tab = LibraryTab.All
                    selectedFolder = null
                }
                TabChip(stringResource(R.string.pdf_library_folders), tab == LibraryTab.Folders) {
                    tab = LibraryTab.Folders
                }
                TabChip(stringResource(R.string.pdf_library_recent), tab == LibraryTab.Recent) {
                    tab = LibraryTab.Recent
                    selectedFolder = null
                }
            }
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.pdf_library_finding), color = Color(0xFF8A8A8A))
                }
                permissionDenied && allPdfs.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.pdf_library_access_needed),
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { openSystemAccess() }) {
                        Text(stringResource(R.string.pdf_library_try_again))
                    }
                }
                tab == LibraryTab.Folders && selectedFolder == null -> {
                    if (folders.isEmpty()) EmptyHint(emptyMsg)
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(folders, key = { it.first }) { pair ->
                            FolderRow(pair.first, pair.second.size) { selectedFolder = pair.first }
                        }
                    }
                }
                visiblePdfs.isEmpty() -> EmptyHint(emptyMsg)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visiblePdfs, key = { it.uri }) { item ->
                        PdfRow(item) {
                            PdfLibraryStore.addRecent(context, item)
                            onOpenPdf(item.uri)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) Accent else Color(0xFF666666),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.height(2.dp).width(if (selected) 36.dp else 0.dp).background(Accent, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun PdfRow(item: PdfLibraryStore.PdfItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.FileDocument, null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.Black, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(PdfLibraryStore.formatSize(item.sizeBytes), color = Color(0xFF8A8A8A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun FolderRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.Folder, null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.Black, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                count.toString() + if (count == 1) " PDF" else " PDFs",
                color = Color(0xFF8A8A8A),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF8A8A8A), modifier = Modifier.padding(32.dp))
    }
}
