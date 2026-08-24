package com.pdfcraft.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfcraft.studio.core.image.ImageCompressor
import com.pdfcraft.studio.core.image.ImageHandler
import com.pdfcraft.studio.core.image.ImageSizeOption
import com.pdfcraft.studio.core.settings.ImageSizePreferences
import com.pdfcraft.studio.core.text.AppFont
import com.pdfcraft.studio.core.text.FontCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportedImage(
    val id: String,
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val approxSizeBytes: Int? = null,
    val linkUrl: String? = null,
    /** 1-based label; null = no numbering icon */
    val numberLabel: Int? = null,
    /** Center of icon inside image, 0f..1f */
    val numberXFrac: Float = 0.5f,
    val numberYFrac: Float = 0.5f,
    /** Relative icon diameter vs min(image side), \~0.08..0.35 */
    val numberSizeFrac: Float = 0.18f,
    /** 0f..1f */
    val numberAlpha: Float = 0.9f,
    /** Icon circle fill ARGB */
    val numberBgArgb: Long = 0xE6000000L,
    /** Icon digit color ARGB */
    val numberFgArgb: Long = 0xFFFFFFFFL,
    /** 0f=Thin .. 1f=Bold */
    val numberWeight: Float = 0.85f,
    /** Extra shift from grid cell, as fraction of page width/height (-1..1). */
    val dragOffsetXFrac: Float = 0f,
    val dragOffsetYFrac: Float = 0f
)

data class ColorRange(
    val range: IntRange,
    val colorArgb: Long
)

data class TextElement(
    val id: String,
    val pageIndex: Int,
    val text: String = "",
    val xFraction: Float = 0.1f,
    val yFraction: Float = 0.1f,
    val boldRanges: List<IntRange> = emptyList(),
    val italicRanges: List<IntRange> = emptyList(),
    val colorRanges: List<ColorRange> = emptyList(),
    val bgColorRanges: List<ColorRange> = emptyList(),
    val fontId: String = FontCatalog.ID_DEFAULT,
    val fontSizeSp: Float = 16f,
    val textColorArgb: Long = 0xFF000000L,
    val bgColorArgb: Long? = null,
    val shadowColorArgb: Long = 0x80000000L,
    val shadowOffsetPx: Float = 0f,
    val shadowBlurPx: Float = 0f
)

private fun adjustRangesForEdit(
    oldText: String,
    newText: String,
    ranges: List<IntRange>
): List<IntRange> {
    var prefix = 0
    val minLen = minOf(oldText.length, newText.length)
    while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++

    var suffix = 0
    while (
        suffix < (minLen - prefix) &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++

    val oldEditEnd = oldText.length - suffix
    val lengthDelta = newText.length - oldText.length

    return ranges.mapNotNull { range ->
        when {
            range.last < prefix -> range
            range.first >= oldEditEnd -> (range.first + lengthDelta)..(range.last + lengthDelta)
            else -> null
        }
    }
}

private fun toggleStyleRange(
    existing: List<IntRange>,
    start: Int,
    end: Int,
    textLength: Int
): List<IntRange> {
    if (textLength <= 0) return existing

    val flags = BooleanArray(textLength)
    existing.forEach { range ->
        for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(textLength - 1)) {
            flags[i] = true
        }
    }

    val allOn = (start until end).all { flags.getOrElse(it) { false } }
    for (i in start until end) {
        if (i in flags.indices) flags[i] = !allOn
    }

    val result = mutableListOf<IntRange>()
    var rangeStart = -1
    for (i in flags.indices) {
        if (flags[i]) {
            if (rangeStart == -1) rangeStart = i
        } else if (rangeStart != -1) {
            result.add(rangeStart until i)
            rangeStart = -1
        }
    }
    if (rangeStart != -1) result.add(rangeStart until flags.size)
    return result
}


