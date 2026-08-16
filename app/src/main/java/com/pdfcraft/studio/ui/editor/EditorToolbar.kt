package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.DpOffset
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
    hasSelectedText: Boolean,
    modifier: Modifier = Modifier
) {
    var resizeModeActive by remember { mutableStateOf(false) }
    var spacingModeActive by remember { mutableStateOf(false) }
    var shapeModeActive by remember { mutableStateOf(false) }
    var cornersModeActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        if (resizeModeActive) {
            ImagesPerRowSlider(
                imagesPerRow = imagesPerRow,
                onImagesPerRowChange = onImagesPerRowSelected,
                onDone = { resizeModeActive = false }
            )
        } else if (spacingModeActive) {
            ImageSpacingSlider(
                spacingDp = imageSpacingDp,
                onSpacingChange = onImageSpacingSelected,
                onDone = { spacingModeActive = false }
            )
        } else if (shapeModeActive) {
            ImageShapeSlider(
                aspectRatio = imageCellAspectRatio,
                onAspectRatioChange = onImageCellAspectRatioSelected,
                onDone = { shapeModeActive = false }
            )
        } else if (cornersModeActive) {
            RoundCornersSlider(
                percent = imageCornerRadiusPercent,
                onPercentChange = onImageCornerRadiusSelected,
                onDone = { cornersModeActive = false }
            )
        } else {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                AllToolsMenu(
                    onImportImagesClick = onImportImagesClick,
                    onResizeImagesClick = { resizeModeActive = true },
                    onAdjustSpacingClick = { spacingModeActive = true },
                    onAdjustImageShapeClick = { shapeModeActive = true },
                    onAdjustCornersClick = { cornersModeActive = true },
                    onEnterTextClick = onAddTextClick,
                    onFontClick = onFontClick,
                    hasSelectedText = hasSelectedText
                )
            }
        }
        HorizontalDivider()
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
            onValueChange = { onImagesPerRowChange(it.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            labelFormatter = { it.roundToInt().toString() }
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
            onValueChange = { onSpacingChange(it.roundToInt()) },
            valueRange = 0f..20f,
            steps = 19,
            labelFormatter = { "${it.roundToInt()} dp" }
        )
    }
}

private const val SHAPE_MIN_RATIO = 0.4f
private const val SHAPE_MAX_RATIO = 2.5f

private fun shapeRatioToPercent(ratio: Float): Int =
    (((ratio - SHAPE_MIN_RATIO) / (SHAPE_MAX_RATIO - SHAPE_MIN_RATIO)) * 100f)
        .roundToInt().coerceIn(0, 100)

private fun shapePercentToRatio(percent: Int): Float =
    SHAPE_MIN_RATIO + (percent.coerceIn(0, 100) / 100f) * (SHAPE_MAX_RATIO - SHAPE_MIN_RATIO)

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
private fun AllToolsMenu(
    onImportImagesClick: () -> Unit,
    onResizeImagesClick: () -> Unit,
    onAdjustSpacingClick: () -> Unit,
    onAdjustImageShapeClick: () -> Unit,
    onAdjustCornersClick: () -> Unit,
    onEnterTextClick: () -> Unit,
    onFontClick: () -> Unit,
    hasSelectedText: Boolean
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = stringResource(R.string.all_tools) + " \u25BE",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            modifier = Modifier
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp)
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.import_images),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    menuExpanded = false
                    onImportImagesClick()
                }
            )

            TextToolsSubmenuEntry(
                hasSelectedText = hasSelectedText,
                onEnterTextClick = onEnterTextClick,
                onFontClick = onFontClick,
                onCloseParentMenu = { menuExpanded = false }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.image_spacing_tool),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    menuExpanded = false
                    onAdjustSpacingClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.resize_images_tool),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    menuExpanded = false
                    onResizeImagesClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.image_shape_tool),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    menuExpanded = false
                    onAdjustImageShapeClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.round_corners_tool),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    menuExpanded = false
                    onAdjustCornersClick()
                }
            )
        }
    }
}

@Composable
private fun TextToolsSubmenuEntry(
    hasSelectedText: Boolean,
    onEnterTextClick: () -> Unit,
    onFontClick: () -> Unit,
    onCloseParentMenu: () -> Unit
) {
    var submenuExpanded by remember { mutableStateOf(false) }

    Box {
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.text_tools_menu_entry),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "\u203A", color = Color.Black)
                }
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            onClick = { submenuExpanded = true }
        )

        DropdownMenu(
            expanded = submenuExpanded,
            onDismissRequest = { submenuExpanded = false },
            offset = DpOffset(x = 200.dp, y = (-48).dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.text_tool_enter_text),
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    submenuExpanded = false
                    onCloseParentMenu()
                    onEnterTextClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.text_tool_font),
                        color = if (hasSelectedText) Color.Black else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                enabled = hasSelectedText,
                onClick = {
                    submenuExpanded = false
                    onCloseParentMenu()
                    onFontClick()
                }
            )
        }
    }
}
