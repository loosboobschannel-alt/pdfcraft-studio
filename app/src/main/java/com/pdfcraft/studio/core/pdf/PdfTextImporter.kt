package com.pdfcraft.studio.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

data class ImportedPdfLine(
    val pageIndex: Int,
    val xFrac: Float,
    val yFrac: Float,
    val wFrac: Float,
    val hFrac: Float,
    val fontSizeSp: Float,
    val text: String,
    val colorArgb: Long
)

object PdfTextImporter {

    fun extract(context: Context, uri: Uri): List<ImportedPdfLine> {
        val out = ArrayList<ImportedPdfLine>()
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc ->
                    val stripper = LineStripper { line -> out.add(line) }
                    stripper.sortByPosition = true
                    stripper.getText(doc)
                }
            }
        } catch (_: Exception) {
        }
        return out.filter { it.text.isNotBlank() }
    }

    fun punchRect(bmp: Bitmap, xFrac: Float, yFrac: Float, wFrac: Float, hFrac: Float) {
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)
        val l = (xFrac * bw) - 1f
        val top = (yFrac * bh) - 1f
        val r = ((xFrac + wFrac) * bw) + 1f
        val b = ((yFrac + hFrac) * bh) + 1f
        val sx = l.toInt().coerceIn(0, bmp.width - 1)
        val sy = top.toInt().coerceIn(0, bmp.height - 1)
        paint.color = try { bmp.getPixel(sx, sy) } catch (_: Exception) { android.graphics.Color.WHITE }
        canvas.drawRect(l, top, r, b, paint)
    }

    fun punchTextFromPage(bmp: Bitmap, lines: List<ImportedPdfLine>, pageIndex: Int) {

        val pageLines = lines.filter { it.pageIndex == pageIndex }
        if (pageLines.isEmpty()) return
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)
        for (line in pageLines) {
            val l = (line.xFrac * bw) - 2f
            val t = (line.yFrac * bh) - 2f
            val r = ((line.xFrac + line.wFrac) * bw) + 2f
            val b = ((line.yFrac + line.hFrac) * bh) + 2f
            val sx = l.toInt().coerceIn(0, bmp.width - 1)
            val sy = t.toInt().coerceIn(0, bmp.height - 1)
            paint.color = try { bmp.getPixel(sx, sy) } catch (_: Exception) { android.graphics.Color.WHITE }
            canvas.drawRect(l, t, r, b, paint)
        }
    }

    private class LineStripper(
        private val sink: (ImportedPdfLine) -> Unit
    ) : PDFTextStripper() {
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val trimmed = text.replace("\u0000", "").trim()
            if (trimmed.isEmpty() || textPositions.isEmpty()) return
            val first = textPositions[0]
            val last = textPositions[textPositions.size - 1]
            val page = currentPage ?: return
            val box = page.mediaBox ?: return
            val pw = box.width.coerceAtLeast(1f)
            val ph = box.height.coerceAtLeast(1f)
            val fontPt = first.fontSizeInPt.coerceAtLeast(1f)
            val glyphH = first.heightDir.coerceAtLeast(fontPt * 0.75f)
            val top = (first.yDirAdj - glyphH).coerceAtLeast(0f)
            val left = first.xDirAdj.coerceAtLeast(0f)
            val right = (last.xDirAdj + last.widthDirAdj).coerceAtLeast(left + 1f)
            val logicalPageDp = 360f
            val fontSp = (fontPt * logicalPageDp / pw).coerceIn(5f, 48f)
            sink(
                ImportedPdfLine(
                    pageIndex = (currentPageNo - 1).coerceAtLeast(0),
                    xFrac = (left / pw).coerceIn(0f, 0.98f),
                    yFrac = (top / ph).coerceIn(0f, 0.98f),
                    wFrac = ((right - left) / pw).coerceIn(0.005f, 1f),
                    hFrac = (glyphH * 1.15f / ph).coerceIn(0.004f, 0.2f),
                    fontSizeSp = fontSp,
                    text = trimmed,
                    colorArgb = currentFillArgb()
                )
            )
        }

        private fun currentFillArgb(): Long {
            return try {
                val nsc = graphicsState.nonStrokingColor ?: return 0xFF000000L
                val rgb = nsc.colorSpace.toRGB(nsc.components)
                val r = (rgb[0].coerceIn(0f, 1f) * 255f).toInt()
                val g = (rgb[1].coerceIn(0f, 1f) * 255f).toInt()
                val b = (rgb[2].coerceIn(0f, 1f) * 255f).toInt()
                (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
            } catch (_: Exception) {
                0xFF000000L
            }
        }
    }
}
