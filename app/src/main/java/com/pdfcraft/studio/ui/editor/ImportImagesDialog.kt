package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

private val AccentBlue = Color(0xFF1976D2)
private val CardBorder = Color(0xFFBDBDBD)
private val SelectedBg = Color(0xFFE3F2FD)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                    title = {
                        Text(
                            text = stringResource(R.string.import_images),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    },
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.image_import_instructions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.image_import_instructions_secondary),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Original — slightly more prominent
                SizeSelectCard(
                    label = stringResource(R.string.original_image_size_label),
                    selected = selectedOption is ImageSizeOption.Default,
                    prominent = true,
                    onClick = { onOptionSelected(ImageSizeOption.Default) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Preset KB pills in a compact flow grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImageSizeOption.presetsKb.forEach { kb ->
                        val selected = selectedOption is ImageSizeOption.Preset &&
                            selectedOption.kb == kb
                        SizeSelectCard(
                            label = stringResource(R.string.image_size_kb_value, kb),
                            selected = selected,
                            onClick = { onOptionSelected(ImageSizeOption.Preset(kb)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom card + compact input when selected
                CustomSizeCard(
                    selectedOption = selectedOption,
                    onOptionSelected = onOptionSelected
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onImportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.import_images),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SizeSelectCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    val bg = when {
        selected -> SelectedBg
        prominent -> Color(0xFFF5F5F5)
        else -> Color.White
    }
    val borderColor = if (selected) AccentBlue else CardBorder
    val borderWidth = if (selected) 2.dp else 1.dp

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (prominent) 14.dp else 12.dp,
                vertical = if (prominent) 12.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (selected) {
            Text(
                text = "\u2713 ",
                color = AccentBlue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = if (selected) AccentBlue else Color.Black,
            style = if (prominent) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || prominent) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomSizeCard(
    selectedOption: ImageSizeOption,
    onOptionSelected: (ImageSizeOption) -> Unit
) {
    var text by remember(selectedOption) {
        mutableStateOf((selectedOption as? ImageSizeOption.Custom)?.kb?.toString() ?: "")
    }
    val selected = selectedOption is ImageSizeOption.Custom

    Column(modifier = Modifier.fillMaxWidth()) {
        SizeSelectCard(
            label = stringResource(R.string.image_size_custom),
            selected = selected,
            onClick = {
                val kb = text.toIntOrNull()
                if (kb != null && kb > 0) {
                    onOptionSelected(ImageSizeOption.Custom(kb))
                } else {
                    // Select custom mode; user can type next
                    onOptionSelected(ImageSizeOption.Custom(text.toIntOrNull() ?: 100))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (selected) {
            Spacer(modifier = Modifier.height(8.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.image_size_custom_dialog_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = CardBorder,
                    focusedLabelColor = AccentBlue
                )
            )
        }
    }
}
