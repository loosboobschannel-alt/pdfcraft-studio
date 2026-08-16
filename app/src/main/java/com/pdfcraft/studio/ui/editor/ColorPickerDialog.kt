package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.R
import kotlin.math.roundToInt

/** HSV spectrum picker — no third-party library. */
@Composable
fun ColorPickerDialog(
    initialColor: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val init = Color(initialColor)
    var hue by remember { mutableStateOf(init.hue()) }
    var sat by remember { mutableStateOf(init.saturation()) }
    var value by remember { mutableStateOf(init.brightness()) }

    val current = Color.hsv(hue, sat, value)
    val argb = current.toArgb().toLong() and 0xFFFFFFFFL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title), color = Color.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(current, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                )
                Text(stringResource(R.string.color_picker_hue), color = Color.Black)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(stringResource(R.string.color_picker_sat), color = Color.Black)
                Slider(
                    value = sat,
                    onValueChange = { sat = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(stringResource(R.string.color_picker_value), color = Color.Black)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "#%06X".format(argb and 0xFFFFFF),
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge
                )
                // Quick presets row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0xFF000000L, 0xFFFFFFFFL, 0xFFFF0000L, 0xFF00FF00L,
                        0xFF0000FFL, 0xFFFFFF00L, 0xFFFF00FFL, 0xFF00FFFFL
                    ).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(c), CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape)
                                .padding(0.dp)
                                .then(
                                    Modifier.padding(0.dp)
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(argb) }) {
                Text(stringResource(R.string.dialog_ok), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = Color.Black)
            }
        }
    )
}

private fun Color.hue(): Float {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6 else 0))
        g -> ((b - r) / d + 2)
        else -> ((r - g) / d + 4)
    }
    return (h * 60f).coerceIn(0f, 360f)
}

private fun Color.saturation(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    if (max == 0f) return 0f
    return (max - min) / max
}

private fun Color.brightness(): Float = maxOf(red, green, blue)
