package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    isPageLandscape: Boolean,
    onPageOrientationChange: (Boolean) -> Unit,
    pageMarginDp: Int,
    onPageMarginChange: (Int) -> Unit,
    pageBackgroundColor: Long,
    onPageBackgroundColorChange: (Long) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    hasBackgroundImage: Boolean,
    pageNumberPosition: EditorViewModel.PageNumberPosition,
    onPageNumberPositionChange: (EditorViewModel.PageNumberPosition) -> Unit,
    pageNumberStyle: EditorViewModel.PageNumberStyle,
    onPageNumberStyleChange: (EditorViewModel.PageNumberStyle) -> Unit,
    onAddNewPage: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var resizeModeActive by remember { mutableStateOf(false) }
    var spacingModeActive by remember { mutableStateOf(false) }
    var shapeModeActive by remember { mutableStateOf(false) }
    var cornersModeActive by remember { mutableStateOf(false) }
    var pageSizeModeActive by remember { mutableStateOf(false) }
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
            pageSizeModeActive -> PageSizeSlider(
                aspectRatio = pageAspectRatio,
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
                        onSetPageSize = { pageSizeModeActive = true },
                        isPageLandscape = isPageLandscape,
                        onPageOrientationChange = onPageOrientationChange,
                        onSetPageMargin = { pageMarginModeActive = true },
                        onDeletePage = onDeletePage,
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
}

@Composable
private fun ToolMenuItem(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (enabled) Color.Black else Color.Gray,
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
private fun PageToolsMenu(
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
private fun ImageToolsMenu(
    onAddNewPage: () -> Unit,
    onSetPageSize: () -> Unit,
    isPageLandscape: Boolean,
    onPageOrientationChange: (Boolean) -> Unit,
    onSetPageMargin: () -> Unit,
    onDeletePage: () -> Unit,
    pageBackgroundColor: Long,
    onPageBackgroundColorChange: (Long) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    hasBackgroundImage: Boolean,
    pageNumberPosition: EditorViewModel.PageNumberPosition,
    onPageNumberPositionChange: (EditorViewModel.PageNumberPosition) -> Unit,
    pageNumberStyle: EditorViewModel.PageNumberStyle,
    onPageNumberStyleChange: (EditorViewModel.PageNumberStyle) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var orientationSub by remember { mutableStateOf(false) }
    var backgroundSub by remember { mutableStateOf(false) }
    var moreColors by remember { mutableStateOf(false) }

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
                moreColors = false
            }
        ) {
            ToolMenuItem(stringResource(R.string.page_tool_add_new_page)) {
                menuExpanded = false
                onAddNewPage()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.page_tool_set_page_size)) {
                menuExpanded = false
                onSetPageSize()
            }
            HorizontalDivider(color = Color.LightGray)

            // ---- Orientation (closes background sub) ----
            ToolMenuItem(stringResource(R.string.page_tool_orientation) + " ›") {
                orientationSub = !orientationSub
                if (orientationSub) {
                    backgroundSub = false
                    moreColors = false
                }
            }
            if (orientationSub) {
                ToolMenuItem(
                    label = (if (!isPageLandscape) "✓ " else "") + stringResource(R.string.page_orientation_portrait)
                ) {
                    onPageOrientationChange(false)
                    // keep menu open; only close this sub
                    orientationSub = false
                }
                ToolMenuItem(
                    label = (if (isPageLandscape) "✓ " else "") + stringResource(R.string.page_orientation_landscape)
                ) {
                    onPageOrientationChange(true)
                    orientationSub = false
                }
            }

            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.page_tool_margin)) {
                menuExpanded = false
                onSetPageMargin()
            }
            HorizontalDivider(color = Color.LightGray)
            ToolMenuItem(stringResource(R.string.page_tool_delete_page)) {
                menuExpanded = false
                onDeletePage()
            }
            HorizontalDivider(color = Color.LightGray)

            // ---- Background Color (closes orientation sub) ----
            ToolMenuItem(stringResource(R.string.page_tool_background_color) + " ›") {
                backgroundSub = !backgroundSub
                if (backgroundSub) {
                    orientationSub = false
                } else {
                    moreColors = false
                }
            }
            if (backgroundSub) {
                val shown = if (moreColors) primaryColors + extraColors else primaryColors
                // rows of 8 swatches
                shown.chunked(8).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                    .clickable {
                                        // Do NOT close Page Tools menu
                                        onPageBackgroundColorChange(c)
                                    }
                            )
                        }
                    }
                }
                // More colors toggle (text glyph, no Material Icon)
                ToolMenuItem(if (moreColors) "▴ Fewer Colors" else "▾ More Colors") {
                    moreColors = !moreColors
                }
                ToolMenuItem("Import From Gallery") {
                    // gallery pick can close menu
                    menuExpanded = false
                    backgroundSub = false
                    moreColors = false
                    onPickBackgroundImage()
                }
                if (hasBackgroundImage) {
                    ToolMenuItem(stringResource(R.string.page_bg_clear_image)) {
                        onClearBackgroundImage()
                    }
                }
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
private fun PageSizeSlider(
    aspectRatio: Float,
    onAspectRatioChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    // percent 0..100 maps to ratio 0.4..2.5 (same range as slider)
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
        SliderToolHeader(title = stringResource(R.string.page_tool_set_page_size), onDone = onDone)

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
                    val h = (100f / ratio).roundToInt()
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

