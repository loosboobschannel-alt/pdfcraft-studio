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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI

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
        minPageCount: Int = 1,
        pageAspectRatioForPage: ((Int) -> Float)? = null
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

        val linkRects = mutableListOf<LinkRect>()
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        pages.forEachIndexed { pageIndex, pageImages ->
            val aspect = (pageAspectRatioForPage?.invoke(pageIndex) ?: pageAspectRatio).coerceAtLeast(0.1f)
            val thisPageHeight = (pageWidth / aspect).toInt().coerceAtLeast(400)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, thisPageHeight, pageIndex + 1).create()
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
                    val drawn = drawBitmapFit(
                        canvas = canvas,
                        bitmap = bmp,
                        dstLeft = left,
                        dstTop = top,
                        dstWidth = cellWidth,
                        dstHeight = cellHeight,
                        paint = paint
                    )
                    val url = img.linkUrl?.trim()?.takeIf { it.isNotEmpty() }
                    if (drawn != null && url != null) {
                        linkRects.add(
                            LinkRect(
                                pageIndex = pageIndex,
                                left = drawn.left,
                                top = drawn.top,
                                right = drawn.right,
                                bottom = drawn.bottom,
                                pageHeight = thisPageHeight.toFloat(),
                                url = url
                            )
                        )
                    }
                }
            }

            // Text: only elements for this page index
            textElements.filter { it.pageIndex == pageIndex }.forEach { te ->
                textPaint.color = te.textColorArgb.toInt()
                textPaint.textSize = te.fontSizeSp * (pageWidth / 360f)
                val x = te.xFraction * pageWidth
                val y = te.yFraction * thisPageHeight + textPaint.textSize
                canvas.drawText(te.text, x, y, textPaint)
            }

            pdf.finishPage(page)
        }

        return try {
            val baos = ByteArrayOutputStream()
            pdf.writeTo(baos)
            pdf.close()
            var bytes = baos.toByteArray()
            if (linkRects.isNotEmpty()) {
                bytes = addLinkAnnotations(bytes, linkRects, context)
            }
            val uri = saveBytesToDocuments(context, safeName, bytes)
            if (uri != null) {
                Result(true, fileName = safeName)
            } else {
                Result(false, message = "save_failed")
            }
        } catch (e: Exception) {
            try { pdf.close() } catch (_: Exception) {}
            Result(false, message = e.message ?: "error")
        }
    }

    private fun saveBytesToDocuments(context: Context, fileName: String, bytes: ByteArray): Uri? {
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
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }


    /** Draw bitmap centered inside dst rect using Fit (no crop). */
    private data class LinkRect(
        val pageIndex: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val pageHeight: Float,
        val url: String
    )

    /** Draw bitmap centered inside dst rect using Fit (no crop). Returns drawn bounds. */
    private fun drawBitmapFit(
        canvas: Canvas,
        bitmap: Bitmap,
        dstLeft: Float,
        dstTop: Float,
        dstWidth: Float,
        dstHeight: Float,
        paint: Paint
    ): android.graphics.RectF? {
        if (bitmap.width <= 0 || bitmap.height <= 0 || dstWidth <= 0f || dstHeight <= 0f) return null
        val bmpAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val cellAspect = dstWidth / dstHeight
        val drawW: Float
        val drawH: Float
        if (bmpAspect > cellAspect) {
            drawW = dstWidth
            drawH = dstWidth / bmpAspect
        } else {
            drawH = dstHeight
            drawW = dstHeight * bmpAspect
        }
        val left = dstLeft + (dstWidth - drawW) / 2f
        val top = dstTop + (dstHeight - drawH) / 2f
        val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val dst = android.graphics.RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bitmap, src, dst, paint)
        return dst
    }

    private fun addLinkAnnotations(pdfBytes: ByteArray, links: List<LinkRect>, context: Context): ByteArray {
        if (links.isEmpty()) return pdfBytes
        return try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(ByteArrayInputStream(pdfBytes)).use { doc ->
                links.forEach { link ->
                    if (link.pageIndex !in 0 until doc.numberOfPages) return@forEach
                    val page = doc.getPage(link.pageIndex)
                    // Use the exact size this page was generated with instead of
                    // page.mediaBox — PDFBox-Android can misread an inherited /MediaBox
                    // on pages written by android.graphics.pdf.PdfDocument and silently
                    // fall back to Letter size (612x792), corrupting the link rect.
                    val pageW = 1080f
                    val pageH = link.pageHeight.coerceAtLeast(1f)
                    val scaleX = 1f
                    val scaleY = 1f

                    // Canvas (top-left, Y down) → PDF user space (bottom-left, Y up)
                    val left = link.left * scaleX
                    val right = link.right * scaleX
                    val top = link.top * scaleY
                    val bottom = link.bottom * scaleY

                    val llx = left.coerceIn(0f, pageW)
                    val lly = (pageH - bottom).coerceIn(0f, pageH)
                    val urx = right.coerceIn(0f, pageW)
                    val ury = (pageH - top).coerceIn(0f, pageH)
                    val w = (urx - llx).coerceAtLeast(1f)
                    val h = (ury - lly).coerceAtLeast(1f)

                    var uri = link.url.trim()
                    if (uri.isEmpty()) return@forEach
                    if (!uri.contains("://")) {
                        uri = "https://$uri"
                    }

                    val action = PDActionURI()
                    action.uri = uri

                    val annot = PDAnnotationLink()
                    annot.rectangle = PDRectangle(llx, lly, w, h)
                    annot.action = action
                    try {
                        val bs = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary()
                        bs.setWidth(0f)
                        annot.borderStyle = bs
                    } catch (_: Exception) {
                        try {
                            val bs = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary()
                            bs.width = 0f
                            annot.borderStyle = bs
                        } catch (_: Exception) { }
                    }

                    // CRITICAL: assign a new list back so ANNOTS is written into the page dict
                    // (plain .add on some PDFBox/Android PDFs does not persist)
                    val updated = ArrayList(page.annotations)
                    updated.add(annot)
                    page.annotations = updated
                }
                val out = ByteArrayOutputStream()
                doc.save(out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pdfBytes
        }
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
