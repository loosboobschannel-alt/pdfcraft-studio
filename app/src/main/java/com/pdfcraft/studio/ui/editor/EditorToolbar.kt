package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.image.ImageSizeOption
import kotlin.math.roundToInt

@Composable
fun EditorToolbar(
    onImportImagesClick: () -> Unit,
    selectedSizeOption: ImageSizeOption,
    onSizeOptionSelected: (ImageSizeOption) -> Unit,
    imagesPerRow: Int,
    onImagesPerRowSelected: (Int) -> Unit,
    imageSpacingDp: Int,
    onImageSpacingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var resizeModeActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        if (resizeModeActive) {
            ResizeImagesSlider(
                imagesPerRow = imagesPerRow,
                onImagesPerRowChange = onImagesPerRowSelected,
                onDone = { resizeModeActive = false }
            )
        } else {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                AllToolsMenu(
                    onImportImagesClick = onImportImagesClick,
                    selectedSizeOption = selectedSizeOption,
                    onSizeOptionSelected = onSizeOptionSelected,
                    imagesPerRow = imagesPerRow,
                    onImagesPerRowSelected = onImagesPerRowSelected,
                    imageSpacingDp = imageSpacingDp,
                    onImageSpacingSelected = onImageSpacingSelected,
                    onResizeImagesClick = { resizeModeActive = true }
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ResizeImagesSlider(
    imagesPerRow: Int,
    onImagesPerRowChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.resize_images_tool),
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
        Slider(
            value = imagesPerRow.toFloat(),
            onValueChange = { onImagesPerRowChange(it.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun AllToolsMenu(
    onImportImagesClick: () -> Unit,
    selectedSizeOption: ImageSizeOption,
    onSizeOptionSelected: (ImageSizeOption) -> Unit,
    imagesPerRow: Int,
    onImagesPerRowSelected: (Int) -> Unit,
    imageSpacingDp: Int,
    onImageSpacingSelected: (Int) -> Unit,
    onResizeImagesClick: () -> Unit
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
                text = { Text(stringResource(R.string.import_images), color = Color.Black) },
                onClick = {
                    menuExpanded = false
                    onImportImagesClick()
                }
            )

            ImageSizeSubmenuEntry(
                selected = selectedSizeOption,
                onOptionSelected = { option ->
                    onSizeOptionSelected(option)
                    menuExpanded = false
                }
            )

            ImagesPerRowMenuEntry(
                currentValue = imagesPerRow,
                onValueSelected = { value ->
                    onImagesPerRowSelected(value)
                    menuExpanded = false
                }
            )

            ImageSpacingMenuEntry(
                currentValue = imageSpacingDp,
                onValueSelected = { value ->
                    onImageSpacingSelected(value)
                    menuExpanded = false
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.resize_images_tool), color = Color.Black) },
                onClick = {
                    menuExpanded = false
                    onResizeImagesClick()
                }
            )
        }
    }
}

@Composable
private fun ImageSizeSubmenuEntry(
    selected: ImageSizeOption,
    onOptionSelected: (ImageSizeOption) -> Unit
) {
    var submenuExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    Box {
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.image_size_menu_entry, selected.label),
                        color = Color.Black,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "\u203A", color = Color.Black)
                }
            },
            onClick = { submenuExpanded = true }
        )

        DropdownMenu(
            expanded = submenuExpanded,
            onDismissRequest = { submenuExpanded = false },
            offset = DpOffset(x = 200.dp, y = (-48).dp)
        ) {
            DropdownMenuItem(
                text = {
                    SizeMenuLabel(
                        stringResource(R.string.image_size_default),
                        isSelected(selected, ImageSizeOption.Default)
                    )
                },
                onClick = {
                    onOptionSelected(ImageSizeOption.Default)
                    submenuExpanded = false
                }
            )
            ImageSizeOption.presetsKb.forEach { kb ->
                val option = ImageSizeOption.Preset(kb)
                DropdownMenuItem(
                    text = {
                        SizeMenuLabel(
                            stringResource(R.string.image_size_kb_value, kb),
                            isSelected(selected, option)
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        submenuExpanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    SizeMenuLabel(
                        stringResource(R.string.image_size_custom),
                        selected is ImageSizeOption.Custom
                    )
                },
                onClick = {
                    submenuExpanded = false
                    showCustomDialog = true
                }
            )
        }
    }

    if (showCustomDialog) {
        CustomSizeDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { kb ->
                onOptionSelected(ImageSizeOption.Custom(kb))
                showCustomDialog = false
            }
        )
    }
}

private fun isSelected(current: ImageSizeOption, candidate: ImageSizeOption): Boolean =
    when (candidate) {
        is ImageSizeOption.Default -> current is ImageSizeOption.Default
        is ImageSizeOption.Preset -> current is ImageSizeOption.Preset && current.kb == candidate.kb
        is ImageSizeOption.Custom -> current is ImageSizeOption.Custom
    }

@Composable
private fun SizeMenuLabel(text: String, isSelected: Boolean) {
    Text(
        text = text,
        color = Color.Black,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun CustomSizeDialog(
    onDismiss: () -> Unit,
    onConfirm: (kb: Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val kbValue = input.toIntOrNull()
    val isValid = kbValue != null && kbValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_size_custom_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.image_size_custom_dialog_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { kbValue?.let(onConfirm) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun ImageSpacingMenuEntry(
    currentValue: Int,
    onValueSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(R.string.image_spacing_menu_entry, currentValue),
                color = Color.Black
            )
        },
        onClick = { showDialog = true }
    )

    if (showDialog) {
        ImageSpacingDialog(
            currentValue = currentValue,
            onDismiss = { showDialog = false },
            onConfirm = { value ->
                onValueSelected(value)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ImageSpacingDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentValue.toString()) }
    val value = input.toIntOrNull()
    val isValid = value != null && value in 0..40

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_spacing_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.image_spacing_dialog_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun ImagesPerRowMenuEntry(
    currentValue: Int,
    onValueSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(R.string.images_per_row_menu_entry, currentValue),
                color = Color.Black
            )
        },
        onClick = { showDialog = true }
    )

    if (showDialog) {
        ImagesPerRowDialog(
            currentValue = currentValue,
            onDismiss = { showDialog = false },
            onConfirm = { value ->
                onValueSelected(value)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ImagesPerRowDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentValue.toString()) }
    val value = input.toIntOrNull()
    val isValid = value != null && value in 1..20

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.images_per_row_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.images_per_row_dialog_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
