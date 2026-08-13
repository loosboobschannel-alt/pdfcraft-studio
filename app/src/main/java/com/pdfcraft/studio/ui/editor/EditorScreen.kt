package com.pdfcraft.studio.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfcraft.studio.R
import com.pdfcraft.studio.ui.editor.canvas.PdfPagesPreview
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onBackClick: () -> Unit) {
    val viewModel: EditorViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val noImagesSelectedMessage = stringResource(R.string.no_images_selected)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(noImagesSelectedMessage)
            }
        } else {
            viewModel.importImages(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorToolbar(
                onImportImagesClick = {
                    imagePickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                selectedSizeOption = viewModel.selectedImageSizeOption,
                onSizeOptionSelected = viewModel::selectImageSizeOption,
                imagesPerRow = viewModel.imagesPerRow,
                onImagesPerRowSelected = viewModel::updateImagesPerRow,
                imageSpacingDp = viewModel.imageSpacingDp,
                onImageSpacingSelected = viewModel::updateImageSpacing,
                imageCellAspectRatio = viewModel.imageCellAspectRatio,
                onImageCellAspectRatioSelected = viewModel::updateImageCellAspectRatio
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp)
            ) {
                PdfPagesPreview(
                    images = viewModel.importedImages,
                    imagesPerRow = viewModel.imagesPerRow,
                    imageSpacingDp = viewModel.imageSpacingDp,
                    imageCellAspectRatio = viewModel.imageCellAspectRatio,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (viewModel.isImporting) {
            ImportProgressDialog(
                completed = viewModel.importCompletedCount,
                total = viewModel.importTotalCount
            )
        }
    }
}

@Composable
private fun ImportProgressDialog(completed: Int, total: Int) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.import_progress_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.Black,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.import_progress_count, completed, total),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScreenPreview() {
    PDFCraftStudioTheme {
        EditorScreen(onBackClick = {})
    }
}
