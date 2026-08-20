package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.foundation.border

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.Image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import com.pdfcraft.studio.core.pdf.PdfGenerator
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfcraft.studio.R
import com.pdfcraft.studio.ui.editor.canvas.PdfPagesPreview
import com.pdfcraft.studio.ui.theme.PDFCraftStudioTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onBackClick: () -> Unit) {
    val viewModel: EditorViewModel = viewModel()

    // Image edit dialogs / actions state (must be before use)
    var cropImageId by remember { mutableStateOf<String?>(null) }
    var rotateImageId by remember { mutableStateOf<String?>(null) }
    var rotateDegrees by remember { mutableStateOf(0f) }
    var linkImageId by remember { mutableStateOf<String?>(null) }
    var linkUrlText by remember { mutableStateOf("") }
    var showImagePositionDialog by remember { mutableStateOf(false) }
    var imagePositionTargetId by remember { mutableStateOf<String?>(null) }

    cropImageId?.let { cid ->
        val img = viewModel.getImage(cid)
        val bmp = img?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            CropImageScreen(
                bitmap = bmp,
                onCancel = { cropImageId = null },
                onApply = { l, t, r, b ->
                    viewModel.cropImageBitmap(cid, l, t, r, b)
                    cropImageId = null
                }
            )
        } else {
            cropImageId = null
        }
    }

    rotateImageId?.let { rid ->
        val img = viewModel.getImage(rid)
        val bmp = img?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            RotateImageDialog(
                bitmap = bmp,
                degrees = rotateDegrees,
                onDegreesChange = { rotateDegrees = it },
                onCancel = {
                    rotateImageId = null
                    rotateDegrees = 0f
                },
                onOk = {
                    if (rotateDegrees % 360f != 0f) {
                        viewModel.rotateImageBitmap(rid, rotateDegrees)
                    }
                    rotateImageId = null
                    rotateDegrees = 0f
                }
            )
        } else {
            rotateImageId = null
        }
    }

    linkImageId?.let { lid ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { linkImageId = null },
            title = { androidx.compose.material3.Text(stringResource(R.string.image_link_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(stringResource(R.string.image_link_instruction))
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = linkUrlText,
                        onValueChange = { linkUrlText = it },
                        placeholder = { androidx.compose.material3.Text(stringResource(R.string.image_link_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.setImageLinkUrl(lid, linkUrlText)
                    linkImageId = null
                }) { androidx.compose.material3.Text(stringResource(R.string.image_link_done)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { linkImageId = null }) {
                    androidx.compose.material3.Text(stringResource(R.string.image_link_cancel))
                }
            }
        )
    }

    if (showImagePositionDialog && imagePositionTargetId != null) {
        val srcId = imagePositionTargetId!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showImagePositionDialog = false
                imagePositionTargetId = null
            },
            title = { androidx.compose.material3.Text(stringResource(R.string.image_position_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(stringResource(R.string.image_position_move_title))
                    androidx.compose.material3.Text(stringResource(R.string.image_position_move_hint))
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.startImageMove(srcId)
                        showImagePositionDialog = false
                        imagePositionTargetId = null
                    }) { androidx.compose.material3.Text(stringResource(R.string.image_position_move_title)) }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Text(stringResource(R.string.image_position_swap_title))
                    androidx.compose.material3.Text(stringResource(R.string.image_position_swap_hint))
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.startImageSwap(srcId)
                        showImagePositionDialog = false
                        imagePositionTargetId = null
                    }) { androidx.compose.material3.Text(stringResource(R.string.image_position_swap_title)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showImagePositionDialog = false
                    imagePositionTargetId = null
                }) { androidx.compose.material3.Text(stringResource(R.string.image_position_cancel)) }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showImportSettings by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showTextBgColorPicker by remember { mutableStateOf(false) }
    var showTextShadowPanel by remember { mutableStateOf(false) }
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
                val replaceId = viewModel.pendingReplaceImageId
                viewModel.importImages(uris, replaceId = replaceId)
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

    val backgroundImagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                viewModel.setPageBackgroundFromUri(uri)
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
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    var showNameDialog by remember { mutableStateOf(false) }
                    var showSuccessDialog by remember { mutableStateOf(false) }
                    var showErrorDialog by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf("") }
                    var savedFileName by remember { mutableStateOf("") }
                    var linkWarning by remember { mutableStateOf<String?>(null) }
                    var linkWarning by remember { mutableStateOf<String?>(null) }
                    var nameField by remember {
                        mutableStateOf(TextFieldValue("Document.pdf", TextRange(0, 8)))
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_pdf)) },
                                onClick = {
                                    menuExpanded = false
                                    if (viewModel.importedImages.isEmpty() && viewModel.textElements.isEmpty()) {
                                        errorMessage = context.getString(R.string.export_pdf_empty)
                                        showErrorDialog = true
                                    } else {
                                        nameField = TextFieldValue("Document.pdf", TextRange(0, 8))
                                        showNameDialog = true
                                    }
                                }
                            )
                        }
                    }

                    // --- Name dialog ---
                    if (showNameDialog) {
                        AlertDialog(
                            onDismissRequest = { showNameDialog = false },
                            title = { Text(stringResource(R.string.export_pdf_dialog_title)) },
                            text = {
                                OutlinedTextField(
                                    value = nameField,
                                    onValueChange = { nameField = it },
                                    label = { Text(stringResource(R.string.export_pdf_name_hint)) },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showNameDialog = false
                                    val raw = nameField.text.trim().ifEmpty { "Document.pdf" }
                                    val result = PdfGenerator.export(
                                        context = context,
                                        fileName = raw,
                                        images = viewModel.importedImages.toList(),
                                        textElements = viewModel.textElements.toList(),
                                        imagesPerRow = viewModel.imagesPerRow,
                                        pageAspectRatio = viewModel.pageAspectRatio,
                                        pageBackgroundColor = viewModel.pageBackgroundColor,
                                        imageSpacingDp = viewModel.imageSpacingDp,
                                        imageCellAspectRatio = viewModel.imageCellAspectRatio,
                                        pageMarginDp = 10,
                                        minPageCount = viewModel.minPageCount,
                                        pageAspectRatioForPage = viewModel::aspectRatioForPage
                                    )
                                    if (result.success) {
                                        savedFileName = result.fileName
                                        linkWarning = result.linkWarning
                                        linkWarning = result.linkWarning
                                        showSuccessDialog = true
                                    } else {
                                        errorMessage = if (result.message == "empty")
                                            context.getString(R.string.export_pdf_empty)
                                        else
                                            context.getString(R.string.export_pdf_failed)
                                        showErrorDialog = true
                                    }
                                }) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNameDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    // --- Success dialog ---
                    if (showSuccessDialog) {
                        AlertDialog(
                            onDismissRequest = { showSuccessDialog = false },
                            title = { Text(stringResource(R.string.export_pdf_success_title)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.export_pdf_success_message, savedFileName))
                                    val warning = linkWarning
                                    if (warning != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Link warning: $warning",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSuccessDialog = false }) {
                                    Text(stringResource(R.string.export_pdf_done))
                                }
                            }
                        )
                    }

                    // --- Error dialog ---
                    if (showErrorDialog) {
                        AlertDialog(
                            onDismissRequest = { showErrorDialog = false },
                            title = { Text(stringResource(R.string.export_pdf_failed)) },
                            text = { Text(errorMessage) },
                            confirmButton = {
                                TextButton(onClick = { showErrorDialog = false }) {
                                    Text(stringResource(R.string.export_pdf_done))
                                }
                            }
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

        
    if (showTextColorPicker) {
        ColorPickerDialog(
            initialColor = viewModel.selectedTextColorArgb(),
            onConfirm = {
                viewModel.updateSelectedTextColor(it)
                showTextColorPicker = false
            },
            onDismiss = { showTextColorPicker = false }
        )
    }
    if (showTextBgColorPicker) {
        ColorPickerDialog(
            initialColor = viewModel.selectedTextBgColorArgb() ?: 0xFFFFFF00,
            onConfirm = {
                viewModel.updateSelectedTextBgColor(it)
                showTextBgColorPicker = false
            },
            onDismiss = { showTextBgColorPicker = false }
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
                    viewModel.selectedTextId != null,
                onTextColorClick = { if (viewModel.selectedTextId != null) showTextColorPicker = true },
                onTextBgColorClick = { if (viewModel.selectedTextId != null) showTextBgColorPicker = true },
                onTextShadowClick = { if (viewModel.selectedTextId != null) showTextShadowPanel = true },
                textSizeSp =
                    viewModel.selectedTextSizeSp(),
                onTextSizeClick = { },
                onTextSizeChange =
                    viewModel::updateSelectedTextSize,
                pageAspectRatio =
                    viewModel.pageAspectRatio,
                onPageAspectRatioChange =
                    viewModel::applyPageSizeToSelection,
                pageCountForSize =
                    viewModel.currentPageCountEstimate(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    ),
                pageSizeSelected =
                    viewModel.pageSizeSelection.toSet(),
                onTogglePageSizeSelection =
                    viewModel::togglePageSizeSelection,
                onToggleSelectAllPagesForSize = {
                    viewModel.toggleSelectAllPagesForSize(
                        viewModel.currentPageCountEstimate(
                            (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                        )
                    )
                },
                onClearPageSizeSelection =
                    viewModel::clearPageSizeSelection,
                sliderAspectForSelection =
                    viewModel.pageSizeSelection.firstOrNull()?.let {
                        viewModel.aspectRatioForPage(it)
                    } ?: viewModel.pageAspectRatio,
                isPageLandscape =
                    viewModel.isPageLandscape,
                onPageOrientationChange =
                    viewModel::updatePageOrientation,
                pageMarginDp =
                    viewModel.pageMarginDp,
                onPageMarginChange =
                    viewModel::updatePageMarginDp,
                pageBackgroundColor =
                    viewModel.pageBackgroundColor,
                onPageBackgroundColorChange =
                    viewModel::applyBackgroundColorToSelection,
                pageCountForBgColor =
                    viewModel.currentPageCountEstimate(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    ),
                pageBgColorSelected =
                    viewModel.pageBgColorSelection.toSet(),
                onTogglePageBgColorSelection =
                    viewModel::togglePageBgColorSelection,
                onToggleSelectAllPagesForBgColor = {
                    viewModel.toggleSelectAllPagesForBgColor(
                        viewModel.currentPageCountEstimate(
                            (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                        )
                    )
                },
                onClearPageBgColorSelection =
                    viewModel::clearPageBgColorSelection,
                onPickBackgroundImage = {
                    backgroundImagePickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onClearBackgroundImage =
                    viewModel::clearPageBackgroundImage,
                hasBackgroundImage =
                    viewModel.pageBackgroundImageUri != null,
                pageNumberPosition =
                    viewModel.pageNumberPosition,
                onPageNumberPositionChange =
                    viewModel::updatePageNumberPosition,
                pageNumberStyle =
                    viewModel.pageNumberStyle,
                onPageNumberStyleChange =
                    viewModel::updatePageNumberStyle,
                onAddNewPage =
                    viewModel::addNewPage,
                onDeletePage =
                    viewModel::deleteLastPage,
                pageCountForDelete =
                    viewModel.currentPageCountEstimate(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    ),
                pageDeleteSelected =
                    viewModel.pageDeleteSelection.toSet(),
                onTogglePageDeleteSelection =
                    viewModel::togglePageDeleteSelection,
                onToggleSelectAllPagesForDelete = {
                    viewModel.toggleSelectAllPagesForDelete(
                        viewModel.currentPageCountEstimate(
                            (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                        )
                    )
                },
                onClearPageDeleteSelection =
                    viewModel::clearPageDeleteSelection,
                onDeleteSelectedPages = {
                    viewModel.deleteSelectedPages(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    )
                },
                pageCountForDuplicate =
                    viewModel.currentPageCountEstimate(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    ),
                pageDuplicateSelected =
                    viewModel.pageDuplicateSelection.toSet(),
                onTogglePageDuplicateSelection =
                    viewModel::togglePageDuplicateSelection,
                onToggleSelectAllPagesForDuplicate = {
                    viewModel.toggleSelectAllPagesForDuplicate(
                        viewModel.currentPageCountEstimate(
                            (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                        )
                    )
                },
                onClearPageDuplicateSelection =
                    viewModel::clearPageDuplicateSelection,
                onDuplicateSelectedPages = {
                    viewModel.duplicateSelectedPages(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    )
                },
                pageCountForArrange =
                    viewModel.currentPageCountEstimate(
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    ),
                onReorderPages = { order ->
                    viewModel.reorderPages(
                        order,
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    )
                },
                onMovePage = { from, to ->
                    viewModel.movePageTo(
                        from,
                        to,
                        (viewModel.imagesPerRow * 2).coerceAtLeast(1)
                    )
                }
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

                    pageAspectRatio = viewModel.pageAspectRatio,
                    pageAspectRatioForPage = viewModel::aspectRatioForPage,
                    pageMarginDp = viewModel.pageMarginDp,
                    pageBackgroundColor = viewModel.pageBackgroundColor,
                    pageBackgroundColorForPage = viewModel::backgroundColorForPage,
                    pageBackgroundBitmap = viewModel.pageBackgroundBitmap,
                    pageBackgroundBitmapForPage = viewModel::backgroundBitmapForPage,
                    pageNumberPosition = viewModel.pageNumberPosition,
                    formatPageNumber = viewModel::formatPageNumber,
                    minPageCount = viewModel.minPageCount,

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

                    onImageLongPress = { _ ->
                        // Long-press multi-select removed
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

                    onCropImage = { id ->
                        viewModel.dismissImageMenu()
                        cropImageId = id
                    },
                    onRotateImage = { id ->
                        viewModel.dismissImageMenu()
                        rotateImageId = id
                        rotateDegrees = 0f
                    },
                    onShareSingle = { id ->
                        viewModel.getImage(id)?.let { image ->
                            shareImages(
                                context = context,
                                images = listOf(image)
                            )
                        }
                    },

                    onReplaceImage = { id ->
                        viewModel.startReplaceImage(id)
                        showImportSettings = true
                    },
                    onImagePosition = { id ->
                        imagePositionTargetId = id
                        showImagePositionDialog = true
                    },
                    onAddImageLink = { id ->
                        linkImageId = id
                        linkUrlText = viewModel.getImage(id)?.linkUrl.orEmpty()
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



@Composable
private fun CropImageScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onApply: (leftFrac: Float, topFrac: Float, rightFrac: Float, bottomFrac: Float) -> Unit
) {
    // Normalized crop rect inside the image (0..1)
    var left by remember { mutableStateOf(0.1f) }
    var top by remember { mutableStateOf(0.1f) }
    var right by remember { mutableStateOf(0.9f) }
    var bottom by remember { mutableStateOf(0.9f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.crop_cancel), color = Color.White)
                }
                Text(
                    text = stringResource(R.string.crop_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { onApply(left, top, right, bottom) }) {
                    Text(stringResource(R.string.crop_apply), color = Color.White)
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val maxW = maxWidth
                val maxH = maxHeight
                val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val boxAspect = maxW / maxH
                val drawW: androidx.compose.ui.unit.Dp
                val drawH: androidx.compose.ui.unit.Dp
                if (imgAspect > boxAspect) {
                    drawW = maxW
                    drawH = maxW / imgAspect
                } else {
                    drawH = maxH
                    drawW = maxH * imgAspect
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(drawW)
                        .height(drawH)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    // Darken outside crop — simple border rectangle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = drawW * left,
                                top = drawH * top,
                                end = drawW * (1f - right),
                                bottom = drawH * (1f - bottom)
                            )
                            .border(2.dp, Color.White)
                            .pointerInput(Unit) {
                                val areaW = size.width.toFloat().coerceAtLeast(1f)
                                val areaH = size.height.toFloat().coerceAtLeast(1f)
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val dx = drag.x / areaW
                                    val dy = drag.y / areaH
                                    val w = right - left
                                    val h = bottom - top
                                    val nl = (left + dx).coerceIn(0f, 1f - w)
                                    val nt = (top + dy).coerceIn(0f, 1f - h)
                                    left = nl
                                    top = nt
                                    right = nl + w
                                    bottom = nt + h
                                }
                            }
                    )
                    // Corner handles: expand/shrink
                    val handle = 28.dp
                    // Top-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = drawW * left - 12.dp, y = drawH * top - 12.dp)
                            .size(handle)
                            .background(Color.White, CircleShape)
                            .pointerInput(Unit) {
                                val areaW = size.width.toFloat().coerceAtLeast(1f)
                                val areaH = size.height.toFloat().coerceAtLeast(1f)
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    left = (left + drag.x / areaW).coerceIn(0f, right - 0.1f)
                                    top = (top + drag.y / areaH).coerceIn(0f, bottom - 0.1f)
                                }
                            }
                    )
                    // Bottom-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = drawW * right - 12.dp, y = drawH * bottom - 12.dp)
                            .size(handle)
                            .background(Color.White, CircleShape)
                            .pointerInput(Unit) {
                                val areaW = size.width.toFloat().coerceAtLeast(1f)
                                val areaH = size.height.toFloat().coerceAtLeast(1f)
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    right = (right + drag.x / areaW).coerceIn(left + 0.1f, 1f)
                                    bottom = (bottom + drag.y / areaH).coerceIn(top + 0.1f, 1f)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun RotateImageDialog(
    bitmap: Bitmap,
    degrees: Float,
    onDegreesChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    val preview = remember(bitmap, degrees) {
        if (degrees % 360f == 0f) bitmap
        else {
            val m = android.graphics.Matrix().apply { postRotate(degrees) }
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.rotate_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { onDegreesChange((degrees + 90f) % 360f) }) {
                    Text(stringResource(R.string.rotate_right))
                }
                TextButton(onClick = { onDegreesChange((degrees + 270f) % 360f) }) {
                    Text(stringResource(R.string.rotate_left))
                }
                TextButton(onClick = { onDegreesChange((degrees + 180f) % 360f) }) {
                    Text(stringResource(R.string.rotate_180))
                }
                TextButton(onClick = { onDegreesChange(0f) }) {
                    Text(stringResource(R.string.rotate_reset))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOk) { Text(stringResource(R.string.rotate_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.rotate_cancel)) }
        }
    )
}
