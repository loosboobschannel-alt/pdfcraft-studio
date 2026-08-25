package com.pdfcraft.studio.ui.editor.canvas
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.material3.LocalTextStyle

import androidx.compose.foundation.layout.wrapContentSize


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.pdfcraft.studio.ui.common.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.text.AppFont
import com.pdfcraft.studio.core.text.FontCatalog
import com.pdfcraft.studio.ui.editor.ImportedImage
import com.pdfcraft.studio.ui.editor.ColorRange
import com.pdfcraft.studio.ui.editor.TextElement
import com.pdfcraft.studio.ui.editor.LinkRange

private const val PAGE_ASPECT_RATIO = 9f / 16f
private const val PAGE_INNER_PADDING_DP = 10f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPagesPreview(
    images: List<ImportedImage>,
    imagesPerRow: Int,
    imageSpacingDp: Int,
    imageCellAspectRatio: Float = 1f,
    imageCornerRadiusPercent: Int = 0,
    pageAspectRatio: Float = PAGE_ASPECT_RATIO,
    pageAspectRatioForPage: ((Int) -> Float)? = null,
    pageMarginDp: Int = 10,
    pageBackgroundColor: Long = 0xFFFFFFFFL,
    pageBackgroundColorForPage: ((Int) -> Long)? = null,
    pageBackgroundBitmap: android.graphics.Bitmap? = null,
    pageBackgroundBitmapForPage: ((Int) -> android.graphics.Bitmap?)? = null,
    pageNumberPosition: com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition =
        com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.NONE,
    formatPageNumber: (Int) -> String = { (it + 1).toString() },
    minPageCount: Int = 1,
    selectedImageIds: List<String> = emptyList(),
    selectionMode: Boolean = false,
    singleMenuImageId: String? = null,
    multipleActionsVisible: Boolean = false,
    reorderMode: Boolean = false,
    hasClipboardImages: Boolean = false,
    textElements: List<TextElement> = emptyList(),
    addTextMode: Boolean = false,
    selectedTextId: String? = null,
    pendingFocusTextId: String? = null,
    onAddTextAt: (pageIndex: Int, xFraction: Float, yFraction: Float) -> Unit = { _, _, _ -> },
    onSelectText: (String) -> Unit = {},
    onMoveText: (id: String, xFraction: Float, yFraction: Float) -> Unit = { _, _, _ -> },
    onTextValueChange: (id: String, newText: String, newSelection: TextRange) -> Unit = { _, _, _ -> },
    onTextFocused: (String) -> Unit = {},
    onTextUnfocused: (String) -> Unit = {},
    onConsumePendingFocus: () -> Unit = {},
    onDeselectText: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onImageLongPress: (String) -> Unit = {},
    onChangePosition: (String) -> Unit = {},
    onCut: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onDeleteSingle: (String) -> Unit = {},
    onPaste: () -> Unit = {},
    onSaveSingle: (String) -> Unit = {},
    onShareSingle: (String) -> Unit = {},
    onCropImage: (String) -> Unit = {},
    onRotateImage: (String) -> Unit = {},
    onReplaceImage: (String) -> Unit = {},
    onImagePosition: (String) -> Unit = {},
    onAddImageLink: (String) -> Unit = {},
    onFinishMultipleSelection: () -> Unit = {},
    onMultipleChangePosition: () -> Unit = {},
    onMultipleCut: () -> Unit = {},
    onMultipleCopy: () -> Unit = {},
    onMultipleSave: () -> Unit = {},
    onMultipleShare: () -> Unit = {},
    onMultipleDelete: () -> Unit = {},
    onCloseMultipleActions: () -> Unit = {},
    onMoveSingle: (String, String) -> Unit = { _, _ -> },
    onMoveMultiple: (String) -> Unit = {},
    onFinishReorder: () -> Unit = {},
    customFonts: List<AppFont> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Always show at least one page so text can be added before any images.
    if (images.isEmpty() && minPageCount <= 1) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.page_label, 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        PageCard(
                            aspectRatio = pageAspectRatioForPage?.invoke(0) ?: pageAspectRatio,
                            backgroundColor = pageBackgroundColorForPage?.invoke(0) ?: pageBackgroundColor,
                            backgroundBitmap = pageBackgroundBitmapForPage?.invoke(0) ?: pageBackgroundBitmap,
                            pageNumberText = if (pageNumberPosition != com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.NONE)
                                formatPageNumber(0) else null,
                            pageNumberPosition = pageNumberPosition
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Soft hint only while page has no text yet
                                if (textElements.none { it.pageIndex == 0 } && minPageCount <= 1) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.editor_empty_state),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black.copy(alpha = 0.55f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.editor_empty_state_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Black.copy(alpha = 0.38f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                PageTextOverlay(
                                    texts = textElements.filter { it.pageIndex == 0 },
                                    addTextMode = addTextMode,
                                    selectedTextId = selectedTextId,
                                    pendingFocusTextId = pendingFocusTextId,
                                    customFonts = customFonts,
                                    onPageTap = { xFrac, yFrac -> onAddTextAt(0, xFrac, yFrac) },
                                    onTextSelect = onSelectText,
                                    onTextDrag = onMoveText,
                                    onTextValueChange = onTextValueChange,
                                    onTextFocused = onTextFocused,
                                    onTextUnfocused = onTextUnfocused,
                                    onConsumePendingFocus = onConsumePendingFocus,
                                    onDeselect = onDeselectText
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val cellBounds = remember { mutableStateMapOf<String, Rect>() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val pageWidthDp = maxWidth.value
        val pageHeightDp = pageWidthDp / pageAspectRatio

        val gridWidthDp = pageWidthDp - (PAGE_INNER_PADDING_DP * 2)
        val gridHeightDp = pageHeightDp - (PAGE_INNER_PADDING_DP * 2)
        val spacing = imageSpacingDp.toFloat()

        val cellWidthDp = (gridWidthDp - spacing * (imagesPerRow - 1)) / imagesPerRow
        val cellHeightDp = if (imageCellAspectRatio > 0f) {
            cellWidthDp / imageCellAspectRatio
        } else {
            cellWidthDp
        }

        val rowsPerPage = if (cellHeightDp > 0f) {
            (((gridHeightDp + spacing) / (cellHeightDp + spacing)).toInt()).coerceAtLeast(1)
        } else {
            1
        }

        // Auto-flow: only as many images as fit on a page; overflow goes to next page.
        val imagesPerPage = (imagesPerRow * rowsPerPage).coerceAtLeast(1)
        val contentPages = if (images.isEmpty()) emptyList() else images.chunked(imagesPerPage)
        val pages = if (contentPages.size >= minPageCount) contentPages
            else contentPages + List(minPageCount - contentPages.size) { emptyList() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(
                pages,
                key = { index, _ -> index }
            ) { pageIndex, pageImages ->

                Column {
                    Text(
                        text = stringResource(
                            R.string.page_label,
                            pageIndex + 1
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    PageCard(
                        aspectRatio = pageAspectRatioForPage?.invoke(pageIndex) ?: pageAspectRatio,
                        backgroundColor = pageBackgroundColorForPage?.invoke(pageIndex) ?: pageBackgroundColor,
                        backgroundBitmap = pageBackgroundBitmapForPage?.invoke(pageIndex) ?: pageBackgroundBitmap,
                        pageNumberText = if (pageNumberPosition != com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.NONE)
                            formatPageNumber(pageIndex) else null,
                        pageNumberPosition = pageNumberPosition
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(pageMarginDp.dp)) {
                            ImageGrid(
                                images = pageImages,
                                imagesPerRow = imagesPerRow,
                                spacingDp = imageSpacingDp,
                                cellAspectRatio = imageCellAspectRatio,
                                cellCornerRadiusPercent = imageCornerRadiusPercent,
                                selectedImageIds = selectedImageIds,
                                selectionMode = selectionMode,
                                singleMenuImageId = singleMenuImageId,
                                reorderMode = reorderMode,
                                hasClipboardImages = hasClipboardImages,
                                cellBounds = cellBounds,
                                onImageClick = onImageClick,
                                onImageLongPress = onImageLongPress,
                                onChangePosition = onChangePosition,
                                onCut = onCut,
                                onCopy = onCopy,
                                onPaste = onPaste,
                                onSaveSingle = onSaveSingle,
                                onShareSingle = onShareSingle,
                                onCropImage = onCropImage,
                                onRotateImage = onRotateImage,
                                onReplaceImage = onReplaceImage,
                                onImagePosition = onImagePosition,
                                onAddImageLink = onAddImageLink,
                                onDeleteSingle = onDeleteSingle,
                                onMoveSingle = onMoveSingle,
                                onMoveMultiple = onMoveMultiple,
                                onFinishReorder = onFinishReorder
                            )

                            PageTextOverlay(
                                texts = textElements.filter { it.pageIndex == pageIndex },
                                addTextMode = addTextMode,
                                selectedTextId = selectedTextId,
                                pendingFocusTextId = pendingFocusTextId,
                                customFonts = customFonts,
                                onPageTap = { xFrac, yFrac -> onAddTextAt(pageIndex, xFrac, yFrac) },
                                onTextSelect = onSelectText,
                                onTextDrag = onMoveText,
                                onTextValueChange = onTextValueChange,
                                onTextFocused = onTextFocused,
                                onTextUnfocused = onTextUnfocused,
                                onConsumePendingFocus = onConsumePendingFocus,
                                onDeselect = onDeselectText
                            )
                        }
                    }
                }
            }
        }

        // Long-press selection Done button removed

    }

    // MultipleActionsDialog removed (long-press flow)

}

private class TextStyleRangesVisualTransformation(
    private val boldRanges: List<IntRange>,
    private val italicRanges: List<IntRange>,
    private val colorRanges: List<ColorRange> = emptyList(),
    private val bgColorRanges: List<ColorRange> = emptyList(),
    private val wholeBgColorArgb: Long? = null,
    private val linkRanges: List<LinkRange> = emptyList()
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        if (wholeBgColorArgb != null && text.text.isNotEmpty()) {
            builder.addStyle(
                SpanStyle(background = Color(wholeBgColorArgb)),
                0,
                text.text.length
            )
        }
        boldRanges.forEach { range ->
            val start = range.first.coerceIn(0, text.text.length)
            val end = (range.last + 1).coerceIn(0, text.text.length)
            if (start < end) {
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
        italicRanges.forEach { range ->
            val start = range.first.coerceIn(0, text.text.length)
            val end = (range.last + 1).coerceIn(0, text.text.length)
            if (start < end) {
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
        }
        colorRanges.forEach { cr ->
            val start = cr.range.first.coerceIn(0, text.text.length)
            val end = (cr.range.last + 1).coerceIn(0, text.text.length)
            if (start < end) {
                builder.addStyle(SpanStyle(color = Color(cr.colorArgb)), start, end)
            }
        }
        bgColorRanges.forEach { cr ->
            val start = cr.range.first.coerceIn(0, text.text.length)
            val end = (cr.range.last + 1).coerceIn(0, text.text.length)
            if (start < end) {
                builder.addStyle(SpanStyle(background = Color(cr.colorArgb)), start, end)
            }
        }
        linkRanges.forEach { lr ->
            val start = lr.range.first.coerceIn(0, text.text.length)
            val end = (lr.range.last + 1).coerceIn(0, text.text.length)
            if (start < end) {
                builder.addStyle(
                    SpanStyle(
                        color = Color(0xFF1976D2),
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    end
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTextOverlay(
    texts: List<TextElement>,
    addTextMode: Boolean,
    selectedTextId: String?,
    pendingFocusTextId: String?,
    customFonts: List<AppFont>,
    onPageTap: (xFraction: Float, yFraction: Float) -> Unit,
    onTextSelect: (String) -> Unit,
    onTextDrag: (id: String, xFraction: Float, yFraction: Float) -> Unit,
    onTextValueChange: (id: String, newText: String, newSelection: TextRange) -> Unit,
    onTextFocused: (String) -> Unit,
    onTextUnfocused: (String) -> Unit,
    onConsumePendingFocus: () -> Unit,
    onDeselect: () -> Unit
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
            // IMPORTANT: only intercept taps while placing new text.
            // Otherwise this full-size layer steals clicks from images underneath
            // and the Save/Share menu never opens.
            .then(
                if (addTextMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (sizePx.width > 0 && sizePx.height > 0) {
                                val xFraction = (offset.x / sizePx.width).coerceIn(0f, 1f)
                                val yFraction = (offset.y / sizePx.height).coerceIn(0f, 1f)
                                onPageTap(xFraction, yFraction)
                            }
                        }
                    }
                } else if (selectedTextId != null) {
                    Modifier.pointerInput(selectedTextId) {
                        detectTapGestures {
                            focusManager.clearFocus()
                            onDeselect()
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        texts.forEach { textElement ->
            var fieldValue by remember(textElement.id) {
                mutableStateOf(TextFieldValue(text = textElement.text))
            }
            val focusRequester = remember { FocusRequester() }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (textElement.xFraction * sizePx.width).toInt(),
                            (textElement.yFraction * sizePx.height).toInt()
                        )
                    }
                    .then(
                        if (textElement.id == selectedTextId) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        }
                    )
                    .padding(4.dp)
            ) {
                Text(
                    text = "\u28FF",
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .pointerInput(textElement.id) {
                            var runningXFraction = textElement.xFraction
                            var runningYFraction = textElement.yFraction
                            detectDragGestures(
                                onDragStart = { onTextSelect(textElement.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (sizePx.width > 0 && sizePx.height > 0) {
                                        runningXFraction = (runningXFraction + dragAmount.x / sizePx.width).coerceIn(0f, 1f)
                                        runningYFraction = (runningYFraction + dragAmount.y / sizePx.height).coerceIn(0f, 1f)
                                        onTextDrag(textElement.id, runningXFraction, runningYFraction)
                                    }
                                }
                            )
                        }
                )

                val fontFamily = FontCatalog.resolveComposeFontFamily(
                    textElement.fontId,
                    customFonts
                )
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        fieldValue = newValue
                        onTextValueChange(textElement.id, newValue.text, newValue.selection)
                    },
                    visualTransformation = TextStyleRangesVisualTransformation(
                        boldRanges = textElement.boldRanges,
                        italicRanges = textElement.italicRanges,
                        colorRanges = textElement.colorRanges,
                        bgColorRanges = textElement.bgColorRanges,
                        wholeBgColorArgb = textElement.bgColorArgb,
                        linkRanges = textElement.linkRanges
                    ),
                    textStyle = TextStyle(
                        color = Color(textElement.textColorArgb),
                        fontSize = textElement.fontSizeSp.sp,
                        fontFamily = fontFamily
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    modifier = Modifier
                        .widthIn(min = 24.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                onTextFocused(textElement.id)
                            } else {
                                onTextUnfocused(textElement.id)
                            }
                        }
                )
            }

            LaunchedEffect(pendingFocusTextId, textElement.id) {
                if (pendingFocusTextId == textElement.id) {
                    focusRequester.requestFocus()
                    onConsumePendingFocus()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGrid(
    images: List<ImportedImage>,
    imagesPerRow: Int,
    spacingDp: Int,
    cellAspectRatio: Float,
    cellCornerRadiusPercent: Int,
    selectedImageIds: List<String>,
    selectionMode: Boolean,
    singleMenuImageId: String?,
    reorderMode: Boolean,
    hasClipboardImages: Boolean,
    cellBounds: MutableMap<String, Rect>,
    onImageClick: (String) -> Unit,
    onImageLongPress: (String) -> Unit,
    onChangePosition: (String) -> Unit,
    onCut: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: () -> Unit,
    onSaveSingle: (String) -> Unit,
    onShareSingle: (String) -> Unit,
    onCropImage: (String) -> Unit,
    onRotateImage: (String) -> Unit,
    onReplaceImage: (String) -> Unit,
    onImagePosition: (String) -> Unit,
    onAddImageLink: (String) -> Unit,
    onDeleteSingle: (String) -> Unit,
    onMoveSingle: (String, String) -> Unit,
    onMoveMultiple: (String) -> Unit,
    onFinishReorder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
    ) {
        images.chunked(imagesPerRow).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
            ) {
                rowImages.forEach { image ->
                    ImageCell(
                        image = image,
                        modifier = Modifier.weight(1f),
                        aspectRatio = cellAspectRatio,
                        cornerRadiusPercent = cellCornerRadiusPercent,
                        selected = selectedImageIds.contains(image.id),
                        reorderMode = reorderMode,
                        showMenu = singleMenuImageId == image.id,
                        hasClipboardImages = hasClipboardImages,
                        cellBounds = cellBounds,
                        onClick = {
                            onImageClick(image.id)
                        },
                        onLongPress = {
                            // long-press multi-select removed
                        },
                        onChangePosition = {
                            onChangePosition(image.id)
                        },
                        onCut = {
                            onCut(image.id)
                        },
                        onCopy = {
                            onCopy(image.id)
                        },
                        onPaste = onPaste,
                        onSave = {
                            onSaveSingle(image.id)
                        },
                        onShare = {
                            onShareSingle(image.id)
                        },
                        onCrop = {
                            onCropImage(image.id)
                        },
                        onRotate = {
                            onRotateImage(image.id)
                        },
                        onReplace = {
                            onReplaceImage(image.id)
                        },
                        onImagePosition = {
                            onImagePosition(image.id)
                        },
                        onAddLink = {
                            onAddImageLink(image.id)
                        },
                        onDelete = {
                            onDeleteSingle(image.id)
                        },
                        onDropOnTarget = { targetId ->
                            if (selectedImageIds.size > 1) {
                                onMoveMultiple(targetId)
                            } else {
                                onMoveSingle(image.id, targetId)
                            }
                            onFinishReorder()
                        }
                    )
                }

                repeat(imagesPerRow - rowImages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCell(
    image: ImportedImage,
    modifier: Modifier = Modifier,
    aspectRatio: Float,
    cornerRadiusPercent: Int,
    selected: Boolean,
    reorderMode: Boolean,
    showMenu: Boolean,
    hasClipboardImages: Boolean,
    cellBounds: MutableMap<String, Rect>,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onChangePosition: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onReplace: () -> Unit,
    onImagePosition: () -> Unit,
    onAddLink: () -> Unit,
    onDelete: () -> Unit,
    onDropOnTarget: (String) -> Unit
) {
    var layoutCoordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .onGloballyPositioned { coordinates ->
                layoutCoordinates = coordinates
                val position = coordinates.positionInRoot()

                cellBounds[image.id] = Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + coordinates.size.width,
                    bottom = position.y + coordinates.size.height
                )
            }
    ) {
        // Clip only the image content — NOT the dropdown menu
        Box(
            modifier = Modifier
                .fillMaxSize()
            .offset(
                // Page-relative: \~full editor page width/height in dp (matches 360x\~640 logical page)
                x = (image.dragOffsetXFrac * 360f).dp,
                y = (image.dragOffsetYFrac * 640f).dp
            )
                .clip(RoundedCornerShape(percent = cornerRadiusPercent))
                
                .then(
                    if (reorderMode) {
                        Modifier.pointerInput(image.id) {
                            detectDragGestures(
                                onDragStart = {},
                                onDrag = { change, _ ->
                                    change.consume()
                                },
                                onDragEnd = {
                                    val coordinates = layoutCoordinates
                                        ?: return@detectDragGestures

                                    val center =
                                        coordinates.localToRoot(
                                            Offset(
                                                coordinates.size.width / 2f,
                                                coordinates.size.height / 2f
                                            )
                                        )

                                    val target = cellBounds.entries
                                        .firstOrNull { entry ->
                                            entry.key != image.id &&
                                                entry.value.contains(center)
                                        }

                                    if (target != null) {
                                        onDropOnTarget(target.key)
                                    }
                                },
                                onDragCancel = {}
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { /* long-press removed */ }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (image.bitmap != null) {
                Image(
                    bitmap = image.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

            // Image numbering badge — position inside ContentScale.Fit bounds
            val label = image.numberLabel
            val badgeBmp = image.bitmap
            if (label != null && badgeBmp != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val cellW = maxWidth
                    val cellH = maxHeight
                    val bmpAspect = badgeBmp.width.toFloat() / badgeBmp.height.toFloat().coerceAtLeast(1f)
                    val cellAspect = (cellW / cellH)
                    val drawW: androidx.compose.ui.unit.Dp
                    val drawH: androidx.compose.ui.unit.Dp
                    if (bmpAspect > cellAspect) {
                        drawW = cellW
                        drawH = cellW / bmpAspect
                    } else {
                        drawH = cellH
                        drawW = cellH * bmpAspect
                    }
                    val originX = (cellW - drawW) / 2
                    val originY = (cellH - drawH) / 2
                    val sizeFrac = image.numberSizeFrac.coerceIn(0.08f, 0.4f)
                    val iconD = minOf(drawW, drawH) * sizeFrac
                    val half = iconD / 2
                    val xf = image.numberXFrac.coerceIn(0f, 1f)
                    val yf = image.numberYFrac.coerceIn(0f, 1f)
                    val cx = originX + drawW * xf
                    val cy = originY + drawH * yf
                    val clampedCx = cx.coerceIn(originX + half, originX + drawW - half)
                    val clampedCy = cy.coerceIn(originY + half, originY + drawH - half)
                    val bg = Color(image.numberBgArgb.toInt())
                    val fg = Color(image.numberFgArgb.toInt())
                    val a = image.numberAlpha.coerceIn(0.2f, 1f)
                    val w = image.numberWeight.coerceIn(0f, 1f)
                    val fw = when {
                        w < 0.34f -> FontWeight.Light
                        w < 0.67f -> FontWeight.Normal
                        else -> FontWeight.Bold
                    }
                    val fs = (iconD.value * 0.42f).sp
                    Box(
                        modifier = Modifier
                            .offset(x = clampedCx - half, y = clampedCy - half)
                            .size(iconD)
                            .background(bg.copy(alpha = bg.alpha * a), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label.toString(),
                            color = fg.copy(alpha = fg.alpha * a),
                            fontWeight = fw,
                            fontSize = fs,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeight = fs
                            ),
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                }
            }
            } else {
                // Real imports (have uri) show loading while bitmap decodes.
                // Spacer slots used for "start on page N" stay empty — no endless spinner.
                if (image.imageUri != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                )
            }
        }

        // Compact dropdown anchored to this image cell (same style as Image/Text Tools menus).
        // Outside tap or any image click dismisses via onDismissRequest / openImageMenu logic.
        var showInfoDialog by remember(image.id) { mutableStateOf(false) }
        if (showMenu && !reorderMode) {
            var savedToGallery by remember(image.id) { mutableStateOf(false) }
            DropdownMenu(
                expanded = true,
                onDismissRequest = onClick
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (savedToGallery) {
                                "✓ " + stringResource(R.string.save_in_gallery)
                            } else {
                                stringResource(R.string.save_in_gallery)
                            },
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSave()
                        savedToGallery = true
                        onClick() // close after use
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.share_image),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onShare()
                        onClick() // close after use
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.view_image_information),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        showInfoDialog = true
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_crop),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onCrop()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_rotate),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onRotate()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_menu_replace),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onReplace()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_menu_position),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onImagePosition()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_menu_add_link),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onAddLink()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.image_menu_delete),
                            color = Color.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    onClick = {
                        onClick()
                        onDelete()
                    }
                )
            }
            if (showInfoDialog) {
                val sizeLabel = formatImageSizeLabel(image)
                val bmp = image.bitmap
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.image_info_title))
                            IconButton(onClick = { showInfoDialog = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.image_info_ok)
                                )
                            }
                        }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.image_info_file_size, sizeLabel))
                            if (bmp != null && !bmp.isRecycled) {
                                Text(
                                    stringResource(
                                        R.string.image_info_dimensions,
                                        bmp.width,
                                        bmp.height
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text(stringResource(R.string.image_info_ok))
                        }
                    }
                )
            }
        }
    }
}

/** Human-readable size for the image info dialog. */
private fun formatImageSizeLabel(image: ImportedImage): String {
    val bytes: Int? = image.approxSizeBytes?.takeIf { it > 0 }
        ?: image.bitmap?.let { bmp ->
            if (bmp.isRecycled) return@let null
            try {
                val stream = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                stream.size()
            } catch (_: Exception) {
                bmp.byteCount
            }
        }
    if (bytes == null || bytes <= 0) return "—"
    return if (bytes < 1024) {
        "$bytes B"
    } else if (bytes < 1024 * 1024) {
        String.format("%.1f KB", bytes / 1024.0)
    } else {
        String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun SingleImageActionsMenu(
    expanded: Boolean,
    savedToGallery: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = if (savedToGallery) {
                        "✓ " + stringResource(R.string.save_in_gallery)
                    } else {
                        stringResource(R.string.save_in_gallery)
                    },
                    color = Color.Black
                )
            },
            onClick = onSave
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.share_image),
                    color = Color.Black
                )
            },
            onClick = onShare
        )
    }
}


