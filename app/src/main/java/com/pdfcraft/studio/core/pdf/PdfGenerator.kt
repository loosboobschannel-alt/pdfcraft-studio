package com.pdfcraft.studio.core.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pdfcraft.studio.ui.editor.ImportedImage
import com.pdfcraft.studio.ui.editor.TextElement
import java.io.OutputStream

/**
 * Builds a PDF from the current editor pages (images + text).
 * Page grid math MUST match the editor preview (PdfPagesPreview)
 * so images never crop at the bottom of a page.
 */
object PdfGenerator {

    data class Result(
        val success: Boolean,
        val fileName: String = "",
        val message: String = ""
    )

    fun export(
        context: Context,
        fileName: String,
        images: List<ImportedImage>,
        textElements: List<TextElement>,
        imagesPerRow: Int,
        pageAspectRatio: Float,
        pageBackgroundColor: Long,
        imageSpacingDp: Int = 6,
        imageCellAspectRatio: Float = 0.526f,
        pageMarginDp: Int = 10,
        minPageCount: Int = 1
    ): Result {
        if (images.isEmpty() && textElements.isEmpty()) {
            return Result(false, message = "empty")
        }

        val safeName = if (fileName.lowercase().endsWith(".pdf")) fileName else "$fileName.pdf"

        // Use a fixed logical width; height follows editor page aspect ratio
        val pageWidth = 1080
        val pageHeight = (pageWidth / pageAspectRatio.coerceAtLeast(0.1f)).toInt().coerceAtLeast(400)

        // Same margin model as editor (pageMarginDp / PAGE_INNER_PADDING)
        val margin = pageMarginDp.toFloat() * (pageWidth / 360f) // scale dp-ish to page px
        val spacing = imageSpacingDp.toFloat() * (pageWidth / 360f)
        val gridWidth = pageWidth - margin * 2f
        val gridHeight = pageHeight - margin * 2f

        val perRow = imagesPerRow.coerceAtLeast(1)
        val cellWidth = (gridWidth - spacing * (perRow - 1)) / perRow
        val cellAspect = imageCellAspectRatio.coerceAtLeast(0.1f)
        val cellHeight = cellWidth / cellAspect

        // CRITICAL: same formula as PdfPagesPreview — only full rows that fit
        val rowsPerPage = if (cellHeight > 0f) {
            (((gridHeight + spacing) / (cellHeight + spacing)).toInt()).coerceAtLeast(1)
        } else {
            1
        }
        val imagesPerPage = (perRow * rowsPerPage).coerceAtLeast(1)
        val imagePages = if (images.isEmpty()) emptyList() else images.chunked(imagesPerPage)

        // Include every page that has text OR blank pages the user added in the editor.
        // Previously text-only page 2+ were dropped because export only used image chunks.
        val maxTextPageIndex = textElements.maxOfOrNull { it.pageIndex } ?: -1
        val totalPages = maxOf(
            imagePages.size,
            maxTextPageIndex + 1,
            minPageCount.coerceAtLeast(1),
            1
        )
        val pages = List(totalPages) { index ->
            imagePages.getOrElse(index) { emptyList() }
        }

        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        pages.forEachIndexed { pageIndex, pageImages ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(pageBackgroundColor.toInt())

            pageImages.forEachIndexed { idx, img ->
                val row = idx / perRow
                val col = idx % perRow
                // Safety: never draw a row that would go past the page bottom
                if (row >= rowsPerPage) return@forEachIndexed

                val left = margin + col * (cellWidth + spacing)
                val top = margin + row * (cellHeight + spacing)
                val bmp = img.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    // ContentScale.Fit equivalent — no crop, letterbox inside cell
                    drawBitmapFit(
                        canvas = canvas,
                        bitmap = bmp,
                        dstLeft = left,
                        dstTop = top,
                        dstWidth = cellWidth,
                        dstHeight = cellHeight,
                        paint = paint
                    )
                }
            }

            // Text: only elements for this page index
            textElements.filter { it.pageIndex == pageIndex }.forEach { te ->
                textPaint.color = te.textColorArgb.toInt()
                textPaint.textSize = te.fontSizeSp * (pageWidth / 360f)
                val x = te.xFraction * pageWidth
                val y = te.yFraction * pageHeight + textPaint.textSize
                canvas.drawText(te.text, x, y, textPaint)
            }

            pdf.finishPage(page)
        }

        return try {
            val uri = saveToDocuments(context, safeName, pdf)
            pdf.close()
            if (uri != null) {
                Result(true, fileName = safeName)
            } else {
                Result(false, message = "save_failed")
            }
        } catch (e: Exception) {
            pdf.close()
            Result(false, message = e.message ?: "error")
        }
    }

    /** Draw bitmap centered inside dst rect using Fit (no crop). */
    private fun drawBitmapFit(
        canvas: Canvas,
        bitmap: Bitmap,
        dstLeft: Float,
        dstTop: Float,
        dstWidth: Float,
        dstHeight: Float,
        paint: Paint
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0 || dstWidth <= 0f || dstHeight <= 0f) return
        val bmpAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val cellAspect = dstWidth / dstHeight
        val drawW: Float
        val drawH: Float
        if (bmpAspect > cellAspect) {
            // wider than cell → fit width
            drawW = dstWidth
            drawH = dstWidth / bmpAspect
        } else {
            // taller → fit height
            drawH = dstHeight
            drawW = dstHeight * bmpAspect
        }
        val left = dstLeft + (dstWidth - drawW) / 2f
        val top = dstTop + (dstHeight - drawH) / 2f
        val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val dst = android.graphics.RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    private fun saveToDocuments(context: Context, fileName: String, pdf: PdfDocument): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/PDFCraftStudio"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { out: OutputStream ->
            pdf.writeTo(out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
