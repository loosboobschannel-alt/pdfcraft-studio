package com.pdfcraft.studio.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfcraft.studio.R
import com.pdfcraft.studio.ui.editor.canvas.PdfPagesPreview
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditorScreen(onBackClick: () -> Unit) {
    val viewModel: EditorViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showImportSettings by remember { mutableStateOf(false) }
    var showFontTools by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val noImagesSelectedMessage =
        stringResource(R.string.no_images_selected)

    val imageSavedMessage =
        stringResource(R.string.image_saved_message)

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia()
        ) { uris ->
            if (uris.isEmpty()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        noImagesSelectedMessage
                    )
                }
            } else {
                viewModel.importImages(uris)
            }
        }

    val fontPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                viewModel.importFontFromUri(uri)
            }
        }

    LaunchedEffect(viewModel.lastFontImportMessage) {
        val msg = viewModel.lastFontImportMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeFontImportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.editor_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.content_desc_back
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->

        if (viewModel.isImporting) {
            val progressMessage = viewModel.selectedImageSizeOption.targetBytes?.let {
                stringResource(
                    R.string.import_progress_message_compressing,
                    viewModel.selectedImageSizeOption.label
                )
            } ?: stringResource(R.string.import_progress_message_default)

            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(progressMessage)
                    }
                }
            )
        }

        if (showImportSettings) {
            ImportImagesDialog(
                selectedOption = viewModel.selectedImageSizeOption,
                onOptionSelected = viewModel::selectImageSizeOption,
                onImportClick = {
                    showImportSettings = false
                    imagePickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onDismiss = { showImportSettings = false }
            )
        }

        if (showFontTools) {
            val hasSelection =
                viewModel.focusedTextId != null && !viewModel.currentSelection.collapsed
            FontToolsDialog(
                fonts = viewModel.availableFonts.toList(),
                selectedFontId = viewModel.currentTextFontId(),
                isBoldActive = viewModel.isSelectionBold(),
                isItalicActive = viewModel.isSelectionItalic(),
                hasTextSelection = hasSelection,
                onFontSelected = { font ->
                    viewModel.applyFontToSelectedText(font)
                },
                onBoldClick = {
                    viewModel.toggleBoldForSelection()
                },
                onItalicClick = {
                    viewModel.toggleItalicForSelection()
                },
                onImportFontClick = {
                    fontPickerLauncher.launch(
                        arrayOf(
                            "font/ttf",
                            "font/otf",
                            "application/x-font-ttf",
                            "application/x-font-otf",
                            "application/font-sfnt",
                            "*/*"
                        )
                    )
                },
                onDismiss = { showFontTools = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorToolbar(
                onImportImagesClick = {
                    showImportSettings = true
                },
                imagesPerRow =
                    viewModel.imagesPerRow,
                onImagesPerRowSelected =
                    viewModel::updateImagesPerRow,
                imageSpacingDp =
                    viewModel.imageSpacingDp,
                onImageSpacingSelected =
                    viewModel::updateImageSpacing,
                imageCellAspectRatio =
                    viewModel.imageCellAspectRatio,
                onImageCellAspectRatioSelected =
                    viewModel::updateImageCellAspectRatio,
                imageCornerRadiusPercent =
                    viewModel.imageCornerRadiusPercent,
                onImageCornerRadiusSelected =
                    viewModel::updateImageCornerRadiusPercent,
                onAddTextClick =
                    viewModel::enterAddTextMode,
                onFontClick = {
                    showFontTools = true
                },
                onDeleteTextClick =
                    viewModel::deleteSelectedText,
                hasSelectedText =
                    viewModel.focusedTextId != null || viewModel.selectedTextId != null
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
                    imageCornerRadiusPercent = viewModel.imageCornerRadiusPercent,

                    textElements =
                        viewModel.textElements,

                    addTextMode =
                        viewModel.addTextMode,

                    selectedTextId =
                        viewModel.selectedTextId,

                    pendingFocusTextId =
                        viewModel.pendingFocusTextId,

                    onAddTextAt = { pageIndex, xFraction, yFraction ->
                        viewModel.addTextAt(pageIndex, xFraction, yFraction)
                    },

                    onSelectText = { id ->
                        viewModel.selectText(id)
                    },

                    onMoveText = { id, xFraction, yFraction ->
                        viewModel.moveText(id, xFraction, yFraction)
                    },

                    onTextValueChange = { id, newText, newSelection ->
                        viewModel.updateTextValue(id, newText, newSelection)
                    },

                    onTextFocused = { id ->
                        viewModel.onTextFocused(id)
                    },

                    onTextUnfocused = { id ->
                        viewModel.onTextUnfocused(id)
                    },

                    onConsumePendingFocus = {
                        viewModel.consumePendingFocus()
                    },

                    onDeselectText = {
                        viewModel.deselectText()
                    },

                    selectedImageIds =
                        viewModel.selectedImageIds,

                    selectionMode =
                        viewModel.selectionMode,

                    singleMenuImageId =
                        viewModel.singleMenuImageId,

                    multipleActionsVisible =
                        viewModel.multipleActionsVisible,

                    reorderMode =
                        viewModel.reorderMode,

                    hasClipboardImages =
                        viewModel.hasClipboardImages,

                    onImageClick = { id ->
                        viewModel.openImageMenu(id)
                    },

                    onImageLongPress = { id ->
                        viewModel.longPressImage(id)
                    },

                    onChangePosition = { id ->
                        viewModel.enterSingleReorder(id)
                    },

                    onCut = { id ->
                        viewModel.cutSingle(id)
                    },

                    onCopy = { id ->
                        viewModel.copySingle(id)
                    },

                    onPaste = {
                        viewModel.pasteImages()
                    },

                    onSaveSingle = { id ->
                        val image = viewModel.getImage(id)

                        if (image?.bitmap != null) {
                            saveBitmapToGallery(
                                context = context,
                                bitmap = image.bitmap
                            )

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    imageSavedMessage
                                )
                            }
                        }
                    },

                    onShareSingle = { id ->
                        viewModel.getImage(id)?.let { image ->
                            shareImages(
                                context = context,
                                images = listOf(image)
                            )
                        }
                    },

                    onDeleteSingle = { id ->
                        viewModel.deleteSingle(id)
                    },

                    onFinishMultipleSelection = {
                        viewModel.finishMultipleSelection()
                    },

                    onMultipleChangePosition = {
                        viewModel.closeMultipleActions()
                        viewModel.reorderMode = true
                    },

                    onMultipleCut = {
                        viewModel.cutSelected()
                    },

                    onMultipleCopy = {
                        viewModel.copySelected()
                    },

                    onMultipleSave = {
                        viewModel.getSelectedImages()
                            .forEach { image ->
                                image.bitmap?.let { bitmap ->
                                    saveBitmapToGallery(
                                        context = context,
                                        bitmap = bitmap
                                    )
                                }
                            }

                        viewModel.cancelSelection()
                    },

                    onMultipleShare = {
                        shareImages(
                            context = context,
                            images = viewModel.getSelectedImages()
                        )
                        viewModel.cancelSelection()
                    },

                    onMultipleDelete = {
                        viewModel.deleteSelected()
                    },

                    onCloseMultipleActions = {
                        viewModel.closeMultipleActions()
                    },

                    onMoveSingle = { sourceId, targetId ->
                        viewModel.moveSingleImageTo(
                            sourceId,
                            targetId
                        )
                    },

                    onMoveMultiple = { targetId ->
                        viewModel.moveSelectedImagesTo(
                            targetId
                        )
                    },

                    onFinishReorder = {
                        viewModel.finishReorder()
                    },

                    customFonts = viewModel.availableFonts.filter { it.isCustom },

                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap
) {
    val resolver = context.contentResolver
    val filename =
        "PDFCraft_${System.currentTimeMillis()}.jpg"

    val values = ContentValues().apply {
        put(
            MediaStore.Images.Media.DISPLAY_NAME,
            filename
        )
        put(
            MediaStore.Images.Media.MIME_TYPE,
            "image/jpeg"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/PDFCraft Studio"
            )
            put(
                MediaStore.Images.Media.IS_PENDING,
                1
            )
        }
    }

    val uri = resolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ) ?: return

    try {
        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                95,
                output
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val completed = ContentValues().apply {
                put(
                    MediaStore.Images.Media.IS_PENDING,
                    0
                )
            }

            resolver.update(
                uri,
                completed,
                null,
                null
            )
        }
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
    }
}

private fun shareImages(
    context: Context,
    images: List<ImportedImage>
) {
    val uris = images.mapNotNull { it.imageUri }

    if (uris.isEmpty()) {
        return
    }

    if (uris.size == 1) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.share_image)
            )
        )
    } else {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(uris)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.share_image)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScreenPreview() {
    PDFCraftStudioTheme {
        EditorScreen(onBackClick = {})
    }
}
