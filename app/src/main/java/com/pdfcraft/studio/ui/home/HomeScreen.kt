package com.pdfcraft.studio.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pdfcraft.studio.core.settings.StorageAccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.project.ProjectStore
import com.pdfcraft.studio.ui.common.AppIcons
import com.pdfcraft.studio.ui.components.AppLogo
import com.pdfcraft.studio.ui.components.PrimaryActionButton
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onCreatePdfClick: () -> Unit,
    onMyProjectsClick: () -> Unit = {},
    onRecoverDraft: (String) -> Unit = {},
    onViewPdf: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRecoverDialog by remember { mutableStateOf(false) }
    var latestDraftPath by remember { mutableStateOf<String?>(null) }
    var storageGranted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    var askedStorage by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storageGranted = granted || StorageAccess.isGranted(context)
    }
    fun requestStorage() {
        if (StorageAccess.needsRuntimeReadPermission()) {
            readLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            StorageAccess.openAllFilesSettings(context)
        }
    }
    LaunchedEffect(Unit) {
        storageGranted = StorageAccess.isGranted(context)
        if (!storageGranted && !askedStorage) {
            askedStorage = true
            delay(3000)
            if (!StorageAccess.isGranted(context)) requestStorage()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageGranted = StorageAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(storageGranted) {
        if (!storageGranted) return@LaunchedEffect
        val latest = withContext(Dispatchers.IO) { ProjectStore.latestDraft(context) }
        if (latest != null && latest.readable) {
            latestDraftPath = latest.file.absolutePath
            showRecoverDialog = true
        }
    }

    if (showRecoverDialog && latestDraftPath != null) {
        AlertDialog(
            onDismissRequest = { showRecoverDialog = false },
            title = { Text(stringResource(R.string.draft_recover_title)) },
            text = { Text(stringResource(R.string.draft_recover_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val path = latestDraftPath
                    showRecoverDialog = false
                    if (!path.isNullOrBlank()) onRecoverDraft(path)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppLogo(size = 108.dp)

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF8A8A8A),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.widthIn(max = 280.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                PrimaryActionButton(
                    text = stringResource(R.string.create_pdf),
                    icon = Icons.Filled.Add,
                    onClick = { if (storageGranted) onCreatePdfClick() else requestStorage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                PrimaryActionButton(
                    text = stringResource(R.string.view_pdf),
                    icon = AppIcons.Visibility,
                    onClick = { if (storageGranted) onViewPdf() else requestStorage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                PrimaryActionButton(
                    text = stringResource(R.string.my_projects),
                    icon = AppIcons.FileDocument,
                    onClick = { if (storageGranted) onMyProjectsClick() else requestStorage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                )
            }

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PDFCraftStudioTheme {
        Surface {
            HomeScreen(onCreatePdfClick = {})
        }
    }
}