private fun applyColorRange(
    existing: List<ColorRange>,
    start: Int,
    end: Int,
    colorArgb: Long,
    textLength: Int
): List<ColorRange> {
    if (textLength <= 0 || start >= end) return existing
    val s = start.coerceIn(0, textLength)
    val e = end.coerceIn(0, textLength)
    if (s >= e) return existing

    val result = mutableListOf<ColorRange>()
    for (cr in existing) {
        val rs = cr.range.first
        val re = cr.range.last + 1
        if (re <= s || rs >= e) {
            result.add(cr)
        } else {
            if (rs < s) {
                result.add(ColorRange(rs until s, cr.colorArgb))
            }
            if (re > e) {
                result.add(ColorRange(e until re, cr.colorArgb))
            }
        }
    }
    result.add(ColorRange(s until e, colorArgb))
    val sorted = result.sortedBy { it.range.first }
    val merged = mutableListOf<ColorRange>()
    for (cr in sorted) {
        if (merged.isEmpty()) {
            merged.add(cr)
        } else {
            val last = merged.last()
            if (last.colorArgb == cr.colorArgb && last.range.last + 1 >= cr.range.first) {
                merged[merged.lastIndex] = ColorRange(
                    last.range.first..(maxOf(last.range.last, cr.range.last)),
                    cr.colorArgb
                )
            } else {
                merged.add(cr)
            }
        }
    }
    return merged
}

