package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            ToolbarTextItem(
                text = stringResource(R.string.import_images),
                onClick = onImportImagesClick
            )

            Box(modifier = Modifier.width(24.dp))

            ImageSizeMenu(
                selected = selectedSizeOption,
                onOptionSelected = onSizeOptionSelected
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ToolbarTextItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ImageSizeMenu(
    selected: ImageSizeOption,
    onOptionSelected: (ImageSizeOption) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    Box {
        Text(
            text = stringResource(R.string.image_size_toolbar_label, selected.label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp)
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { SizeMenuLabel(stringResource(R.string.image_size_default), isSelected(selected, ImageSizeOption.Default)) },
                onClick = {
                    onOptionSelected(ImageSizeOption.Default)
                    menuExpanded = false
                }
            )
            ImageSizeOption.presetsKb.forEach { kb ->
                val option = ImageSizeOption.Preset(kb)
                DropdownMenuItem(
                    text = { SizeMenuLabel(stringResource(R.string.image_size_kb_value, kb), isSelected(selected, option)) },
                    onClick = {
                        onOptionSelected(option)
                        menuExpanded = false
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
                    menuExpanded = false
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
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
