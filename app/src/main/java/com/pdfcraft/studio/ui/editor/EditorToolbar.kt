package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.pdfcraft.studio.R
import kotlin.math.roundToInt

@Composable
fun EditorToolbar(
    onImportImagesClick: () -> Unit,
    imagesPerRow: Int,
    onImagesPerRowSelected: (Int) -> Unit,
    imageSpacingDp: Int,
    onImageSpacingSelected: (Int) -> Unit,
    imageCellAspectRatio: Float,
    onImageCellAspectRatioSelected: (Float) -> Unit,
    imageCornerRadiusPercent: Int,
    onImageCornerRadiusSelected: (Int) -> Unit,
    onAddTextClick: () -> Unit,
    onFontClick: () -> Unit,
    onDeleteTextClick: () -> Unit,
    hasSelectedText: Boolean,
    onTextColorClick: () -> Unit = {},
    onTextBgColorClick: () -> Unit = {},
    onTextShadowClick: () -> Unit = {},
    textSizeSp: Float = 16f,
    onTextSizeClick: () -> Unit = {},
    onTextSizeChange: (Float) -> Unit = {},
    pageAspectRatio: Float,
    onPageAspectRatioChange: (Float) -> Unit,
    pageCountForSize: Int = 1,
    pageSizeSelected: Set<Int> = emptySet(),
    onTogglePageSizeSelection: (Int) -> Unit = {},
    onToggleSelectAllPagesForSize: () -> Unit = {},
    onClearPageSizeSelection: () -> Unit = {},
    sliderAspectForSelection: Float = pageAspectRatio,
    isPageLandscape: Boolean,
    onPageOrientationChange: (EditorViewModel.PageOrientation) -> Unit,
    pageMarginDp: Int,
    onPageMarginChange: (Int) -> Unit,
    pageBackgroundColor: Long,
    onPageBackgroundColorChange: (Long) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    hasBackgroundImage: Boolean,
    pageCountForBgColor: Int = 1,
    pageBgColorSelected: Set<Int> = emptySet(),
    onTogglePageBgColorSelection: (Int) -> Unit = {},
    onToggleSelectAllPagesForBgColor: () -> Unit = {},
    onClearPageBgColorSelection: () -> Unit = {},
    pageNumberPosition: EditorViewModel.PageNumberPosition,
    onPageNumberPositionChange: (EditorViewModel.PageNumberPosition) -> Unit,
    pageNumberStyle: EditorViewModel.PageNumberStyle,
    onPageNumberStyleChange: (EditorViewModel.PageNumberStyle) -> Unit,
    onAddNewPage: () -> Unit,
    onDeletePage: () -> Unit,
    pageCountForDelete: Int = 1,
    pageDeleteSelected: Set<Int> = emptySet(),
    onTogglePageDeleteSelection: (Int) -> Unit = {},
    onToggleSelectAllPagesForDelete: () -> Unit = {},
    onClearPageDeleteSelection: () -> Unit = {},
    onDeleteSelectedPages: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var resizeModeActive by remember { mutableStateOf(false) }
    var spacingModeActive by remember { mutableStateOf(false) }
    var shapeModeActive by remember { mutableStateOf(false) }
    var cornersModeActive by remember { mutableStateOf(false) }
    var pageSizeModeActive by remember { mutableStateOf(false) }
    var showPageSizePicker by remember { mutableStateOf(false) }
    var showPageBgColorPicker by remember { mutableStateOf(false) }
    var showPageDeletePicker by remember { mutableStateOf(false) }
    var pageBgColorModeActive by remember { mutableStateOf(false) }
    var pageMarginModeActive by remember { mutableStateOf(false) }
    var textSizeModeActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        when {
            resizeModeActive -> ImagesPerRowSlider(
                imagesPerRow = imagesPerRow,
                onImagesPerRowChange = onImagesPerRowSelected,
                onDone = { resizeModeActive = false }
            )
            spacingModeActive -> ImageSpacingSlider(
                spacingDp = imageSpacingDp,
                onSpacingChange = onImageSpacingSelected,
                onDone = { spacingModeActive = false }
            )
            shapeModeActive -> ImageShapeSlider(
                aspectRatio = imageCellAspectRatio,
                onAspectRatioChange = onImageCellAspectRatioSelected,
                onDone = { shapeModeActive = false }
            )
            cornersModeActive -> RoundCornersSlider(
                percent = imageCornerRadiusPercent,
                onPercentChange = onImageCornerRadiusSelected,
                onDone = { cornersModeActive = false }
            )
            textSizeModeActive -> TextSizeSlider(
                sizeSp = textSizeSp,
                onSizeChange = onTextSizeChange,
                onDone = { textSizeModeActive = false }
            )
            pageBgColorModeActive -> PageBackgroundColorPanel(
                pageBackgroundColor = pageBackgroundColor,
                hasBackgroundImage = hasBackgroundImage,
                onColorSelected = onPageBackgroundColorChange,
                onPickBackgroundImage = {
                    pageBgColorModeActive = false
                    onPickBackgroundImage()
                },
                onClearBackgroundImage = onClearBackgroundImage,
                onDone = { pageBgColorModeActive = false }
            )
            pageSizeModeActive -> PageSizeSlider(
                aspectRatio = sliderAspectForSelection,
                onAspectRatioChange = onPageAspectRatioChange,
                onDone = { pageSizeModeActive = false }
            )
            pageMarginModeActive -> PageMarginSlider(
                marginDp = pageMarginDp,
                onMarginChange = onPageMarginChange,
                onDone = { pageMarginModeActive = false }
            )
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                    PageToolsMenu(
                        onAddNewPage = onAddNewPage,
                        onSetPageSize = {
                            onClearPageSizeSelection()
                            showPageSizePicker = true
                        },
                        onSetBackgroundColor = {
                            onClearPageBgColorSelection()
                            showPageBgColorPicker = true
                        },
                        isPageLandscape = isPageLandscape,
                        pageAspectRatio = pageAspectRatio,
                        onPageOrientationChange = onPageOrientationChange,
                        onSetPageMargin = { pageMarginModeActive = true },
                        onDeletePage = onDeletePage,
                        onSetDeletePage = {
                            onClearPageDeleteSelection()
                            showPageDeletePicker = true
                        },
                        pageBackgroundColor = pageBackgroundColor,
                        onPageBackgroundColorChange = onPageBackgroundColorChange,
                        onPickBackgroundImage = onPickBackgroundImage,
                        onClearBackgroundImage = onClearBackgroundImage,
                        hasBackgroundImage = hasBackgroundImage,
                        pageNumberPosition = pageNumberPosition,
                        onPageNumberPositionChange = onPageNumberPositionChange,
                        pageNumberStyle = pageNumberStyle,
                        onPageNumberStyleChange = onPageNumberStyleChange
                    )
                    ImageToolsMenu(
                        onImportImagesClick = onImportImagesClick,
                        onResizeImagesClick = { resizeModeActive = true },
                        onAdjustSpacingClick = { spacingModeActive = true },
                        onAdjustImageShapeClick = { shapeModeActive = true },
                        onAdjustCornersClick = { cornersModeActive = true }
                    )
                    TextToolsMenu(
                        onEnterTextClick = onAddTextClick,
                        onFontClick = onFontClick,
                        onTextColorClick = onTextColorClick,
                        onTextBgColorClick = onTextBgColorClick,
                        onTextShadowClick = onTextShadowClick,
                        onTextSizeClick = {
                            if (hasSelectedText) textSizeModeActive = true
                            else onTextSizeClick()
                        },
                        onDeleteTextClick = onDeleteTextClick,
                        hasSelectedText = hasSelectedText
                    )
                }
            }
        }
        HorizontalDivider()
    }

    if (showPageSizePicker) {
        PageSizePickerDialog(
            pageCount = pageCountForSize,
            selectedPages = pageSizeSelected,
            onTogglePage = onTogglePageSizeSelection,
            onToggleSelectAll = onToggleSelectAllPagesForSize,
            onOk = {
                showPageSizePicker = false
                if (pageSizeSelected.isNotEmpty()) {
                    pageSizeModeActive = true
                }
            },
            onDismiss = { showPageSizePicker = false },
            instructionText = stringResource(R.string.page_size_picker_instruction),
            confirmText = stringResource(R.string.page_size_ok)
        )
    }

    if (showPageBgColorPicker) {
        PageSizePickerDialog(
            pageCount = pageCountForBgColor,
            selectedPages = pageBgColorSelected,
            onTogglePage = onTogglePageBgColorSelection,
            onToggleSelectAll = onToggleSelectAllPagesForBgColor,
            onOk = {
                showPageBgColorPicker = false
                if (pageBgColorSelected.isNotEmpty()) {
                    pageBgColorModeActive = true
                }
            },
            onDismiss = { showPageBgColorPicker = false },
            instructionText = stringResource(R.string.page_bg_picker_instruction),
            confirmText = stringResource(R.string.page_size_ok)
        )
    }

    if (showPageDeletePicker) {
        PageSizePickerDialog(
            pageCount = pageCountForDelete,
            selectedPages = pageDeleteSelected,
            onTogglePage = onTogglePageDeleteSelection,
            onToggleSelectAll = onToggleSelectAllPagesForDelete,
            onOk = {
                showPageDeletePicker = false
                if (pageDeleteSelected.isNotEmpty()) {
                    onDeleteSelectedPages()
                }
            },
            onDismiss = { showPageDeletePicker = false },
            instructionText = stringResource(R.string.page_delete_picker_instruction),
            confirmText = stringResource(R.string.page_delete_button)
        )
    }

}


