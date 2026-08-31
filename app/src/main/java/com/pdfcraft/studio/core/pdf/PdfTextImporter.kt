package com.pdfcraft.studio.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

data class ImportedPdfLine(
    val pageIndex: Int,
    val xFrac: Float,
    val yFrac: Float,
    val fontSizeSp: Float,
    val text: String
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
        return mergeParagraphs(out)
    }

    private fun mergeParagraphs(lines: List<ImportedPdfLine>): List<ImportedPdfLine> {
        if (lines.isEmpty()) return lines
        val merged = ArrayList<ImportedPdfLine>()
        var cur = lines[0]
        for (i in 1 until lines.size) {
            val n = lines[i]
            val samePage = n.pageIndex == cur.pageIndex
            val close = kotlin.math.abs(n.yFrac - cur.yFrac) < 0.035f &&
                kotlin.math.abs(n.xFrac - cur.xFrac) < 0.08f
            if (samePage && close) {
                cur = cur.copy(text = cur.text + "\n" + n.text)
            } else {
                merged.add(cur)
                cur = n
            }
        }
        merged.add(cur)
        return merged
    }

    private class LineStripper(
        private val sink: (ImportedPdfLine) -> Unit
    ) : PDFTextStripper() {
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || textPositions.isEmpty()) return
            val first = textPositions[0]
            val page = currentPage ?: return
            val box = page.mediaBox ?: return
            val pw = box.width.coerceAtLeast(1f)
            val ph = box.height.coerceAtLeast(1f)
            val xFrac = (first.xDirAdj / pw).coerceIn(0f, 0.92f)
            val yFrac = (first.yDirAdj / ph).coerceIn(0f, 0.92f)
            val sp = first.fontSizeInPt.coerceIn(8f, 42f)
            sink(
                ImportedPdfLine(
                    pageIndex = (currentPageNo - 1).coerceAtLeast(0),
                    xFrac = xFrac,
                    yFrac = yFrac,
                    fontSizeSp = sp,
                    text = trimmed
                )
            )
        }
    }
}
