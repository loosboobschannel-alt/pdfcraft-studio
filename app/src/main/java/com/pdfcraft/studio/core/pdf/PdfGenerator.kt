package com.pdfcraft.studio.core.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * Builds a PDF from the current editor pages (images + text) and saves it
 * into Documents/PDFCraftStudio/ using MediaStore (no storage permission
 * needed on Android 10+).
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
        pageBackgroundColor: Long
    ): Result {
        if (images.isEmpty() && textElements.isEmpty()) {
            return Result(false, message = "empty")
        }

        val safeName = if (fileName.lowercase().endsWith(".pdf")) fileName else "$fileName.pdf"
        val pageWidth = 595 // A4-ish points
        val pageHeight = (pageWidth / pageAspectRatio).toInt().coerceAtLeast(400)

        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Group images into pages by imagesPerRow rows (simple grid)
        val rowsPerPage = 4
        val imagesPerPage = (imagesPerRow * rowsPerPage).coerceAtLeast(1)
        val pages = if (images.isEmpty()) listOf(emptyList()) else images.chunked(imagesPerPage)

        pages.forEachIndexed { pageIndex, pageImages ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            canvas.drawColor(pageBackgroundColor.toInt())

            // Draw images in a simple grid
            if (pageImages.isNotEmpty()) {
                val margin = 24f
                val gap = 12f
                val usableWidth = pageWidth - margin * 2
                val cellWidth = (usableWidth - gap * (imagesPerRow - 1)) / imagesPerRow
                val cellHeight = cellWidth / pageAspectRatio.coerceAtLeast(0.5f)

                pageImages.forEachIndexed { idx, img ->
                    val row = idx / imagesPerRow
                    val col = idx % imagesPerRow
                    val left = margin + col * (cellWidth + gap)
                    val top = margin + row * (cellHeight + gap)

                    val bmp = img.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                        val dst = android.graphics.RectF(left, top, left + cellWidth, top + cellHeight)
                        canvas.drawBitmap(bmp, src, dst, paint)
                    }
                }
            }

            // Draw text elements (simple absolute placement for now)
            textElements.forEach { te ->
                paint.color = te.textColorArgb.toInt()
                paint.textSize = te.fontSizeSp * 2.2f // rough scale to PDF points
                val x = te.xFraction * pageWidth
                val y = te.yFraction * pageHeight + paint.textSize
                canvas.drawText(te.text, x, y, paint)
            }

            pdf.finishPage(page)
        }

        // Save via MediaStore → Documents/PDFCraftStudio/
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

    private fun saveToDocuments(context: Context, fileName: String, pdf: PdfDocument): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PDFCraftStudio")
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
