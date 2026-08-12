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

@Composable
fun EditorToolbar(
    onImportImagesClick: () -> Unit,
    selectedSizeOption: ImageSizeOption,
    onSizeOptionSelected: (ImageSizeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AllToolsMenu(
                onImportImagesClick = onImportImagesClick,
                selectedSizeOption = selectedSizeOption,
                onSizeOptionSelected = onSizeOptionSelected
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun AllToolsMenu(
    onImportImagesClick: () -> Unit,
    selectedSizeOption: ImageSizeOption,
    onSizeOptionSelected: (ImageSizeOption) -> Unit
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
