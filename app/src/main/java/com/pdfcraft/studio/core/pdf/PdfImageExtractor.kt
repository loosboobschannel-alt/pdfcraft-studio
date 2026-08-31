package com.pdfcraft.studio.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import android.graphics.Path
import android.graphics.PointF
import java.io.IOException

data class CapturedPdfImage(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val xFrac: Float,
    val yFrac: Float,
    val wFrac: Float,
    val hFrac: Float
)

object PdfImageExtractor {

    fun extract(context: Context, uri: Uri): List<CapturedPdfImage> {
        val out = ArrayList<CapturedPdfImage>()
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc ->
                    for (i in 0 until doc.numberOfPages) {
                        val page = doc.getPage(i)
                        Catcher(page, i, out).processPage(page)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private class Catcher(
        page: PDPage,
        private val pageIndex: Int,
        private val out: MutableList<CapturedPdfImage>
    ) : PDFGraphicsStreamEngine(page) {

        @Throws(IOException::class)
        override fun drawImage(pdImage: PDImage) {
            val bmp = try { pdImage.image } catch (_: Exception) { null } ?: return
            if (bmp.width < 8 || bmp.height < 8) return
            val box = page.mediaBox ?: return
            val pw = box.width.coerceAtLeast(1f)
            val ph = box.height.coerceAtLeast(1f)
            val m = graphicsState.currentTransformationMatrix
            val a = m.getValue(0, 0)
            val b = m.getValue(0, 1)
            val c = m.getValue(1, 0)
            val d = m.getValue(1, 1)
            val e = m.getValue(2, 0)
            val f = m.getValue(2, 1)
            fun mapX(x: Float, y: Float) = a * x + c * y + e
            fun mapY(x: Float, y: Float) = b * x + d * y + f
            val xs = floatArrayOf(mapX(0f, 0f), mapX(1f, 0f), mapX(1f, 1f), mapX(0f, 1f))
            val ys = floatArrayOf(mapY(0f, 0f), mapY(1f, 0f), mapY(1f, 1f), mapY(0f, 1f))
            val minX = xs.minOrNull() ?: return
            val maxX = xs.maxOrNull() ?: return
            val minY = ys.minOrNull() ?: return
            val maxY = ys.maxOrNull() ?: return
            val wFrac = ((maxX - minX) / pw).coerceIn(0f, 1f)
            val hFrac = ((maxY - minY) / ph).coerceIn(0f, 1f)
            if (wFrac * hFrac >= 0.72f) return
            if (wFrac < 0.012f && hFrac < 0.012f) return
            val xFrac = (minX / pw).coerceIn(0f, 0.98f)
            val yFrac = (1f - maxY / ph).coerceIn(0f, 0.98f)
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
            out.add(
                CapturedPdfImage(
                    pageIndex = pageIndex,
                    bitmap = copy,
                    xFrac = xFrac,
                    yFrac = yFrac,
                    wFrac = wFrac.coerceAtLeast(0.02f),
                    hFrac = hFrac.coerceAtLeast(0.02f)
                )
            )
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {}
        override fun clip(windingRule: Path.FillType) {}
        override fun moveTo(x: Float, y: Float) {}
        override fun lineTo(x: Float, y: Float) {}
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {}
        override fun getCurrentPoint(): PointF = PointF(0f, 0f)
        override fun closePath() {}
        override fun endPath() {}
        override fun strokePath() {}
        override fun fillPath(windingRule: Path.FillType) {}
        override fun fillAndStrokePath(windingRule: Path.FillType) {}
        override fun shadingFill(shadingName: COSName) {}
    }
}