@Composable
private fun OrientationOptionRow(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: icon + label (with ratio in brackets)
        Text(
            text = icon + "  " + label,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.weight(1f))
        // Right: tick with a little space from the edge
        Text(
            text = if (selected) "✓" else "",
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun ToolMenuItem(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = Color.Black,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )

}

@Composable
private fun ImageToolsMenu(
    onImportImagesClick: () -> Unit,
    onResizeImagesClick: () -> Unit,
    onAdjustSpacingClick: () -> Unit,
    onAdjustImageShapeClick: () -> Unit,
    onAdjustCornersClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = stringResource(R.string.image_tools_menu_entry) + " \u25BE",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            modifier = Modifier
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp)
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            ToolMenuItem(stringResource(R.string.import_images)) {
                menuExpanded = false
                onImportImagesClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.resize_images_tool)) {
                menuExpanded = false
                onResizeImagesClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.image_spacing_tool)) {
                menuExpanded = false
                onAdjustSpacingClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.image_shape_tool)) {
                menuExpanded = false
                onAdjustImageShapeClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.round_corners_tool)) {
                menuExpanded = false
                onAdjustCornersClick()
            }
        }
    }
}

@Composable
private fun PageToolsMenu(
    onAddNewPage: () -> Unit,
    onSetPageSize: () -> Unit,
    onSetBackgroundColor: () -> Unit,
    isPageLandscape: Boolean,
    pageAspectRatio: Float,
    onPageOrientationChange: (EditorViewModel.PageOrientation) -> Unit,
    onSetPageMargin: () -> Unit,
    onDeletePage: () -> Unit,
    onSetDeletePage: () -> Unit,
    pageBackgroundColor: Long,
    onPageBackgroundColorChange: (Long) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    hasBackgroundImage: Boolean,
    pageCountForBgColor: Int = 1,
    pageBgColorSelected: Set<Int> = emptySet(),
    onTogglePageBgColorSelection: (Int) -> Unit = {},
    onToggleSelectAllPagesForBgColor: () -> Unit = {},
    onClearPageBgColorSelection: () -> Unit = {},
    pageNumberPosition: EditorViewModel.PageNumberPosition,
    onPageNumberPositionChange: (EditorViewModel.PageNumberPosition) -> Unit,
    pageNumberStyle: EditorViewModel.PageNumberStyle,
    onPageNumberStyleChange: (EditorViewModel.PageNumberStyle) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var orientationSub by remember { mutableStateOf(false) }
    var backgroundSub by remember { mutableStateOf(false) }

    // Default / primary palette (3rd item = light gray 0xFFE0E0E0 is default selected in ViewModel)
    val primaryColors = listOf(
        0xFFFFFFFFL, // 1 white
        0xFFF5F5F5L, // 2 light gray
        0xFFE0E0E0L, // 3 medium light gray (DEFAULT)
        0xFFFFF8E1L, // 4 warm
        0xFFE3F2FDL, // 5 light blue
        0xFFE8F5E9L, // 6 light green
        0xFFFFEBEEL, // 7 light pink
        0xFF000000L  // 8 black
    )
    val extraColors = listOf(
        0xFFFFCDD2L, 0xFFF8BBD0L, 0xFFE1BEE7L, 0xFFD1C4E9L,
        0xFFC5CAE9L, 0xFFBBDEFBL, 0xFFB3E5FCL, 0xFFB2EBF2L,
        0xFFB2DFDBL, 0xFFC8E6C9L, 0xFFDCEDC8L, 0xFFF0F4C3L,
        0xFFFFF9C4L, 0xFFFFECB3L, 0xFFFFE0B2L, 0xFFFFCCBCL,
        0xFFD7CCC8L, 0xFFCFD8DCL, 0xFF90A4AEL, 0xFF607D8BL
    )

    Box {
        Text(
            text = stringResource(R.string.page_tools_menu_entry) + " \u25BE",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            modifier = Modifier
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp)
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = {
                menuExpanded = false
                orientationSub = false
                backgroundSub = false
            }
        ) {
            // 1. Add New Page
            ToolMenuItem(stringResource(R.string.page_tool_add_new_page)) {
                // Keep Page Settings menu open; closes only on outside touch
                onAddNewPage()
            }
            HorizontalDivider(color = Color.LightGray)

            // 2. Delete Page
            ToolMenuItem(stringResource(R.string.page_tool_delete_page)) {
                menuExpanded = false
                onSetDeletePage()
            }
            HorizontalDivider(color = Color.LightGray)

            // 3. Background Color
            ToolMenuItem(stringResource(R.string.page_tool_background_color)) {
                menuExpanded = false
                backgroundSub = false
                orientationSub = false
                onSetBackgroundColor()
            }
            HorizontalDivider(color = Color.LightGray)

            // 4. Set Page Size
            ToolMenuItem(stringResource(R.string.page_tool_set_page_size)) {
                menuExpanded = false
                onSetPageSize()
            }
            HorizontalDivider(color = Color.LightGray)

            // 5. Page Orientation
            ToolMenuItem(stringResource(R.string.page_tool_orientation) + " ›") {
                orientationSub = !orientationSub
                if (orientationSub) {
                    backgroundSub = false
                }
            }
            if (orientationSub) {
                val isSquare = kotlin.math.abs(pageAspectRatio - 1f) < 0.05f
                val isLand = pageAspectRatio > 1.05f
                val isPort = pageAspectRatio < 0.95f

                OrientationOptionRow(
                    icon = "▯",
                    label = stringResource(R.string.page_orientation_portrait) + " (9:16)",
                    selected = isPort,
                    onClick = {
                        onPageOrientationChange(EditorViewModel.PageOrientation.PORTRAIT)
                    }
                )
                OrientationOptionRow(
                    icon = "▭",
                    label = stringResource(R.string.page_orientation_landscape) + " (16:9)",
                    selected = isLand,
                    onClick = {
                        onPageOrientationChange(EditorViewModel.PageOrientation.LANDSCAPE)
                    }
                )
                OrientationOptionRow(
                    icon = "□",
                    label = stringResource(R.string.page_orientation_square) + " (1:1)",
                    selected = isSquare,
                    onClick = {
                        onPageOrientationChange(EditorViewModel.PageOrientation.SQUARE)
                    }
                )
            }

            HorizontalDivider(color = Color.LightGray)

            // 6. Page Margin
            ToolMenuItem(stringResource(R.string.page_tool_margin)) {
                menuExpanded = false
                onSetPageMargin()
            }

        }
    }
}