@Composable
private fun MultipleActionsDialog(
    onChangePosition: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.selected_images_actions),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onChangePosition,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.change_position))
                }

                TextButton(
                    onClick = onCut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cut_image))
                }

                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.copy_image))
                }

                TextButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_in_gallery))
                }

                TextButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.share_image))
                }

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_all_images))
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun EmptyStatePage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = AppIcons.FileDocument,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.height(56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.editor_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.editor_empty_state_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun PageCard(
    aspectRatio: Float = PAGE_ASPECT_RATIO,
    backgroundColor: Long = 0xFFFFFFFFL,
    backgroundBitmap: android.graphics.Bitmap? = null,
    pageNumberText: String? = null,
    pageNumberPosition: com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition =
        com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.NONE,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(backgroundColor), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        content()
        if (pageNumberText != null &&
            pageNumberPosition != com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.NONE
        ) {
            val align = when (pageNumberPosition) {
                com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.LEFT -> Alignment.BottomStart
                com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.CENTER -> Alignment.BottomCenter
                com.pdfcraft.studio.ui.editor.EditorViewModel.PageNumberPosition.RIGHT -> Alignment.BottomEnd
                else -> Alignment.BottomCenter
            }
            Text(
                text = pageNumberText,
                color = Color.Black.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(align)
                    .padding(10.dp)
            )
        }
    }
}