private fun adjustColorRangesForEdit(
    oldText: String,
    newText: String,
    ranges: List<ColorRange>
): List<ColorRange> {
    var prefix = 0
    val minLen = minOf(oldText.length, newText.length)
    while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++

    var suffix = 0
    while (
        suffix < (minLen - prefix) &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++

    val oldEditEnd = oldText.length - suffix
    val lengthDelta = newText.length - oldText.length

    return ranges.mapNotNull { cr ->
        val range = cr.range
        when {
            range.last < prefix -> cr
            range.first >= oldEditEnd -> ColorRange(
                (range.first + lengthDelta)..(range.last + lengthDelta),
                cr.colorArgb
            )
            else -> null
        }
    }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageHandler = ImageHandler(application.contentResolver)
    private val imageCompressor = ImageCompressor()
    private val imageSizePreferences = ImageSizePreferences(application)

    val importedImages: SnapshotStateList<ImportedImage> = mutableStateListOf()

    var selectedImageSizeOption: ImageSizeOption by mutableStateOf(imageSizePreferences.getSavedOption())
        private set

    var imagesPerRow: Int by mutableStateOf(4)
        private set

    var imageSpacingDp: Int by mutableStateOf(6)
        private set

    var imageCellAspectRatio: Float by mutableStateOf(1.192929f)
        private set

    var imageCornerRadiusPercent: Int by mutableStateOf(0)
        private set

    // ---- Page Tools state ----
    var pageAspectRatio: Float by mutableStateOf(0.673f)
    /** Per-page aspect overrides; missing key uses [pageAspectRatio]. */
    val pageAspectOverrides = mutableStateMapOf<Int, Float>()
    /** Page indices selected in Set Page Size tool. */
    val pageSizeSelection = mutableStateListOf<Int>()

    var isPageLandscape: Boolean by mutableStateOf(false)
        private set

    var pageMarginDp: Int by mutableStateOf(10)
        private set

    var pageBackgroundColor: Long by mutableStateOf(imageSizePreferences.getPageBackgroundColor())
    val pageBackgroundColorOverrides = mutableStateMapOf<Int, Long>()
    val pageBgColorSelection = mutableStateListOf<Int>()
    val pageDeleteSelection = mutableStateListOf<Int>()
    val pageDuplicateSelection = mutableStateListOf<Int>()

    var pageBackgroundImageUri: Uri? by mutableStateOf(null)
        private set

    var pageBackgroundBitmap: Bitmap? by mutableStateOf(null)
    val pageBackgroundBitmapOverrides = mutableStateMapOf<Int, Bitmap>()

    enum class PageNumberPosition { NONE, LEFT, CENTER, RIGHT }
    enum class PageNumberStyle { ARABIC, ROMAN_LOWER, ROMAN_UPPER, ALPHA_LOWER, ALPHA_UPPER }

    var pageNumberPosition: PageNumberPosition by mutableStateOf(PageNumberPosition.NONE)
        private set

    var pageNumberStyle: PageNumberStyle by mutableStateOf(PageNumberStyle.ARABIC)
        private set

    var minPageCount: Int by mutableStateOf(1)
        private set

    var isImporting: Boolean by mutableStateOf(false)
        private set

    val selectedImageIds: SnapshotStateList<String> = mutableStateListOf()

    var selectionMode: Boolean by mutableStateOf(false)
        private set

    var singleMenuImageId: String? by mutableStateOf(null)
        private set

    var multipleActionsVisible: Boolean by mutableStateOf(false)
        private set

    var reorderMode: Boolean by mutableStateOf(false)
    var pendingReplaceImageId: String? by mutableStateOf(null)
    /** 0=none, 1=move, 2=swap */
    var imagePositionMode: Int by mutableStateOf(0)
    var imagePositionSourceId: String? by mutableStateOf(null)

    private var clipboardImages: List<ImportedImage> by mutableStateOf(emptyList())

    val hasClipboardImages: Boolean
        get() = clipboardImages.isNotEmpty()

    val textElements: SnapshotStateList<TextElement> = mutableStateListOf()

    var addTextMode: Boolean by mutableStateOf(false)
        private set

    var selectedTextId: String? by mutableStateOf(null)
        private set

    var focusedTextId: String? by mutableStateOf(null)
        private set

    var pendingFocusTextId: String? by mutableStateOf(null)
        private set

    var currentSelection: TextRange by mutableStateOf(TextRange.Zero)
        private set

    val availableFonts: SnapshotStateList<AppFont> = mutableStateListOf()

    var lastFontImportMessage: String? by mutableStateOf(null)
        private set

    init {
        refreshAvailableFonts()
    }

    fun refreshAvailableFonts() {
        availableFonts.clear()
        availableFonts.addAll(FontCatalog.allFonts(getApplication()))
    }

    fun enterAddTextMode() {
        addTextMode = true
        selectedTextId = null
    }

    fun addTextAt(pageIndex: Int, xFraction: Float, yFraction: Float) {
        val id = "text_" + System.currentTimeMillis() + "_" + textElements.size
        textElements.add(
            TextElement(
                id = id,
                pageIndex = pageIndex,
                xFraction = xFraction,
                yFraction = yFraction
            )
        )
        selectedTextId = id
        addTextMode = false
        pendingFocusTextId = id
    }

    fun selectText(id: String) {
        selectedTextId = id
    }

    fun deselectText() {
        selectedTextId = null
    }

    fun consumePendingFocus() {
        pendingFocusTextId = null
    }

    fun onTextFocused(id: String) {
        focusedTextId = id
        selectedTextId = id
    }

    fun onTextUnfocused(id: String) {
        if (focusedTextId == id) {
            focusedTextId = null
        }
    }

    fun updateTextValue(id: String, newText: String, newSelection: TextRange) {
        val index = textElements.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = textElements[index]
            val adjustedBold = if (newText != current.text) {
                adjustRangesForEdit(current.text, newText, current.boldRanges)
            } else {
                current.boldRanges
            }
            val adjustedItalic = if (newText != current.text) {
                adjustRangesForEdit(current.text, newText, current.italicRanges)
            } else {
                current.italicRanges
            }
            val adjustedColors = if (newText != current.text) {
                adjustColorRangesForEdit(current.text, newText, current.colorRanges)
            } else {
                current.colorRanges
            }
            val adjustedBgColors = if (newText != current.text) {
                adjustColorRangesForEdit(current.text, newText, current.bgColorRanges)
            } else {
                current.bgColorRanges
            }
            textElements[index] = current.copy(
                text = newText,
                boldRanges = adjustedBold,
                italicRanges = adjustedItalic,
                colorRanges = adjustedColors,
                bgColorRanges = adjustedBgColors
            )
        }
        currentSelection = newSelection
    }

    fun moveText(id: String, xFraction: Float, yFraction: Float) {
        val index = textElements.indexOfFirst { it.id == id }
        if (index >= 0) {
            textElements[index] = textElements[index].copy(
                xFraction = xFraction,
                yFraction = yFraction
            )
        }
    }

    private fun activeTextIndex(): Int {
        val id = focusedTextId ?: selectedTextId ?: return -1
        return textElements.indexOfFirst { it.id == id }
    }

    fun toggleBoldForSelection() {
        val index = activeTextIndex()
        if (index < 0) return
        val selection = currentSelection
        if (selection.collapsed) return

        val element = textElements[index]
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)
        if (start >= end) return

        val newRanges = toggleStyleRange(element.boldRanges, start, end, element.text.length)
        textElements[index] = element.copy(boldRanges = newRanges)
    }

    fun toggleItalicForSelection() {
        val index = activeTextIndex()
        if (index < 0) return
        val selection = currentSelection
        if (selection.collapsed) return

        val element = textElements[index]
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)
        if (start >= end) return

        val newRanges = toggleStyleRange(element.italicRanges, start, end, element.text.length)
        textElements[index] = element.copy(italicRanges = newRanges)
    }

    fun isSelectionBold(): Boolean {
        val index = activeTextIndex()
        if (index < 0) return false
        val selection = currentSelection
        if (selection.collapsed) return false
        val element = textElements[index]
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)
        if (start >= end) return false
        return (start until end).all { i ->
            element.boldRanges.any { i in it }
        }
    }

    fun isSelectionItalic(): Boolean {
        val index = activeTextIndex()
        if (index < 0) return false
        val selection = currentSelection
        if (selection.collapsed) return false
        val element = textElements[index]
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)
        if (start >= end) return false
        return (start until end).all { i ->
            element.italicRanges.any { i in it }
        }
    }

    fun currentTextFontId(): String {
        val index = activeTextIndex()
        if (index < 0) return FontCatalog.ID_DEFAULT
        return textElements[index].fontId
    }

    fun deleteSelectedText() {
        val id = focusedTextId ?: selectedTextId ?: return
        textElements.removeAll { it.id == id }
        if (focusedTextId == id) focusedTextId = null
        if (selectedTextId == id) selectedTextId = null
        pendingFocusTextId = null
    }

    fun applyFontToSelectedText(font: AppFont) {
        val index = activeTextIndex()
        if (index < 0) return
        textElements[index] = textElements[index].copy(fontId = font.id)
    }

    fun importFontFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FontCatalog.importFont(getApplication(), uri)
            }
            if (result != null) {
                refreshAvailableFonts()
                applyFontToSelectedText(result)
                lastFontImportMessage = "Imported: ${result.displayName}"
            } else {
                lastFontImportMessage = "Could not import font"
            }
        }
    }

    fun consumeFontImportMessage() {
        lastFontImportMessage = null
    }

    fun selectImageSizeOption(option: ImageSizeOption) {
        selectedImageSizeOption = option
        imageSizePreferences.saveOption(option)
    }


    /** Page Size slider 1..100 → aspect (same as PageSizeSlider). */
    private fun pageSizePercentToRatio(percent: Int): Float {
        val t = percent.coerceIn(1, 100) / 100f
        return 0.4f + t * (2.5f - 0.4f)
    }

    /** Set Image Length slider 1..100 → cell aspect (same as ImageShapeSlider). */
    private fun imageLengthPercentToRatio(percent: Int): Float {
        val minR = 0.3f
        val maxR = 2.0f
        val t = (percent.coerceIn(1, 100) - 1) / 99f
        return minR + t * (maxR - minR)
    }

    /**
     * Import Images ratio presets.
     * Currently only "portrait" (9:16) applies settings; others reserved.
     */
    fun applyImportRatioPreset(key: String) {
        when (key) {
            "portrait" -> {
                // 9:16 — Page Size 8, Per Row 4, Spacing 7, Image Length 15
                pageAspectRatio = pageSizePercentToRatio(8)
                pageAspectOverrides.clear()
                imagesPerRow = 4
                imageSpacingDp = 7
                imageCellAspectRatio = imageLengthPercentToRatio(15)
            }
            "landscape" -> {
                // 16:9 — Page Size 11, Per Row 3, Spacing 6, Image Length 84
                pageAspectRatio = pageSizePercentToRatio(11)
                pageAspectOverrides.clear()
                imagesPerRow = 3
                imageSpacingDp = 6
                imageCellAspectRatio = imageLengthPercentToRatio(84)
            }
            "square" -> {
                // 1:1 — Page Size 13, Per Row 4, Spacing 6, Image Length 53
                pageAspectRatio = pageSizePercentToRatio(13)
                pageAspectOverrides.clear()
                imagesPerRow = 4
                imageSpacingDp = 6
                imageCellAspectRatio = imageLengthPercentToRatio(53)
            }
        }
    }


    // ---- Drag Images (offset from grid cell; stays on same page) ----
    var dragModeActive: Boolean by mutableStateOf(false)
    var dragPageIndex: Int by mutableStateOf(0)
    val dragSelectedIds: SnapshotStateList<String> = mutableStateListOf()

    fun startDragImagesOnPage(pageIndex: Int) {
        dragPageIndex = pageIndex.coerceAtLeast(0)
        dragSelectedIds.clear()
        dragModeActive = false
    }

    fun setDragImageSelection(ids: Collection<String>) {
        dragSelectedIds.clear()
        dragSelectedIds.addAll(ids)
    }

    fun enterDragMoveMode() {
        if (dragSelectedIds.isEmpty()) return
        dragModeActive = true
    }

    fun exitDragImages() {
        dragModeActive = false
        dragSelectedIds.clear()
    }

    /** Images currently laid out on [pageIndex] (skips spacers). */

    fun imagesPerPageCapacity(
        pageAspect: Float = pageAspectRatio,
        spacingDp: Int = imageSpacingDp,
        cellAspect: Float = imageCellAspectRatio,
    ): Int {
        val aspect = pageAspect.coerceAtLeast(0.1f)
        val rows = maxOf(1, (1f / (cellAspect.coerceAtLeast(0.05f) * aspect) * 0.85f).toInt())
        return (imagesPerRow.coerceAtLeast(1) * rows).coerceAtLeast(1)
    }

    fun imagesOnPage(pageIndex: Int): List<ImportedImage> {
        val per = imagesPerPageCapacity().coerceAtLeast(1)
        val start = pageIndex.coerceAtLeast(0) * per
        val end = minOf(start + per, importedImages.size)
        if (start >= importedImages.size) return emptyList()
        return importedImages.subList(start, end).filter { !it.id.startsWith("spacer_") }
    }

    fun imagesOnPages(pageIndices: Collection<Int>): List<ImportedImage> {
        if (pageIndices.isEmpty()) return emptyList()
        return pageIndices.sorted().flatMap { imagesOnPage(it) }.distinctBy { it.id }
    }

    private fun groupDragCenter(): Pair<Float, Float> {
        val imgs = dragSelectedIds.mapNotNull { id -> importedImages.firstOrNull { it.id == id } }
        if (imgs.isEmpty()) return 0.5f to 0.5f
        val cx = imgs.map { 0.5f + it.dragOffsetXFrac }.average().toFloat()
        val cy = imgs.map { 0.5f + it.dragOffsetYFrac }.average().toFloat()
        return cx to cy
    }

    fun dragGroupXPercent(): Float =
        ((groupDragCenter().first).coerceIn(0f, 1f) * 100f).coerceIn(0f, 100f)

    fun dragGroupYPercent(): Float =
        ((groupDragCenter().second).coerceIn(0f, 1f) * 100f).coerceIn(0f, 100f)

    fun nudgeDragImages(dx: Float, dy: Float) {
        if (dragSelectedIds.isEmpty()) return
        val step = 0.0025f
        for (id in dragSelectedIds.toList()) {
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val img = importedImages[idx]
            val nx = (img.dragOffsetXFrac + dx * step).coerceIn(-0.92f, 0.92f)
            val ny = (img.dragOffsetYFrac + dy * step).coerceIn(-0.92f, 0.92f)
            importedImages[idx] = img.copy(dragOffsetXFrac = nx, dragOffsetYFrac = ny)
        }
    }

    fun centerDragImages() {
        if (dragSelectedIds.isEmpty()) return
        val (cx, cy) = groupDragCenter()
        val ddx = 0.5f - cx
        val ddy = 0.5f - cy
        for (id in dragSelectedIds.toList()) {
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val img = importedImages[idx]
            importedImages[idx] = img.copy(
                dragOffsetXFrac = (img.dragOffsetXFrac + ddx).coerceIn(-0.92f, 0.92f),
                dragOffsetYFrac = (img.dragOffsetYFrac + ddy).coerceIn(-0.92f, 0.92f)
            )
        }
    }

    fun setDragGroupPositionPercent(xPercent: Float?, yPercent: Float?) {
        if (dragSelectedIds.isEmpty()) return
        val (cx, cy) = groupDragCenter()
        val tx = if (xPercent != null) xPercent.coerceIn(0f, 100f) / 100f else cx
        val ty = if (yPercent != null) yPercent.coerceIn(0f, 100f) / 100f else cy
        val ddx = tx - cx
        val ddy = ty - cy
        for (id in dragSelectedIds.toList()) {
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val img = importedImages[idx]
            importedImages[idx] = img.copy(
                dragOffsetXFrac = (img.dragOffsetXFrac + ddx).coerceIn(-0.92f, 0.92f),
                dragOffsetYFrac = (img.dragOffsetYFrac + ddy).coerceIn(-0.92f, 0.92f)
            )
        }
    }

