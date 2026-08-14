package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pdfcraft.studio.R
import com.pdfcraft.studio.core.image.ImageSizeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportImagesDialog(
    selectedOption: ImageSizeOption,
    onOptionSelected: (ImageSizeOption) -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.import_images)) },
                    navigationIcon = {
                        Text(
                            text = "\u2715",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Black,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable(onClick = onDismiss)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color.White
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.image_import_instructions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedOption is ImageSizeOption.Default,
                        onCheckedChange = { onOptionSelected(ImageSizeOption.Default) }
                    )
                    Text(
                        text = stringResource(R.string.original_image_size_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val presetRows = ImageSizeOption.presetsKb.chunked(3)
                presetRows.forEachIndexed { index, rowKb ->
                    val isLastRow = index == presetRows.lastIndex
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKb.forEach { kb ->
                            PresetSizeCheckbox(
                                kb = kb,
                                checked = selectedOption is ImageSizeOption.Preset && selectedOption.kb == kb,
                                onChecked = { onOptionSelected(ImageSizeOption.Preset(kb)) }
                            )
                        }
                        if (isLastRow) {
                            CustomSizeCheckbox(
                                selectedOption = selectedOption,
                                onOptionSelected = onOptionSelected
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = onImportClick) {
                        Text(stringResource(R.string.import_images))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PresetSizeCheckbox(kb: Int, checked: Boolean, onChecked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onChecked() })
        Text(
            text = stringResource(R.string.image_size_kb_value, kb),
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CustomSizeCheckbox(
    selectedOption: ImageSizeOption,
    onOptionSelected: (ImageSizeOption) -> Unit
) {
    var text by remember(selectedOption) {
        mutableStateOf((selectedOption as? ImageSizeOption.Custom)?.kb?.toString() ?: "")
    }
    val checked = selectedOption is ImageSizeOption.Custom

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                val kb = text.toIntOrNull()
                if (kb != null && kb > 0) {
                    onOptionSelected(ImageSizeOption.Custom(kb))
                }
            }
        )
        Text(
            text = stringResource(R.string.image_size_custom),
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val filtered = input.filter(Char::isDigit).take(5)
                text = filtered
                val kb = filtered.toIntOrNull()
                if (kb != null && kb > 0) {
                    onOptionSelected(ImageSizeOption.Custom(kb))
                }
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
