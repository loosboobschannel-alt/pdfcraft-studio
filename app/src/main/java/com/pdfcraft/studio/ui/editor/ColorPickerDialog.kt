package com.pdfcraft.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class NamedColor(val name: String, val hex: Long)

private data class ColorCategory(val title: String, val colors: List<NamedColor>)

private val COLOR_CATEGORIES = listOf(
    ColorCategory(
        "Basic Colors",
        listOf(
            NamedColor("Black", 0xFF000000),
            NamedColor("White", 0xFFFFFFFF),
            NamedColor("Red", 0xFFFF0000),
            NamedColor("Green", 0xFF008000),
            NamedColor("Blue", 0xFF0000FF),
            NamedColor("Yellow", 0xFFFFFF00),
            NamedColor("Orange", 0xFFFFA500),
            NamedColor("Purple", 0xFF800080),
            NamedColor("Pink", 0xFFFFC0CB),
            NamedColor("Brown", 0xFFA52A2A),
            NamedColor("Gray", 0xFF808080)
        )
    ),
    ColorCategory(
        "Reds",
        listOf(
            NamedColor("Crimson", 0xFFDC143C),
            NamedColor("Scarlet", 0xFFFF2400),
            NamedColor("Ruby", 0xFF9B111E),
            NamedColor("Cherry", 0xFFD2042D),
            NamedColor("Burgundy", 0xFF800020),
            NamedColor("Maroon", 0xFF800000),
            NamedColor("Rose", 0xFFFF007F),
            NamedColor("Coral", 0xFFFF7F50)
        )
    ),
    ColorCategory(
        "Oranges",
        listOf(
            NamedColor("Orange", 0xFFFF6600),
            NamedColor("Tangerine", 0xFFF28500),
            NamedColor("Amber", 0xFFFFBF00),
            NamedColor("Apricot", 0xFFFBCEB1),
            NamedColor("Peach", 0xFFFFE5B4),
            NamedColor("Pumpkin", 0xFFFF7518)
        )
    ),
    ColorCategory(
        "Yellows",
        listOf(
            NamedColor("Gold", 0xFFFFD700),
            NamedColor("Lemon", 0xFFFFF44F),
            NamedColor("Mustard", 0xFFFFDB58),
            NamedColor("Canary", 0xFFFFEF00),
            NamedColor("Khaki", 0xFFF0E68C),
            NamedColor("Cream", 0xFFFFFDD0)
        )
    ),
    ColorCategory(
        "Greens",
        listOf(
            NamedColor("Lime", 0xFF32CD32),
            NamedColor("Green", 0xFF008000),
            NamedColor("Emerald", 0xFF50C878),
            NamedColor("Mint", 0xFF98FF98),
            NamedColor("Olive", 0xFF808000),
            NamedColor("Forest", 0xFF228B22),
            NamedColor("Teal", 0xFF008080)
        )
    ),
    ColorCategory(
        "Blues",
        listOf(
            NamedColor("Sky Blue", 0xFF87CEEB),
            NamedColor("Cyan", 0xFF00FFFF),
            NamedColor("Azure", 0xFF007FFF),
            NamedColor("Blue", 0xFF0000FF),
            NamedColor("Royal Blue", 0xFF4169E1),
            NamedColor("Navy", 0xFF000080),
            NamedColor("Aqua", 0xFF00CED1)
        )
    ),
    ColorCategory(
        "Purples",
        listOf(
            NamedColor("Lavender", 0xFFE6E6FA),
            NamedColor("Violet", 0xFF8F00FF),
            NamedColor("Purple", 0xFF800080),
            NamedColor("Orchid", 0xFFDA70D6),
            NamedColor("Plum", 0xFF8E4585),
            NamedColor("Magenta", 0xFFFF00FF)
        )
    ),
    ColorCategory(
        "Pinks",
        listOf(
            NamedColor("Light Pink", 0xFFFFB6C1),
            NamedColor("Pink", 0xFFFFC0CB),
            NamedColor("Hot Pink", 0xFFFF69B4),
            NamedColor("Rose", 0xFFFF007F),
            NamedColor("Salmon", 0xFFFA8072),
            NamedColor("Fuchsia", 0xFFFF00FF)
        )
    ),
    ColorCategory(
        "Browns",
        listOf(
            NamedColor("Beige", 0xFFF5F5DC),
            NamedColor("Tan", 0xFFD2B48C),
            NamedColor("Caramel", 0xFFC68E17),
            NamedColor("Copper", 0xFFB87333),
            NamedColor("Chocolate", 0xFF7B3F00),
            NamedColor("Brown", 0xFFA52A2A)
        )
    ),
    ColorCategory(
        "Grays / Neutrals",
        listOf(
            NamedColor("Light Gray", 0xFFD3D3D3),
            NamedColor("Silver", 0xFFC0C0C0),
            NamedColor("Gray", 0xFF808080),
            NamedColor("Slate", 0xFF708090),
            NamedColor("Charcoal", 0xFF36454F),
            NamedColor("Dark Gray", 0xFF555555)
        )
    )
)

/** One-page color window — every category/color kept, compact so it does not scroll. */
@Composable
fun ColorPickerDialog(
    initialColor: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    previewAsBackground: Boolean = false
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "X",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa Sample",
                            color = if (previewAsBackground) Color.Black else Color(selectedColor),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = if (previewAsBackground) {
                                Modifier
                                    .background(Color(selectedColor), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            } else Modifier
                        )
                    }
                    Spacer(modifier = Modifier.width(28.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    COLOR_CATEGORIES.forEach { category ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                            )
                            category.colors.chunked(7).forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowColors.forEach { named ->
                                        ColorItem(
                                            named = named,
                                            isSelected = selectedColor == named.hex,
                                            onClick = { selectedColor = named.hex }
                                        )
                                    }
                                    repeat(7 - rowColors.size) {
                                        Spacer(modifier = Modifier.width(44.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onConfirm(selectedColor) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorItem(
    named: NamedColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(named.hex), CircleShape)
                .then(
                    if (isSelected) Modifier.border(2.dp, Color(0xFF1976D2), CircleShape)
                    else Modifier.border(1.dp, Color.LightGray, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if ((named.hex and 0xFFFFFF) > 0xAAAAAA) Color.Black else Color.White,
                            CircleShape
                        )
                )
            }
        }
        Text(
            text = named.name,
            fontSize = 7.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "#%06X".format(named.hex and 0xFFFFFF),
            fontSize = 6.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