@Composable
private fun TextToolsMenu(
    onEnterTextClick: () -> Unit,
    onFontClick: () -> Unit,
    onTextSizeClick: () -> Unit,
    onTextColorClick: () -> Unit,
    onTextBgColorClick: () -> Unit,
    onTextShadowClick: () -> Unit,
    onDeleteTextClick: () -> Unit,
    hasSelectedText: Boolean
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = stringResource(R.string.text_tools_menu_entry) + " \u25BE",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            modifier = Modifier
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp)
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            ToolMenuItem(stringResource(R.string.text_tool_enter_text)) {
                menuExpanded = false
                onEnterTextClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.text_tool_font)) {
                menuExpanded = false
                onFontClick()
            }
            HorizontalDivider(color = Color.LightGray)
            
            ToolMenuItem(
                label = stringResource(R.string.text_tool_text_color),
                enabled = hasSelectedText
            ) {
                menuExpanded = false
                onTextColorClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(
                label = stringResource(R.string.text_tool_bg_color),
                enabled = hasSelectedText
            ) {
                menuExpanded = false
                onTextBgColorClick()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(
                label = stringResource(R.string.text_tool_shadow),
                enabled = hasSelectedText
            ) {
                menuExpanded = false
                onTextShadowClick()
            }
            HorizontalDivider(color = Color.LightGray)
ToolMenuItem(
                label = stringResource(R.string.text_tool_text_size),
                enabled = hasSelectedText
            ) {
                menuExpanded = false
                onTextSizeClick()
            }
            HorizontalDivider(color = Color.LightGray)
            Text(
                text = stringResource(R.string.text_tool_delete),
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasSelectedText) {
                        menuExpanded = false
                        onDeleteTextClick()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SliderWithValueLabel(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    labelFormatter: (Float) -> String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val trackInset = 14.dp
        val usableWidth = (maxWidth - trackInset * 2)
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
            .coerceIn(0f, 1f)
        val bubbleCenterX = trackInset + usableWidth * fraction

        Text(
            text = labelFormatter(value),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .offset(x = bubbleCenterX - 14.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SliderToolHeader(title: String, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black
        )
        Text(
            text = stringResource(R.string.resize_images_done),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onDone)
        )
    }
}

@Composable
private fun ImagesPerRowSlider(
    imagesPerRow: Int,
    onImagesPerRowChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.resize_images_tool), onDone = onDone)
        SliderWithValueLabel(
            value = imagesPerRow.toFloat(),
            onValueChange = { onImagesPerRowChange(it.roundToInt().coerceIn(1, 20)) },
            valueRange = 1f..20f,
            steps = 18,
            labelFormatter = { "${it.roundToInt()}" }
        )
    }
}

