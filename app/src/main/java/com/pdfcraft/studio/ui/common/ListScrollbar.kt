package com.pdfcraft.studio.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalListScrollbar(
    state: LazyListState,
    color: Color = Color(0xFF1976D2),
    width: Dp = 3.dp
): Modifier = this.drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total <= 0 || visible.isEmpty()) return@drawWithContent
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return@drawWithContent
    val avg = visible.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val content = avg * total
    if (content <= viewport) return@drawWithContent
    val thumb = (viewport / content * viewport).coerceAtLeast(24.dp.toPx())
    val first = visible.first()
    val start = first.index.toFloat() + (first.offset / avg)
    val y = (start / total.toFloat() * viewport).coerceIn(0f, viewport - thumb)
    val w = width.toPx()
    drawRoundRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(size.width - w - 3.dp.toPx(), y),
        size = Size(w, thumb),
        cornerRadius = CornerRadius(w / 2f, w / 2f)
    )
}
