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
import com.pdfcraft.studio.ui.editor.ShadowRange
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt
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
        val message: String = "",
        val linkWarning: String? = null,
        val savedUri: Uri? = null
    )

    
    private fun packImagesForPagesPdf(
        images: List<com.pdfcraft.studio.ui.editor.ImportedImage>,
        imagesPerRow: Int,
        spacingPx: Float,
        cellWidthPx: Float,
        gridHeightPx: Float,
        defaultAspect: Float
    ): List<List<com.pdfcraft.studio.ui.editor.ImportedImage>> {
        if (images.isEmpty()) return emptyList()
        val perRow = imagesPerRow.coerceAtLeast(1)
        val pages = mutableListOf<MutableList<com.pdfcraft.studio.ui.editor.ImportedImage>>()
        var page = mutableListOf<com.pdfcraft.studio.ui.editor.ImportedImage>()
        var usedH = 0f
        fun imgH(img: com.pdfcraft.studio.ui.editor.ImportedImage): Float {
            val aspect = (img.aspectRatioOverride ?: defaultAspect).coerceIn(0.3f, 2.0f)
            return cellWidthPx / aspect
        }
        fun rowH(row: List<com.pdfcraft.studio.ui.editor.ImportedImage>): Float =
            row.maxOf { imgH(it) }
        for (row in images.chunked(perRow)) {
            val h = rowH(row)
            val gap = if (page.isEmpty()) 0f else spacingPx
            if (page.isNotEmpty() && usedH + gap + h > gridHeightPx + 0.5f) {
                pages.add(page)
                page = mutableListOf()
                usedH = 0f
            }
            val g = if (page.isEmpty()) 0f else spacingPx
            page.addAll(row)
            usedH += g + h
        }
        if (page.isNotEmpty()) pages.add(page)
        return pages
    }

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
        val pageHeight = (pageWidth / pageAspectRatio.coerceAtLeast(0.1f)).roundToInt().coerceAtLeast(1)

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
        // Auto-flow images by how many fit per page (same as editor preview).
        val imagesPerPage = (perRow * rowsPerPage).coerceAtLeast(1)
        val imagePages = packImagesForPagesPdf(
            images = images,
            imagesPerRow = perRow,
            spacingPx = spacing,
            cellWidthPx = cellWidth,
            gridHeightPx = gridHeight,
            defaultAspect = cellAspect
        )

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
            val thisPageHeight = (pageWidth / aspect).roundToInt().coerceAtLeast(1)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, thisPageHeight, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(pageBackgroundColor.toInt())

            pageImages.forEachIndexed { idx, img ->
                val row = idx / perRow
                val col = idx % perRow
                // Safety: never draw a row that would go past the page bottom
                if (row >= rowsPerPage) return@forEachIndexed

                val left0 = margin + col * (cellWidth + spacing)
                val top0 = margin + row * (cellHeight + spacing)
                // Drag Images offsets: fraction of full page size (keep on page)
                val ox = img.dragOffsetXFrac * pageWidth
                val oy = img.dragOffsetYFrac * thisPageHeight
                var left = left0 + ox
                var top = top0 + oy
                left = left.coerceIn(0f, (pageWidth - cellWidth).coerceAtLeast(0f))
                top = top.coerceIn(0f, (thisPageHeight - cellHeight).coerceAtLeast(0f))
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
                    if (drawn != null && img.numberLabel != null) {
                        drawNumberBadge(
                            canvas = canvas,
                            label = img.numberLabel!!,
                            bounds = drawn,
                            xFrac = img.numberXFrac,
                            yFrac = img.numberYFrac,
                            sizeFrac = img.numberSizeFrac,
                            alpha = img.numberAlpha,
                            bgArgb = img.numberBgArgb,
                            fgArgb = img.numberFgArgb,
                            weight = img.numberWeight
                        )
                    }
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

            // Text: only elements for this page index (supports partial color + bg ranges)
            // Newlines are drawn as separate lines (editor multiline must match PDF).
            textElements.filter { it.pageIndex == pageIndex }.forEach { te ->
                textPaint.textSize = te.fontSizeSp * (pageWidth / 360f)
                val baseX = te.xFraction * pageWidth
                val firstBaseline = te.yFraction * thisPageHeight + textPaint.textSize
                val fullText = te.text
                if (fullText.isEmpty()) return@forEach

                val fg = LongArray(fullText.length) { te.textColorArgb }
                te.colorRanges.forEach { cr ->
                    val s = cr.range.first.coerceIn(0, fullText.length)
                    val e = (cr.range.last + 1).coerceIn(0, fullText.length)
                    for (i in s until e) fg[i] = cr.colorArgb
                }
                val bg = arrayOfNulls<Long>(fullText.length)
                if (te.bgColorArgb != null) {
                    for (i in fullText.indices) bg[i] = te.bgColorArgb
                }
                te.bgColorRanges.forEach { cr ->
                    val s = cr.range.first.coerceIn(0, fullText.length)
                    val e = (cr.range.last + 1).coerceIn(0, fullText.length)
                    for (i in s until e) bg[i] = cr.colorArgb
                }

                val fm = textPaint.fontMetrics
                var lineH = fm.descent - fm.ascent + fm.leading
                if (lineH < textPaint.textSize * 0.8f) lineH = textPaint.textSize * 1.2f

                val lines = mutableListOf<IntRange>()
                var ls = 0
                var ci = 0
                while (true) {
                    var le = ci
                    while (le < fullText.length && fullText[le] != '\n' && fullText[le] != '\r') le++
                    lines.add(ls until le)
                    if (le >= fullText.length) break
                    if (fullText[le] == '\r' && le + 1 < fullText.length && fullText[le + 1] == '\n') {
                        ci = le + 2
                    } else {
                        ci = le + 1
                    }
                    ls = ci
                }

                val bgPaint = Paint(textPaint).apply { style = Paint.Style.FILL }
                val scale = pageWidth / 360f
                val sh = arrayOfNulls<ShadowRange>(fullText.length)
                if (te.shadowRanges.isEmpty() &&
                    (te.shadowBlurPx > 0.01f || te.shadowOffsetXPx != 0f || te.shadowOffsetYPx != 0f)
                ) {
                    val whole = ShadowRange(0 until fullText.length, te.shadowColorArgb, te.shadowOffsetXPx, te.shadowOffsetYPx, te.shadowBlurPx)
                    for (si in fullText.indices) sh[si] = whole
                }
                te.shadowRanges.forEach { sr ->
                    val s = sr.range.first.coerceIn(0, fullText.length)
                    val e = (sr.range.last + 1).coerceIn(0, fullText.length)
                    for (si in s until e) sh[si] = sr
                }
                lines.forEachIndexed { lineNo, range ->
                    val y = firstBaseline + lineNo * lineH
                    var x = baseX
                    var i = range.first
                    val lineEnd = range.last + 1
                    while (i < lineEnd) {
                        val cFg = fg[i]
                        val cBg = bg[i]
                        val cSh = sh[i]
                        var j = i + 1
                        while (j < lineEnd && fg[j] == cFg && bg[j] == cBg && sh[j] == cSh) j++
                        val segment = fullText.substring(i, j)
                        val w = textPaint.measureText(segment)
                        if (cBg != null) {
                            bgPaint.color = cBg.toInt()
                            val top = y - textPaint.textSize * 0.85f
                            val bottom = y + textPaint.textSize * 0.2f
                            canvas.drawRect(x, top, x + w, bottom, bgPaint)
                        }
                        textPaint.color = cFg.toInt()
                        if (cSh != null) {
                            textPaint.setShadowLayer(
                                (cSh.blurPx * scale).coerceAtLeast(0.01f),
                                cSh.offsetXPx * scale,
                                cSh.offsetYPx * scale,
                                cSh.colorArgb.toInt()
                            )
                        } else {
                            textPaint.clearShadowLayer()
                        }
                        canvas.drawText(segment, x, y, textPaint)
                        x += w
                        i = j
                    }
                }

                textPaint.clearShadowLayer()

                te.linkRanges.forEach { lr ->
                    val url = lr.url.trim().takeIf { it.isNotEmpty() } ?: return@forEach
                    val s = lr.range.first.coerceIn(0, fullText.length)
                    val e = (lr.range.last + 1).coerceIn(0, fullText.length)
                    if (s >= e) return@forEach
                    lines.forEachIndexed { lineNo, range ->
                        val lineEnd = range.last + 1
                        val os = maxOf(s, range.first)
                        val oe = minOf(e, lineEnd)
                        if (os >= oe) return@forEachIndexed
                        val before = fullText.substring(range.first, os)
                        val linked = fullText.substring(os, oe)
                        val left = baseX + textPaint.measureText(before)
                        val width = textPaint.measureText(linked)
                        val y = firstBaseline + lineNo * lineH
                        val top = y - textPaint.textSize * 0.85f
                        val bottom = y + textPaint.textSize * 0.25f
                        linkRects.add(
                            LinkRect(
                                pageIndex = pageIndex,
                                left = left,
                                top = top,
                                right = left + width,
                                bottom = bottom,
                                pageHeight = thisPageHeight.toFloat(),
                                url = url
                            )
                        )
                    }
                }
            }

            pdf.finishPage(page)
        }

        return try {
            val baos = ByteArrayOutputStream()
            pdf.writeTo(baos)
            pdf.close()
            var bytes = baos.toByteArray()
            var linkWarning: String? = null
            if (linkRects.isNotEmpty()) {
                val stamped = addLinkAnnotations(bytes, linkRects, context)
                bytes = stamped.first
                linkWarning = stamped.second
            }
            val uri = saveBytesToDocuments(context, safeName, bytes)
            if (uri != null) {
                Result(true, fileName = safeName, linkWarning = linkWarning, savedUri = uri)
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
    
    
    private fun drawNumberBadge(
        canvas: Canvas,
        label: Int,
        bounds: android.graphics.RectF,
        xFrac: Float,
        yFrac: Float,
        sizeFrac: Float,
        alpha: Float,
        bgArgb: Long = 0xE6000000L,
        fgArgb: Long = 0xFFFFFFFFL,
        weight: Float = 0.85f
    ) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0f || h <= 0f) return
        val d = minOf(w, h) * sizeFrac.coerceIn(0.08f, 0.4f)
        val half = d / 2f
        val cx = (bounds.left + w * xFrac.coerceIn(0f, 1f)).coerceIn(bounds.left + half, bounds.right - half)
        val cy = (bounds.top + h * yFrac.coerceIn(0f, 1f)).coerceIn(bounds.top + half, bounds.bottom - half)
        val a = (alpha.coerceIn(0.15f, 1f) * 255f).toInt().coerceIn(40, 255)
        fun withAlpha(argb: Long): Int {
            val base = argb.toInt()
            val r = (base shr 16) and 0xFF
            val g = (base shr 8) and 0xFF
            val b = base and 0xFF
            val srcA = (base ushr 24) and 0xFF
            val outA = (srcA * a) / 255
            return android.graphics.Color.argb(outA, r, g, b)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(bgArgb)
        }
        canvas.drawCircle(cx, cy, half, fill)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(fgArgb)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = weight >= 0.67f
            textSize = d * 0.55f
            strokeWidth = if (weight < 0.34f) 0f else if (weight < 0.67f) 0.5f else 1.2f
            style = if (weight >= 0.85f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        }
        val text = label.toString()
        val fm = textPaint.fontMetrics
        // True vertical center using font metrics (works for 1, 10, 100...)
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, textY, textPaint)
    }

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
        val dst = android.graphics.RectF(left, top, left + drawW, top + drawH)

        // Downscale to on-page pixel size so PdfDocument does not embed
        // full-resolution bitmaps (main cause of multi-MB PDFs from tiny JPEGs).
        val targetW = drawW.toInt().coerceAtLeast(1)
        val targetH = drawH.toInt().coerceAtLeast(1)
        val toDraw: Bitmap
        val recycleAfter: Boolean
        if (bitmap.width > targetW * 2 || bitmap.height > targetH * 2) {
            toDraw = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            recycleAfter = toDraw !== bitmap
        } else {
            toDraw = bitmap
            recycleAfter = false
        }
        val src = android.graphics.Rect(0, 0, toDraw.width, toDraw.height)
        canvas.drawBitmap(toDraw, src, dst, paint)
        if (recycleAfter && !toDraw.isRecycled) {
            toDraw.recycle()
        }
        return dst
    }

    private fun addLinkAnnotations(pdfBytes: ByteArray, links: List<LinkRect>, context: Context): Pair<ByteArray, String?> {
        if (links.isEmpty()) return pdfBytes to null
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
                out.toByteArray() to null
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Failed to add link annotations", e)
            pdfBytes to (e.javaClass.simpleName + ": " + (e.message ?: "unknown error"))
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