@Composable
private fun ImageSpacingSlider(
    spacingDp: Int,
    onSpacingChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.image_spacing_tool), onDone = onDone)
        SliderWithValueLabel(
            value = spacingDp.toFloat(),
            onValueChange = { onSpacingChange(it.roundToInt().coerceIn(0, 20)) },
            valueRange = 0f..20f,
            steps = 0,
            labelFormatter = { "${it.roundToInt()} dp" }
        )
    }
}

private const val SHAPE_MIN_RATIO = 0.3f
private const val SHAPE_MAX_RATIO = 2.0f

private fun shapeRatioToPercent(ratio: Float): Int {
    val t = ((ratio - SHAPE_MIN_RATIO) / (SHAPE_MAX_RATIO - SHAPE_MIN_RATIO)).coerceIn(0f, 1f)
    return (t * 100f).roundToInt()
}

private fun shapePercentToRatio(percent: Int): Float {
    val t = percent.coerceIn(0, 100) / 100f
    return SHAPE_MIN_RATIO + t * (SHAPE_MAX_RATIO - SHAPE_MIN_RATIO)
}

@Composable
private fun ImageShapeSlider(
    aspectRatio: Float,
    onAspectRatioChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    var percentText by remember(aspectRatio) {
        mutableStateOf(shapeRatioToPercent(aspectRatio).toString())
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.image_shape_tool), onDone = onDone)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SliderWithValueLabel(
                value = aspectRatio,
                onValueChange = onAspectRatioChange,
                valueRange = SHAPE_MIN_RATIO..SHAPE_MAX_RATIO,
                steps = 0,
                labelFormatter = { "${shapeRatioToPercent(it)}%" },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = percentText,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(3)
                    percentText = filtered
                    val percent = filtered.toIntOrNull()
                    if (percent != null && percent in 0..100) {
                        onAspectRatioChange(shapePercentToRatio(percent))
                    }
                },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RoundCornersSlider(
    percent: Int,
    onPercentChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    var percentText by remember(percent) { mutableStateOf(percent.toString()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.round_corners_tool), onDone = onDone)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SliderWithValueLabel(
                value = percent.toFloat(),
                onValueChange = { onPercentChange(it.roundToInt()) },
                valueRange = 0f..100f,
                steps = 0,
                labelFormatter = { "${it.roundToInt()}%" },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = percentText,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(3)
                    percentText = filtered
                    val value = filtered.toIntOrNull()
                    if (value != null && value in 0..100) {
                        onPercentChange(value)
                    }
                },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun PageSizePickerDialog(
    pageCount: Int,
    selectedPages: Set<Int>,
    onTogglePage: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onOk: () -> Unit,
    onDismiss: () -> Unit,
    instructionText: String = "",
    confirmText: String = ""
) {
    val count = pageCount.coerceAtLeast(1)
    val allSelected = selectedPages.size >= count &&
        (0 until count).all { it in selectedPages }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            // Instruction: large + bold (all 3 tools)
            Text(
                text = instructionText.ifEmpty { stringResource(R.string.page_size_picker_instruction) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Select All only (top left) — outlined
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onToggleSelectAll) {
                    Text(
                        text = stringResource(R.string.page_size_select_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Page list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (i in 0 until count) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePage(i) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = i in selectedPages,
                            onCheckedChange = { onTogglePage(i) },
                            modifier = Modifier.padding(0.dp)
                        )
                        Text(
                            text = stringResource(R.string.page_size_page_item, i + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // OK / Delete: bottom-right, outlined (like Select All), blue text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOk,
                    enabled = selectedPages.isNotEmpty()
                ) {
                    Text(
                        text = confirmText.ifEmpty { stringResource(R.string.page_size_ok) },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1976D2)
                    )
                }
            }
        }
    }
}


@Composable
private fun PageBackgroundColorPanel(
    pageBackgroundColor: Long,
    hasBackgroundImage: Boolean,
    onColorSelected: (Long) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    onDone: () -> Unit
) {
    val primaryColors = listOf(
        0xFFFFFFFFL, 0xFFF5F5F5L, 0xFFE0E0E0L, 0xFFFFF8E1L,
        0xFFE3F2FDL, 0xFFE8F5E9L, 0xFFFFEBEEL, 0xFF000000L
    )
    val extraColors = listOf(
        0xFFFFCDD2L, 0xFFF8BBD0L, 0xFFE1BEE7L, 0xFFD1C4E9L,
        0xFFC5CAE9L, 0xFFBBDEFBL, 0xFFB3E5FCL, 0xFFB2EBF2L,
        0xFFB2DFDBL, 0xFFC8E6C9L, 0xFFDCEDC8L, 0xFFF0F4C3L,
        0xFFFFF9C4L, 0xFFFFECB3L, 0xFFFFE0B2L, 0xFFFFCCBCL,
        0xFFD7CCC8L, 0xFFCFD8DCL, 0xFF90A4AEL, 0xFF607D8BL
    )
    val allColors = primaryColors + extraColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SliderToolHeader(
            title = stringResource(R.string.page_tool_background_color),
            onDone = onDone
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickBackgroundImage) {
            Text(
                text = stringResource(R.string.page_bg_import_gallery),
                color = Color.Black
            )
        }
        if (hasBackgroundImage) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onClearBackgroundImage) {
                Text(stringResource(R.string.page_bg_clear_image))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            allColors.chunked(8).forEach { row ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { c ->
                        val selected = pageBackgroundColor == c && !hasBackgroundImage
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(c) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSizeSlider(
    aspectRatio: Float,
    onAspectRatioChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    fun ratioToPercent(ratio: Float): Int {
        val t = ((ratio - 0.4f) / (2.5f - 0.4f)).coerceIn(0f, 1f)
        return (t * 100f).roundToInt()
    }
    fun percentToRatio(percent: Int): Float {
        val t = percent.coerceIn(0, 100) / 100f
        return 0.4f + t * (2.5f - 0.4f)
    }

    var percentText by remember(aspectRatio) {
        mutableStateOf(ratioToPercent(aspectRatio).toString())
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(
            title = stringResource(R.string.page_tool_set_page_size),
            onDone = onDone
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SliderWithValueLabel(
                value = aspectRatio,
                onValueChange = { newRatio ->
                    onAspectRatioChange(newRatio)
                    percentText = ratioToPercent(newRatio).toString()
                },
                valueRange = 0.4f..2.5f,
                steps = 0,
                labelFormatter = { ratio ->
                    val w = 100
                    val h = (100f / ratio).roundToInt().coerceAtLeast(1)
                    if (ratio >= 1f) {
                        "Landscape " + w + ":" + h
                    } else {
                        "Portrait " + h + ":" + w
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = percentText,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(3)
                    percentText = filtered
                    val percent = filtered.toIntOrNull()
                    if (percent != null && percent in 0..100) {
                        onAspectRatioChange(percentToRatio(percent))
                    }
                },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PageMarginSlider(
    marginDp: Int,
    onMarginChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.page_tool_margin), onDone = onDone)
        SliderWithValueLabel(
            value = marginDp.toFloat(),
            onValueChange = { onMarginChange(it.roundToInt().coerceIn(0, 48)) },
            valueRange = 0f..48f,
            steps = 0,
            labelFormatter = { "${it.roundToInt()} dp" }
        )
    }
}

@Composable
private fun TextSizeSlider(
    sizeSp: Float,
    onSizeChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    var sizeText by remember(sizeSp) { mutableStateOf(sizeSp.roundToInt().toString()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SliderToolHeader(title = stringResource(R.string.text_tool_text_size), onDone = onDone)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SliderWithValueLabel(
                value = sizeSp,
                onValueChange = {
                    onSizeChange(it)
                    sizeText = it.roundToInt().toString()
                },
                valueRange = 8f..72f,
                steps = 0,
                labelFormatter = { "${it.roundToInt()} sp" },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = sizeText,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(3)
                    sizeText = filtered
                    val v = filtered.toIntOrNull()
                    if (v != null && v in 8..72) {
                        onSizeChange(v.toFloat())
                    }
                },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

