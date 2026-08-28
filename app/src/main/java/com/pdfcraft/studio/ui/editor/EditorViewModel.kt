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
    val dragOffsetYFrac: Float = 0f,
    /** 0..100 clip radius for this image only. */
    val cornerRadiusPercent: Int = 0,
    /** Null = use global imageCellAspectRatio (Set/Resize Images). */
    val aspectRatioOverride: Float? = null
)

data class ColorRange(
    val range: IntRange,
    val colorArgb: Long
)

data class LinkRange(
    val range: IntRange,
    val url: String
)

data class ShadowRange(
    val range: IntRange,
    val colorArgb: Long,
    val offsetXPx: Float,
    val offsetYPx: Float,
    val blurPx: Float
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
    val linkRanges: List<LinkRange> = emptyList(),
    val shadowRanges: List<ShadowRange> = emptyList(),
    val fontId: String = FontCatalog.ID_DEFAULT,
    val fontSizeSp: Float = 16f,
    val textColorArgb: Long = 0xFF000000L,
    val bgColorArgb: Long? = null,
    val shadowColorArgb: Long = 0x80000000L,
    val shadowOffsetXPx: Float = 0f,
    val shadowOffsetYPx: Float = 0f,
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


private fun applyLinkRange(
    existing: List<LinkRange>,
    start: Int,
    end: Int,
    url: String,
    textLength: Int
): List<LinkRange> {
    if (start >= end || url.isBlank()) return existing
    val s = start.coerceIn(0, textLength)
    val e = end.coerceIn(0, textLength)
    if (s >= e) return existing
    val result = mutableListOf<LinkRange>()
    for (lr in existing) {
        val a = lr.range.first
        val b = lr.range.last
        if (b < s || a >= e) {
            result.add(lr)
        } else {
            if (a < s) result.add(LinkRange(a until s, lr.url))
            if (b >= e) result.add(LinkRange(e..b, lr.url))
        }
    }
    result.add(LinkRange(s until e, url.trim()))
    return result.filter { it.range.first <= it.range.last }
}

private fun adjustLinkRangesForEdit(
    oldText: String,
    newText: String,
    ranges: List<LinkRange>
): List<LinkRange> {
    if (ranges.isEmpty()) return ranges
    var prefix = 0
    val minLen = minOf(oldText.length, newText.length)
    while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (
        suffix < (minLen - prefix) &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++
    val oldEditEnd = oldText.length - suffix
    val newEditEnd = newText.length - suffix
    val delta = newEditEnd - oldEditEnd
    val out = mutableListOf<LinkRange>()
    for (lr in ranges) {
        var a = lr.range.first
        var b = lr.range.last
        if (b < prefix) {
            out.add(lr)
        } else if (a >= oldEditEnd) {
            out.add(LinkRange((a + delta)..(b + delta), lr.url))
        } else if (a >= prefix && b < oldEditEnd) {
            // range fully inside edit — drop
        } else {
            if (a < prefix) a = a
            else a = newEditEnd
            if (b >= oldEditEnd) b = b + delta
            else b = newEditEnd - 1
            if (a <= b) out.add(LinkRange(a..b, lr.url))
        }
    }
    return out
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
    /** Grid/fallback ratio. Not changed while Resize Images targets selected images. */
    var layoutCellAspectRatio: Float by mutableStateOf(1.192929f)
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

    var pinnedTextId: String? by mutableStateOf(null)
        private set
    var pinnedSelection: TextRange by mutableStateOf(TextRange.Zero)
        private set

    fun pinTextSelectionIfAny() {
        val id = focusedTextId ?: selectedTextId ?: return
        if (!currentSelection.collapsed) {
            pinnedTextId = id
            pinnedSelection = currentSelection
        }
    }

    fun selectionForEdit(): TextRange {
        if (!currentSelection.collapsed) return currentSelection
        val id = focusedTextId ?: selectedTextId ?: pinnedTextId
        if (id != null && id == pinnedTextId && !pinnedSelection.collapsed) return pinnedSelection
        return currentSelection
    }

    fun hasTextRangeForEdit(): Boolean {
        val id = focusedTextId ?: selectedTextId ?: pinnedTextId
        return id != null && !selectionForEdit().collapsed
    }

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
        focusedTextId = null
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
            val adjustedLinks = if (newText != current.text) {
                adjustLinkRangesForEdit(current.text, newText, current.linkRanges)
            } else {
                current.linkRanges
            }
            val adjustedShadows = if (newText != current.text) {
                adjustShadowRangesForEdit(current.text, newText, current.shadowRanges)
            } else {
                current.shadowRanges
            }
            val textChanged = newText != current.text
            textElements[index] = current.copy(
                text = newText,
                boldRanges = adjustedBold,
                italicRanges = adjustedItalic,
                colorRanges = adjustedColors,
                linkRanges = adjustedLinks,
                bgColorRanges = adjustedBgColors,
                shadowRanges = adjustedShadows
            )
            if (!newSelection.collapsed) {
                currentSelection = newSelection
                pinnedTextId = id
                pinnedSelection = newSelection
            } else if (textChanged) {
                currentSelection = newSelection
                pinnedTextId = null
                pinnedSelection = TextRange.Zero
            }
        }
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
        val selection = selectionForEdit()
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
        val selection = selectionForEdit()
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
        val selection = selectionForEdit()
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
        val selection = selectionForEdit()
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
                imageCellAspectRatio = imageLengthPercentToRatio(15).also { layoutCellAspectRatio = it }
            }
            "landscape" -> {
                // 16:9 — Page Size 11, Per Row 3, Spacing 6, Image Length 84
                pageAspectRatio = pageSizePercentToRatio(11)
                pageAspectOverrides.clear()
                imagesPerRow = 3
                imageSpacingDp = 6
                imageCellAspectRatio = imageLengthPercentToRatio(84).also { layoutCellAspectRatio = it }
            }
            "square" -> {
                // 1:1 — Page Size 13, Per Row 4, Spacing 6, Image Length 53
                pageAspectRatio = pageSizePercentToRatio(13)
                pageAspectOverrides.clear()
                imagesPerRow = 4
                imageSpacingDp = 6
                imageCellAspectRatio = imageLengthPercentToRatio(53).also { layoutCellAspectRatio = it }
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
        val ids = dragSelectedIds.toList()
        for (id in ids) {
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
        val dx = 0.5f - cx
        val dy = 0.5f - cy
        for (id in dragSelectedIds.toList()) {
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val img = importedImages[idx]
            importedImages[idx] = img.copy(
                dragOffsetXFrac = (img.dragOffsetXFrac + dx).coerceIn(-0.92f, 0.92f),
                dragOffsetYFrac = (img.dragOffsetYFrac + dy).coerceIn(-0.92f, 0.92f)
            )
        }
    }

    fun setDragGroupPositionPercent(xPercent: Float?, yPercent: Float?) {
        if (dragSelectedIds.isEmpty()) return
        val (cx, cy) = groupDragCenter()
        val tx = if (xPercent != null) xPercent.coerceIn(0f, 100f) / 100f else cx
        val ty = if (yPercent != null) yPercent.coerceIn(0f, 100f) / 100f else cy
        val dx = tx - cx
        val dy = ty - cy
        for (id in dragSelectedIds.toList()) {
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val img = importedImages[idx]
            importedImages[idx] = img.copy(
                dragOffsetXFrac = (img.dragOffsetXFrac + dx).coerceIn(-0.92f, 0.92f),
                dragOffsetYFrac = (img.dragOffsetYFrac + dy).coerceIn(-0.92f, 0.92f)
            )
        }
    }

    fun updateImagesPerRow(count: Int) {
        imagesPerRow = count.coerceIn(1, 20)
    }

    fun updateImageSpacing(dp: Int) {
        imageSpacingDp = dp.coerceIn(0, 20)
    }

    var resizeEditActive: Boolean by mutableStateOf(false)
    private val resizeTargetIds: SnapshotStateList<String> = mutableStateListOf()

    fun beginResizeEdit(ids: Collection<String>) {
        resizeTargetIds.clear()
        resizeTargetIds.addAll(ids.filter { !it.startsWith("spacer_") })
        val first = resizeTargetIds.firstOrNull()?.let { id ->
            importedImages.firstOrNull { it.id == id }
        }
        imageCellAspectRatio = (first?.aspectRatioOverride ?: layoutCellAspectRatio).coerceIn(0.3f, 2.0f)
        resizeEditActive = resizeTargetIds.isNotEmpty()
    }

    fun exitResizeEdit() {
        resizeEditActive = false
        resizeTargetIds.clear()
    }

    fun updateImageCellAspectRatio(ratio: Float) {
        val r = ratio.coerceIn(0.3f, 2.0f)
        imageCellAspectRatio = r
        if (resizeTargetIds.isNotEmpty()) {
            resizeTargetIds.forEach { id ->
                val idx = importedImages.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    importedImages[idx] = importedImages[idx].copy(aspectRatioOverride = r)
                }
            }
        } else {
            layoutCellAspectRatio = r
        }
    }

    var cornersEditActive: Boolean by mutableStateOf(false)
    private val cornerTargetIds: SnapshotStateList<String> = mutableStateListOf()

    fun beginCornerEdit(ids: Collection<String>) {
        cornerTargetIds.clear()
        cornerTargetIds.addAll(ids.filter { !it.startsWith("spacer_") })
        val first = cornerTargetIds.firstOrNull()?.let { id ->
            importedImages.firstOrNull { it.id == id }
        }
        imageCornerRadiusPercent = first?.cornerRadiusPercent?.coerceIn(0, 100) ?: 0
        cornersEditActive = cornerTargetIds.isNotEmpty()
    }

    fun exitCornerEdit() {
        cornersEditActive = false
        cornerTargetIds.clear()
    }

    fun updateImageCornerRadiusPercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        imageCornerRadiusPercent = p
        cornerTargetIds.forEach { id ->
            val idx = importedImages.indexOfFirst { it.id == id }
            if (idx >= 0) {
                importedImages[idx] = importedImages[idx].copy(cornerRadiusPercent = p)
            }
        }
    }

    fun importImages(uris: List<Uri>, replaceId: String? = null, startPageIndex: Int = 0) {
        val targetBytes = selectedImageSizeOption.targetBytes
        isImporting = true
        pendingReplaceImageId = null

        val imageIds = mutableListOf<String>()

        if (replaceId != null) {
            // Replace one existing image in-place (no reflow).
            val idx = importedImages.indexOfFirst { it.id == replaceId }
            val placeAt = if (idx >= 0) {
                importedImages.removeAt(idx)
                idx
            } else {
                importedImages.size
            }
            uris.forEachIndexed { index, uri ->
                val imageId = "" + uri + "_" + System.currentTimeMillis() + "_" + index
                if (index == 0) {
                    importedImages.add(placeAt, ImportedImage(id = imageId, imageUri = uri))
                } else {
                    // Extra uris after a replace: fill first available slots after this one
                    placeNewImageAtFirstEmpty(imageId, uri, placeAt + index)
                }
                imageIds.add(imageId)
            }
        } else {
            // Fill first available empty slots starting from selected page.
            // Empty = spacer_ slot, or append past current list (next free cell / page).
            // Never insert at the front in a way that shifts existing real images.
            val perPage = imagesPerPageCapacity().coerceAtLeast(1)
            val pageIdx = startPageIndex.coerceAtLeast(0)
            val startSlot = pageIdx * perPage
            minPageCount = maxOf(minPageCount, pageIdx + 1)
            while (importedImages.size < startSlot) {
                val spacerId = "spacer_" + System.nanoTime() + "_" + importedImages.size
                importedImages.add(
                    ImportedImage(id = spacerId, imageUri = null, bitmap = null)
                )
            }
            var cursor = startSlot
            uris.forEachIndexed { index, uri ->
                val imageId = "" + uri + "_" + System.currentTimeMillis() + "_" + index
                cursor = placeNewImageAtFirstEmpty(imageId, uri, cursor)
                imageIds.add(imageId)
            }
        }

        viewModelScope.launch {
            uris.forEachIndexed { index, uri ->
                if (index >= imageIds.size) return@forEachIndexed
                val imageId = imageIds[index]
                val decoded = imageHandler.decode(uri)
                val imageIndex = importedImages.indexOfFirst { it.id == imageId }

                if (decoded != null && imageIndex >= 0) {
                    if (targetBytes == null) {
                        importedImages[imageIndex] = importedImages[imageIndex].copy(bitmap = decoded)
                    } else {
                        val result = withContext(Dispatchers.Default) {
                            imageCompressor.compressToTarget(decoded, targetBytes)
                        }
                        importedImages[imageIndex] = importedImages[imageIndex].copy(
                            bitmap = result.bitmap,
                            approxSizeBytes = result.approxSizeBytes
                        )
                    }
                }
            }
            isImporting = false
        }
    }

    /**
     * Place a new image at the first empty slot at or after [fromIndex].
     * Empty = spacer_ entry (replace in place) or end of list (append).
     * Returns the next index to continue searching from.
     */
    private fun placeNewImageAtFirstEmpty(imageId: String, uri: Uri, fromIndex: Int): Int {
        val start = fromIndex.coerceAtLeast(0)
        for (i in start until importedImages.size) {
            if (importedImages[i].id.startsWith("spacer_")) {
                importedImages[i] = ImportedImage(id = imageId, imageUri = uri)
                return i + 1
            }
        }
        importedImages.add(ImportedImage(id = imageId, imageUri = uri))
        return importedImages.size
    }

    fun openImageMenu(id: String) {
        // Ignore empty spacer slots (used only to offset import start page)
        if (id.startsWith("spacer_")) return
        if (imagePositionMode != 0 && imagePositionSourceId != null) {
            val src = imagePositionSourceId!!
            if (id != src) {
                when (imagePositionMode) {
                    1 -> moveSingleImageTo(src, id)
                    2 -> swapImages(src, id)
                }
            }
            imagePositionMode = 0
            imagePositionSourceId = null
            singleMenuImageId = null
            return
        }
        // Multi-select via long-press removed
        selectionMode = false
        selectedImageIds.clear()
        multipleActionsVisible = false
        singleMenuImageId = if (singleMenuImageId == id) null else id
    }

    fun dismissImageMenu() {
        singleMenuImageId = null
    }

    fun longPressImage(id: String) {
        // Long-press multi-select removed — intentionally no-op
    }

    private fun toggleSelection(id: String) {
        if (selectedImageIds.contains(id)) {
            selectedImageIds.remove(id)
        } else {
            selectedImageIds.add(id)
        }
    }

    
    fun replaceImageBitmap(id: String, newBitmap: android.graphics.Bitmap, sizeBytes: Int? = null) {
        val idx = importedImages.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = importedImages[idx]
        val bytes = sizeBytes ?: run {
            try {
                val stream = java.io.ByteArrayOutputStream()
                newBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                stream.size()
            } catch (_: Exception) {
                if (!newBitmap.isRecycled) newBitmap.byteCount else old.approxSizeBytes
            }
        }
        importedImages[idx] = old.copy(
            bitmap = newBitmap,
            approxSizeBytes = bytes,
            imageUri = null
        )
    }

    fun rotateImageBitmap(id: String, degrees: Float): Boolean {
        val img = getImage(id) ?: return false
        val src = img.bitmap ?: return false
        if (src.isRecycled) return false
        if (degrees % 360f == 0f) return true
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated = android.graphics.Bitmap.createBitmap(
            src, 0, 0, src.width, src.height, matrix, true
        )
        replaceImageBitmap(id, rotated)
        return true
    }

    fun cropImageBitmap(
        id: String,
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float
    ): Boolean {
        val img = getImage(id) ?: return false
        val src = img.bitmap ?: return false
        if (src.isRecycled) return false
        val l = (leftFrac.coerceIn(0f, 1f) * src.width).toInt().coerceIn(0, src.width - 1)
        val t = (topFrac.coerceIn(0f, 1f) * src.height).toInt().coerceIn(0, src.height - 1)
        val r = (rightFrac.coerceIn(0f, 1f) * src.width).toInt().coerceIn(l + 1, src.width)
        val b = (bottomFrac.coerceIn(0f, 1f) * src.height).toInt().coerceIn(t + 1, src.height)
        val w = r - l
        val h = b - t
        if (w < 2 || h < 2) return false
        val cropped = android.graphics.Bitmap.createBitmap(src, l, t, w, h)
        replaceImageBitmap(id, cropped)
        return true
    }

    
    fun setImageLinkUrl(id: String, url: String?) {
        val idx = importedImages.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cleaned = url?.trim()?.takeIf { it.isNotEmpty() }
        val normalized = cleaned?.let {
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it
            else "https://$it"
        }
        val old = importedImages[idx]
        importedImages[idx] = old.copy(linkUrl = normalized)
        singleMenuImageId = null
    }

    
    val pageNumberingSelection = mutableStateListOf<Int>()
    var numberingEditMode: Boolean by mutableStateOf(false)
    var numberingAlpha: Float by mutableStateOf(0.9f)
    var numberingSizeFrac: Float by mutableStateOf(0.18f)
    var numberingXFrac: Float by mutableStateOf(0.5f)
    var numberingYFrac: Float by mutableStateOf(0.5f)
    var numberingBgArgb: Long by mutableStateOf(0xFF7C4DFFL)
    var numberingFgArgb: Long by mutableStateOf(0xFFFFFFFFL)
    var numberingWeight: Float by mutableStateOf(0.67f)
    /** true = style screen (colors + thickness) before position edit */
    var numberingStyleScreen: Boolean by mutableStateOf(false)
    /** 0 = transparency/size panel, 1 = move panel */
    var numberingEditStep: Int by mutableStateOf(0)

    init {
        // restore last applied numbering style as defaults
        numberingBgArgb = imageSizePreferences.getNumberingBg()
        numberingFgArgb = imageSizePreferences.getNumberingFg()
        numberingWeight = imageSizePreferences.getNumberingWeight()
        numberingAlpha = imageSizePreferences.getNumberingAlpha()
        numberingSizeFrac = imageSizePreferences.getNumberingSize()
    }

    fun clearPageNumberingSelection() { pageNumberingSelection.clear() }

    fun togglePageNumberingSelection(pageIndex: Int) {
        if (pageNumberingSelection.contains(pageIndex)) pageNumberingSelection.remove(pageIndex)
        else pageNumberingSelection.add(pageIndex)
    }

    fun toggleSelectAllPagesForNumbering(pageCount: Int) {
        val count = pageCount.coerceAtLeast(1)
        val all = pageNumberingSelection.size >= count && (0 until count).all { it in pageNumberingSelection }
        pageNumberingSelection.clear()
        if (!all) pageNumberingSelection.addAll(0 until count)
    }

    fun startNumberingEdit(imagesPerPage: Int) {
        val selected = pageNumberingSelection.filter { it >= 0 }.toSortedSet()
        if (selected.isEmpty()) return
        var n = 1
        val total = documentPageCount()
        for (p in selected) {
            if (p !in 0 until total) continue
            for (i in imageRangeForPage(p, total)) {
                val img = importedImages[i]
                importedImages[i] = img.copy(
                    numberLabel = n++,
                    numberXFrac = numberingXFrac,
                    numberYFrac = numberingYFrac,
                    numberSizeFrac = numberingSizeFrac,
                    numberAlpha = numberingAlpha,
                    numberBgArgb = numberingBgArgb,
                    numberFgArgb = numberingFgArgb,
                    numberWeight = numberingWeight
                )
            }
        }
        numberingEditMode = true
        numberingEditStep = 0
    }

    fun updateNumberingLiveStyle() {
        for (i in importedImages.indices) {
            val img = importedImages[i]
            if (img.numberLabel != null) {
                importedImages[i] = img.copy(
                    numberXFrac = numberingXFrac,
                    numberYFrac = numberingYFrac,
                    numberSizeFrac = numberingSizeFrac,
                    numberAlpha = numberingAlpha,
                    numberBgArgb = numberingBgArgb,
                    numberFgArgb = numberingFgArgb,
                    numberWeight = numberingWeight
                )
            }
        }
    }

    fun nudgeNumbering(dx: Float, dy: Float) {
        // Small fractional step so hold-to-repeat feels smooth (same idea as Drag Images).
        val step = 0.0025f
        numberingXFrac = (numberingXFrac + dx * step).coerceIn(0f, 1f)
        numberingYFrac = (numberingYFrac + dy * step).coerceIn(0f, 1f)
        updateNumberingLiveStyle()
    }

    fun centerNumbering() {
        numberingXFrac = 0.5f
        numberingYFrac = 0.5f
        updateNumberingLiveStyle()
    }

    
    fun openNumberingStyleScreen() {
        if (pageNumberingSelection.isEmpty()) return
        numberingStyleScreen = true
    }

    fun confirmNumberingStyleAndEdit(imagesPerPage: Int) {
        numberingStyleScreen = false
        startNumberingEdit(imagesPerPage)
    }

    fun cancelNumberingStyleScreen() {
        numberingStyleScreen = false
    }

    fun finishNumberingEdit() {
        updateNumberingLiveStyle()
        imageSizePreferences.saveNumberingStyle(
            numberingBgArgb, numberingFgArgb, numberingWeight, numberingAlpha, numberingSizeFrac
        )
        numberingEditMode = false
        numberingStyleScreen = false
        numberingEditStep = 0
        pageNumberingSelection.clear()
    }

    fun backNumberingEditToStyle() {
        updateNumberingLiveStyle()
        numberingEditMode = false
        numberingEditStep = 0
        numberingStyleScreen = true
    }

    fun advanceNumberingEditStep() {
        numberingEditStep = 1
    }

    fun backNumberingEditStep() {
        numberingEditStep = 0
    }

    fun getImage(id: String): ImportedImage? =
        importedImages.firstOrNull { it.id == id }

    fun enterSingleReorder(id: String) {
        singleMenuImageId = null
        selectedImageIds.clear()
        selectedImageIds.add(id)
        reorderMode = true
    }

    fun cutSingle(id: String) {
        val image = importedImages.firstOrNull { it.id == id }
        if (image != null) {
            clipboardImages = listOf(image)
            importedImages.remove(image)
        }
        singleMenuImageId = null
    }

    fun copySingle(id: String) {
        val image = importedImages.firstOrNull { it.id == id }
        if (image != null) {
            clipboardImages = listOf(image)
        }
        singleMenuImageId = null
    }

    fun pasteImages() {
        if (clipboardImages.isNotEmpty()) {
            importedImages.addAll(clipboardImages)
        }
        singleMenuImageId = null
    }

    fun deleteSingle(id: String) {
        deleteImages(listOf(id), keepSpace = false)
    }

    fun deleteSingleKeepSpace(id: String) {
        deleteImages(listOf(id), keepSpace = true)
    }

    fun deleteSingleFillSpace(id: String) {
        deleteImages(listOf(id), keepSpace = false)
    }

    fun deleteImages(ids: Collection<String>, keepSpace: Boolean) {
        val idSet = ids.filter { it.isNotBlank() && !it.startsWith("spacer_") }.toSet()
        if (idSet.isEmpty()) {
            singleMenuImageId = null
            return
        }
        if (keepSpace) {
            for (i in importedImages.indices) {
                val img = importedImages[i]
                if (img.id in idSet) {
                    importedImages[i] = ImportedImage(
                        id = "spacer_" + System.nanoTime() + "_" + i,
                        imageUri = null,
                        bitmap = null
                    )
                }
            }
        } else {
            importedImages.removeAll { it.id in idSet }
        }
        selectedImageIds.removeAll { it in idSet }
        if (singleMenuImageId in idSet) singleMenuImageId = null
        dragSelectedIds.removeAll { it in idSet }
    }

    fun finishMultipleSelection() {
        multipleActionsVisible = true
    }

    fun closeMultipleActions() {
        multipleActionsVisible = false
    }

    fun getSelectedImages(): List<ImportedImage> =
        importedImages.filter { it.id in selectedImageIds }

    fun cancelSelection() {
        selectionMode = false
        selectedImageIds.clear()
        multipleActionsVisible = false
    }

    fun cutSelected() {
        clipboardImages = getSelectedImages()
        importedImages.removeAll { it.id in selectedImageIds }
        multipleActionsVisible = false
        cancelSelection()
    }

    fun copySelected() {
        clipboardImages = getSelectedImages()
        multipleActionsVisible = false
        cancelSelection()
    }

    fun deleteSelected() {
        importedImages.removeAll { it.id in selectedImageIds }
        multipleActionsVisible = false
        cancelSelection()
    }

    
    fun startReplaceImage(id: String) {
        pendingReplaceImageId = id
        singleMenuImageId = null
    }

    fun startImageMove(id: String) {
        imagePositionSourceId = id
        imagePositionMode = 1
        singleMenuImageId = null
    }

    fun startImageSwap(id: String) {
        imagePositionSourceId = id
        imagePositionMode = 2
        singleMenuImageId = null
    }

    fun cancelImagePositionMode() {
        imagePositionMode = 0
        imagePositionSourceId = null
    }

    fun swapImages(idA: String, idB: String) {
        if (idA == idB) return
        val i = importedImages.indexOfFirst { it.id == idA }
        val j = importedImages.indexOfFirst { it.id == idB }
        if (i < 0 || j < 0) return
        val tmp = importedImages[i]
        importedImages[i] = importedImages[j]
        importedImages[j] = tmp
    }

    /**
     * Move [sourceId] to [targetId]'s original list position (insertion,
     * not replacement). The destination image is kept — it (and every
     * image between source and destination) shifts over by one slot.
     * Source is not duplicated. Works across pages (flat image list order).
     */
    fun moveSingleImageTo(sourceId: String, targetId: String) {
        if (sourceId == targetId) return

        val sourceIndex = importedImages.indexOfFirst { it.id == sourceId }
        if (sourceIndex < 0) return

        // Capture the destination's index BEFORE removing the source —
        // it must not be recalculated after removal.
        val targetIndex = importedImages.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return

        // Remove ONLY the source. The destination is never touched/removed.
        val sourceItem = importedImages.removeAt(sourceIndex)

        // Insert the source at the destination's original index. This
        // correctly places it in that exact slot and shifts everything
        // between source and destination by one, in either direction.
        val insertAt = targetIndex.coerceIn(0, importedImages.size)
        importedImages.add(insertAt, sourceItem)
    }

    fun moveSelectedImagesTo(targetId: String) {
        val idsToMove = selectedImageIds.toList()
        if (idsToMove.isEmpty() || targetId in idsToMove) return

        val itemsToMove = importedImages.filter { it.id in idsToMove }
        importedImages.removeAll { it.id in idsToMove }

        val targetIndex = importedImages.indexOfFirst { it.id == targetId }
        val insertAt = if (targetIndex < 0) importedImages.size else targetIndex
        importedImages.addAll(insertAt, itemsToMove)
    }

    fun finishReorder() {
        reorderMode = false
        cancelSelection()
    }

    // ---- Page Tools actions ----

    fun addNewPage() {
        minPageCount = minPageCount + 1
    }

    fun deleteLastPage() {
        if (minPageCount <= 1) return
        minPageCount = minPageCount - 1
    }

    fun clearPageDeleteSelection() {
        pageDeleteSelection.clear()
    }

    fun togglePageDeleteSelection(pageIndex: Int) {
        if (pageDeleteSelection.contains(pageIndex)) {
            pageDeleteSelection.remove(pageIndex)
        } else {
            pageDeleteSelection.add(pageIndex)
        }
    }

    fun toggleSelectAllPagesForDelete(pageCount: Int) {
        val count = pageCount.coerceAtLeast(1)
        val allSelected = pageDeleteSelection.size >= count &&
            (0 until count).all { it in pageDeleteSelection }
        if (allSelected) {
            pageDeleteSelection.clear()
        } else {
            pageDeleteSelection.clear()
            pageDeleteSelection.addAll(0 until count)
        }
    }

    
    fun clearPageDuplicateSelection() {
        pageDuplicateSelection.clear()
    }

    fun togglePageDuplicateSelection(pageIndex: Int) {
        if (pageDuplicateSelection.contains(pageIndex)) {
            pageDuplicateSelection.remove(pageIndex)
        } else {
            pageDuplicateSelection.add(pageIndex)
        }
    }

    fun toggleSelectAllPagesForDuplicate(pageCount: Int) {
        val count = pageCount.coerceAtLeast(1)
        val allSelected = pageDuplicateSelection.size >= count &&
            (0 until count).all { it in pageDuplicateSelection }
        if (allSelected) {
            pageDuplicateSelection.clear()
        } else {
            pageDuplicateSelection.clear()
            pageDuplicateSelection.addAll(0 until count)
        }
    }

    fun duplicateSelectedPages(imagesPerPage: Int) {
        val total = documentPageCount()
        val selected = pageDuplicateSelection.filter { it in 0 until total }.sortedDescending()
        if (selected.isEmpty()) return

        for (pageIdx in selected) {
            val range = imageRangeForPage(pageIdx, total)
            val slice = if (!range.isEmpty()) {
                importedImages.subList(range.first, range.last + 1).toList()
            } else {
                emptyList()
            }
            val start = if (!range.isEmpty()) range.first else 0
            val end = if (!range.isEmpty()) range.last + 1 else 0
            val copies = slice.map { img ->
                img.copy(id = java.util.UUID.randomUUID().toString())
            }
            importedImages.addAll(end, copies)

            // Shift text on pages after this page, then copy texts on this page
            val textsOnPage = textElements.filter { it.pageIndex == pageIdx }
            for (i in textElements.indices.reversed()) {
                val te = textElements[i]
                if (te.pageIndex > pageIdx) {
                    textElements[i] = te.copy(pageIndex = te.pageIndex + 1)
                }
            }
            textsOnPage.forEach { te ->
                textElements.add(
                    te.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        pageIndex = pageIdx + 1
                    )
                )
            }

            // Shift overrides for indices > pageIdx, then copy override at pageIdx to pageIdx+1
            fun shiftFloat(map: MutableMap<Int, Float>) {
                val entries = map.toList().sortedByDescending { it.first }
                map.clear()
                entries.forEach { (k, v) ->
                    when {
                        k > pageIdx -> map[k + 1] = v
                        k == pageIdx -> {
                            map[k] = v
                            map[k + 1] = v
                        }
                        else -> map[k] = v
                    }
                }
            }
            fun shiftLong(map: MutableMap<Int, Long>) {
                val entries = map.toList().sortedByDescending { it.first }
                map.clear()
                entries.forEach { (k, v) ->
                    when {
                        k > pageIdx -> map[k + 1] = v
                        k == pageIdx -> {
                            map[k] = v
                            map[k + 1] = v
                        }
                        else -> map[k] = v
                    }
                }
            }
            fun shiftBmp(map: MutableMap<Int, android.graphics.Bitmap>) {
                val entries = map.toList().sortedByDescending { it.first }
                map.clear()
                entries.forEach { (k, v) ->
                    when {
                        k > pageIdx -> map[k + 1] = v
                        k == pageIdx -> {
                            map[k] = v
                            map[k + 1] = v
                        }
                        else -> map[k] = v
                    }
                }
            }
            shiftFloat(pageAspectOverrides)
            shiftLong(pageBackgroundColorOverrides)
            shiftBmp(pageBackgroundBitmapOverrides)

            minPageCount = minPageCount + 1
        }
        pageDuplicateSelection.clear()
    }

    
    /**
     * Move page [fromIndex] so it lands at [toIndex].
     * All page content (images, text, links, backgrounds) moves with the page.
     */
    /**
     * Move source page so it lands exactly at [toIndex].
     * Example (5 pages, source Page1 → dest Page4):
     *   before: 1, 2, 3, 4, 5
     *   after:  2, 3, 4, 1, 5
     * i.e. page1 content → position 4; pages 2–4 shift one step toward the old source.
     * Content (images, text, links, backgrounds) moves with each page via reorderPages.
     */
    fun movePageTo(fromIndex: Int, toIndex: Int, imagesPerPage: Int) {
        val total = documentPageCount()
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until total || toIndex !in 0 until total) return

        val order = (0 until total).toMutableList()
        val item = order.removeAt(fromIndex)
        // Insert at destination index in the list AFTER removal so final index == toIndex.
        // Do NOT use toIndex-1 when from < to — that placed the page one slot early.
        order.add(toIndex.coerceIn(0, order.size), item)
        reorderPages(order, imagesPerPage)
    }

    /**
     * Reorder pages by [newOrder] where newOrder[newPos] = oldPos.
     * Each page is treated as a fixed-capacity slot block so empty pages stay empty
     * and images/text/links/backgrounds move with their page.
     */
    fun reorderPages(newOrder: List<Int>, imagesPerPage: Int) {
        val total = documentPageCount()
        if (newOrder.size != total || newOrder.toSet() != (0 until total).toSet()) return
        val perPage = imagesPerPage.coerceAtLeast(1)

        // Snapshot every page as exactly [perPage] slots (pad with spacers).
        // This keeps empty pages and mid-page gaps (Keep Space) intact across moves.
        val pageSlots: List<List<ImportedImage>> = (0 until total).map { p ->
            val start = p * perPage
            List(perPage) { slot ->
                val idx = start + slot
                if (idx < importedImages.size) {
                    importedImages[idx]
                } else {
                    ImportedImage(
                        id = "spacer_" + System.nanoTime() + "_" + p + "_" + slot,
                        imageUri = null,
                        bitmap = null
                    )
                }
            }
        }

        val rebuilt = newOrder.flatMap { oldPos -> pageSlots[oldPos] }
        importedImages.clear()
        importedImages.addAll(rebuilt)

        // newOrder[newPos] = oldPos  =>  old page index maps to new page index
        val oldToNew = newOrder.mapIndexed { newPos, oldPos -> oldPos to newPos }.toMap()

        // Text elements (and their links/styles) follow their page
        val newTexts = textElements.map { te ->
            te.copy(pageIndex = oldToNew[te.pageIndex] ?: te.pageIndex)
        }
        textElements.clear()
        textElements.addAll(newTexts)

        fun remapFloat(map: MutableMap<Int, Float>) {
            val old = map.toMap()
            map.clear()
            old.forEach { (k, v) ->
                val nk = oldToNew[k]
                if (nk != null) map[nk] = v
            }
        }
        fun remapLong(map: MutableMap<Int, Long>) {
            val old = map.toMap()
            map.clear()
            old.forEach { (k, v) ->
                val nk = oldToNew[k]
                if (nk != null) map[nk] = v
            }
        }
        fun remapBmp(map: MutableMap<Int, android.graphics.Bitmap>) {
            val old = map.toMap()
            map.clear()
            old.forEach { (k, v) ->
                val nk = oldToNew[k]
                if (nk != null) map[nk] = v
            }
        }
        remapFloat(pageAspectOverrides)
        remapLong(pageBackgroundColorOverrides)
        remapBmp(pageBackgroundBitmapOverrides)

        // Keep document page count stable
        minPageCount = maxOf(minPageCount, total)
    }

    fun deleteSelectedPages(imagesPerPage: Int) {
        val total = documentPageCount()
        val toDelete = pageDeleteSelection.filter { it in 0 until total }.toSortedSet()
        if (toDelete.isEmpty()) return

        if (toDelete.size >= total) {
            importedImages.clear()
            textElements.clear()
            minPageCount = 1
            pageAspectOverrides.clear()
            pageBackgroundColorOverrides.clear()
            pageBackgroundBitmapOverrides.clear()
            pageDeleteSelection.clear()
            return
        }

        // Remove images that sit on deleted document pages
        val keptImages = (0 until total)
            .filter { it !in toDelete }
            .flatMap { p ->
                val range = imageRangeForPage(p, total)
                if (range.isEmpty()) emptyList()
                else importedImages.subList(range.first, range.last + 1).toList()
            }
        importedImages.clear()
        importedImages.addAll(keptImages)

        // Text: drop deleted pages, shift later indices down
        val keptText = textElements
            .filter { it.pageIndex !in toDelete }
            .map { te ->
                val shift = toDelete.count { it < te.pageIndex }
                if (shift > 0) te.copy(pageIndex = te.pageIndex - shift) else te
            }
        textElements.clear()
        textElements.addAll(keptText)

        // Reindex float overrides
        run {
            val old = pageAspectOverrides.toMap()
            pageAspectOverrides.clear()
            old.forEach { (k, v) ->
                if (k in toDelete) return@forEach
                pageAspectOverrides[k - toDelete.count { it < k }] = v
            }
        }
        run {
            val old = pageBackgroundColorOverrides.toMap()
            pageBackgroundColorOverrides.clear()
            old.forEach { (k, v) ->
                if (k in toDelete) return@forEach
                pageBackgroundColorOverrides[k - toDelete.count { it < k }] = v
            }
        }
        run {
            val old = pageBackgroundBitmapOverrides.toMap()
            pageBackgroundBitmapOverrides.clear()
            old.forEach { (k, v) ->
                if (k !in toDelete) {
                    pageBackgroundBitmapOverrides[k - toDelete.count { it < k }] = v
                }
            }
        }

        minPageCount = maxOf(1, minPageCount - toDelete.size)
        pageDeleteSelection.clear()
    }


    fun aspectRatioForPage(pageIndex: Int): Float {
        return pageAspectOverrides[pageIndex] ?: pageAspectRatio
    }

    /**
     * How many images fit on one page — SAME math as PdfPagesPreview / PdfGenerator.
     * Uses page aspect, margin, spacing, cell aspect, imagesPerRow.
     */
    fun imagesPerPageCapacity(
        imagesPerRowOverride: Int = imagesPerRow,
        pageAspect: Float = pageAspectRatio,
        spacingDp: Int = imageSpacingDp,
        cellAspect: Float = imageCellAspectRatio,
        marginDp: Int = pageMarginDp
    ): Int {
        val pageWidthDp = 360f
        val aspect = pageAspect.coerceAtLeast(0.1f)
        val pageHeightDp = pageWidthDp / aspect
        val pad = marginDp.toFloat().coerceAtLeast(0f)
        val gridW = (pageWidthDp - pad * 2f).coerceAtLeast(1f)
        val gridH = (pageHeightDp - pad * 2f).coerceAtLeast(1f)
        val spacing = spacingDp.toFloat().coerceAtLeast(0f)
        val perRow = imagesPerRowOverride.coerceAtLeast(1)
        val cellW = (gridW - spacing * (perRow - 1)) / perRow
        val cellH = cellW / cellAspect.coerceAtLeast(0.1f)
        val rows = if (cellH > 0f) {
            (((gridH + spacing) / (cellH + spacing)).toInt()).coerceAtLeast(1)
        } else {
            1
        }
        return (perRow * rows).coerceAtLeast(1)
    }

    /**
     * Visible page count for pickers + tools:
     * max( pages needed for images at real capacity, text pages, minPageCount ).
     * Does NOT use the old wrong imagesPerRow*2 shortcut.
     */
    fun currentPageCountEstimate(imagesPerPage: Int = imagesPerPageCapacity()): Int {
        val perPage = imagesPerPage.coerceAtLeast(1)
        val imagePages = if (importedImages.isEmpty()) 0
            else (importedImages.size + perPage - 1) / perPage
        val textPages = (textElements.maxOfOrNull { it.pageIndex } ?: -1) + 1
        return maxOf(imagePages, textPages, minPageCount, 1)
    }

    fun documentPageCount(): Int = currentPageCountEstimate(imagesPerPageCapacity())

    /** Image indices on a layout page (fixed capacity slices, not even-split). */
    fun imageRangeForPage(
        pageIndex: Int,
        pageCount: Int = documentPageCount(),
        perPage: Int = imagesPerPageCapacity()
    ): IntRange {
        val n = importedImages.size
        val capacity = perPage.coerceAtLeast(1)
        if (n == 0 || pageIndex < 0 || pageIndex >= pageCount.coerceAtLeast(1)) {
            return IntRange.EMPTY
        }
        val start = pageIndex * capacity
        if (start >= n) return IntRange.EMPTY
        val end = minOf(start + capacity, n)
        return start until end
    }

    fun selectAllPagesForSize(pageCount: Int) {
        pageSizeSelection.clear()
        pageSizeSelection.addAll(0 until pageCount.coerceAtLeast(1))
    }

    /** First click selects all; second click clears all. */
    fun toggleSelectAllPagesForSize(pageCount: Int) {
        val count = pageCount.coerceAtLeast(1)
        val allSelected = pageSizeSelection.size >= count &&
            (0 until count).all { it in pageSizeSelection }
        if (allSelected) {
            pageSizeSelection.clear()
        } else {
            pageSizeSelection.clear()
            pageSizeSelection.addAll(0 until count)
        }
    }

    fun clearPageSizeSelection() {
        pageSizeSelection.clear()
    }

    fun togglePageSizeSelection(pageIndex: Int) {
        if (pageSizeSelection.contains(pageIndex)) {
            pageSizeSelection.remove(pageIndex)
        } else {
            pageSizeSelection.add(pageIndex)
        }
    }

    fun isPageSizeSelected(pageIndex: Int): Boolean =
        pageSizeSelection.contains(pageIndex)

    fun applyPageSizeToSelection(ratio: Float) {
        val r = ratio.coerceIn(0.4f, 2.5f)
        if (pageSizeSelection.isEmpty()) {
            // No selection → treat as global default for all pages
            pageAspectRatio = r
            pageAspectOverrides.clear()
        } else {
            pageSizeSelection.forEach { idx ->
                pageAspectOverrides[idx] = r
            }
        }
    }

    fun updatePageAspectRatio(ratio: Float) {
        val r = ratio.coerceIn(0.4f, 2.5f)
        pageAspectRatio = r
        isPageLandscape = r > 1f
    }

    enum class PageOrientation { PORTRAIT, LANDSCAPE, SQUARE }

    fun updatePageOrientation(orientation: PageOrientation) {
        val ratio = when (orientation) {
            PageOrientation.PORTRAIT -> 9f / 16f
            PageOrientation.LANDSCAPE -> 16f / 9f
            PageOrientation.SQUARE -> 1f
        }
        pageAspectRatio = ratio
        isPageLandscape = ratio > 1f
        // Set Page Size overrides must not block orientation
        pageAspectOverrides.clear()
        pageSizeSelection.clear()
    }

    /** Keep old Boolean API working if anything still calls it. */
    fun updatePageOrientation(landscape: Boolean) {
        updatePageOrientation(
            if (landscape) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT
        )
    }

    fun updatePageMarginDp(dp: Int) {
        pageMarginDp = dp.coerceIn(0, 48)
    }

    fun backgroundColorForPage(pageIndex: Int): Long {
        return pageBackgroundColorOverrides[pageIndex] ?: pageBackgroundColor
    }

    fun selectAllPagesForBgColor(pageCount: Int) {
        pageBgColorSelection.clear()
        pageBgColorSelection.addAll(0 until pageCount.coerceAtLeast(1))
    }

    fun togglePageBgColorSelection(pageIndex: Int) {
        if (pageBgColorSelection.contains(pageIndex)) {
            pageBgColorSelection.remove(pageIndex)
        } else {
            pageBgColorSelection.add(pageIndex)
        }
    }


    fun clearPageBgColorSelection() {
        pageBgColorSelection.clear()
    }

    fun toggleSelectAllPagesForBgColor(pageCount: Int) {
        val count = pageCount.coerceAtLeast(1)
        val allSelected = pageBgColorSelection.size >= count &&
            (0 until count).all { it in pageBgColorSelection }
        if (allSelected) {
            pageBgColorSelection.clear()
        } else {
            pageBgColorSelection.clear()
            pageBgColorSelection.addAll(0 until count)
        }
    }

    fun applyBackgroundColorToSelection(colorArgb: Long) {
        if (pageBgColorSelection.isEmpty()) {
            pageBackgroundColor = colorArgb
            pageBackgroundColorOverrides.clear()
            imageSizePreferences.savePageBackgroundColor(colorArgb)
            // solid color replaces image on global path
            pageBackgroundImageUri = null
            pageBackgroundBitmap = null
            pageBackgroundBitmapOverrides.clear()
        } else {
            pageBgColorSelection.forEach { idx ->
                pageBackgroundColorOverrides[idx] = colorArgb
                pageBackgroundBitmapOverrides.remove(idx)
            }
        }
    }

    fun updatePageBackgroundColor(colorArgb: Long) {
        pageBackgroundColor = colorArgb
        imageSizePreferences.savePageBackgroundColor(colorArgb)
    }

    fun setPageBackgroundFromUri(uri: Uri?) {
        if (uri == null) {
            clearPageBackgroundImage()
            return
        }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                imageHandler.decode(uri, maxDimensionPx = 1600)
            } ?: return@launch
            if (pageBgColorSelection.isEmpty()) {
                pageBackgroundImageUri = uri
                pageBackgroundBitmap = bmp
                pageBackgroundBitmapOverrides.clear()
            } else {
                pageBgColorSelection.forEach { idx ->
                    pageBackgroundBitmapOverrides[idx] = bmp
                    pageBackgroundColorOverrides.remove(idx)
                }
            }
        }
    }

    fun clearPageBackgroundImage() {
        if (pageBgColorSelection.isEmpty()) {
            pageBackgroundImageUri = null
            pageBackgroundBitmap = null
            pageBackgroundBitmapOverrides.clear()
        } else {
            pageBgColorSelection.forEach { idx ->
                pageBackgroundBitmapOverrides.remove(idx)
            }
        }
    }

    fun backgroundBitmapForPage(pageIndex: Int): Bitmap? {
        return pageBackgroundBitmapOverrides[pageIndex] ?: pageBackgroundBitmap
    }



    fun updatePageNumberPosition(pos: PageNumberPosition) {
        pageNumberPosition = pos
    }

    fun updatePageNumberStyle(style: PageNumberStyle) {
        pageNumberStyle = style
    }

    fun formatPageNumber(pageIndexZeroBased: Int): String {
        val n = pageIndexZeroBased + 1
        return when (pageNumberStyle) {
            PageNumberStyle.ARABIC -> n.toString()
            PageNumberStyle.ROMAN_LOWER -> toRoman(n).lowercase()
            PageNumberStyle.ROMAN_UPPER -> toRoman(n)
            PageNumberStyle.ALPHA_LOWER -> toAlpha(n).lowercase()
            PageNumberStyle.ALPHA_UPPER -> toAlpha(n)
        }
    }

    private fun toRoman(num: Int): String {
        if (num <= 0) return ""
        val values = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = listOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var n = num
        val sb = StringBuilder()
        for (i in values.indices) {
            while (n >= values[i]) {
                sb.append(symbols[i])
                n -= values[i]
            }
        }
        return sb.toString()
    }

    private fun toAlpha(num: Int): String {
        var n = num
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.insert(0, ('A' + (n % 26)).toChar())
            n /= 26
        }
        return sb.toString()
    }

    fun updateSelectedTextSize(sizeSp: Float) {
        val id = focusedTextId ?: selectedTextId ?: return
        val index = textElements.indexOfFirst { it.id == id }
        if (index < 0) return
        val clamped = sizeSp.coerceIn(8f, 72f)
        textElements[index] = textElements[index].copy(fontSizeSp = clamped)
    }

    fun selectedTextSizeSp(): Float {
        val id = focusedTextId ?: selectedTextId ?: return 16f
        return textElements.firstOrNull { it.id == id }?.fontSizeSp ?: 16f
    }

    fun applyLinkToSelection(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        val index = activeTextIndex()
        if (index < 0) return false
        val element = textElements[index]
        val selection = selectionForEdit()
        if (selection.collapsed) return false
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)
        if (start >= end) return false
        val linkBlue = 0xFF1976D2L
        val newLinks = applyLinkRange(element.linkRanges, start, end, clean, element.text.length)
        val newColors = applyColorRange(element.colorRanges, start, end, linkBlue, element.text.length)
        textElements[index] = element.copy(linkRanges = newLinks, colorRanges = newColors)
        return true
    }

    fun updateSelectedTextColor(colorArgb: Long) {
        val index = activeTextIndex()
        if (index < 0) return
        val element = textElements[index]
        val selection = selectionForEdit()
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)

        if (!selection.collapsed && start < end) {
            // Apply color only to the selected range (same behavior as Bold)
            val newRanges = applyColorRange(
                element.colorRanges,
                start,
                end,
                colorArgb,
                element.text.length
            )
            textElements[index] = element.copy(colorRanges = newRanges)
        } else {
            // No selection → apply to entire text element
            textElements[index] = element.copy(
                textColorArgb = colorArgb,
                colorRanges = emptyList()
            )
        }
    }

    fun updateSelectedTextBgColor(colorArgb: Long?) {
        val index = activeTextIndex()
        if (index < 0) return
        val element = textElements[index]
        if (colorArgb == null) {
            textElements[index] = element.copy(bgColorArgb = null, bgColorRanges = emptyList())
            return
        }
        val selection = selectionForEdit()
        val start = selection.min.coerceIn(0, element.text.length)
        val end = selection.max.coerceIn(0, element.text.length)

        if (!selection.collapsed && start < end) {
            val newRanges = applyColorRange(
                element.bgColorRanges,
                start,
                end,
                colorArgb,
                element.text.length
            )
            textElements[index] = element.copy(bgColorRanges = newRanges)
        } else {
            textElements[index] = element.copy(
                bgColorArgb = colorArgb,
                bgColorRanges = emptyList()
            )
        }
    }

    private fun applyShadowRange(
        existing: List<ShadowRange>, start: Int, end: Int,
        colorArgb: Long, offsetXPx: Float, offsetYPx: Float, blurPx: Float, textLength: Int
    ): List<ShadowRange> {
        if (textLength <= 0 || start >= end) return existing
        val s = start.coerceIn(0, textLength)
        val e = end.coerceIn(0, textLength)
        if (s >= e) return existing
        val result = mutableListOf<ShadowRange>()
        for (sr in existing) {
            val rs = sr.range.first
            val re = sr.range.last + 1
            if (re <= s || rs >= e) result.add(sr)
            else {
                if (rs < s) result.add(sr.copy(range = rs until s))
                if (re > e) result.add(sr.copy(range = e until re))
            }
        }
        result.add(ShadowRange(s until e, colorArgb, offsetXPx, offsetYPx, blurPx))
        return result.sortedBy { it.range.first }
    }

    private fun adjustShadowRangesForEdit(oldText: String, newText: String, ranges: List<ShadowRange>): List<ShadowRange> {
        return ranges.mapNotNull { sr ->
            val mapped = adjustColorRangesForEdit(oldText, newText, listOf(ColorRange(sr.range, 0L)))
            val r = mapped.firstOrNull()?.range ?: return@mapNotNull null
            sr.copy(range = r)
        }
    }

    fun updateSelectedTextShadow(
        colorArgb: Long? = null,
        offsetXPx: Float? = null,
        offsetYPx: Float? = null,
        blurPx: Float? = null
    ) {
        val index = activeTextIndex()
        if (index < 0) return
        val cur = textElements[index]
        val selection = selectionForEdit()
        val color = colorArgb ?: cur.shadowColorArgb
        val ox = offsetXPx ?: cur.shadowOffsetXPx
        val oy = offsetYPx ?: cur.shadowOffsetYPx
        val blur = blurPx ?: cur.shadowBlurPx
        val start = selection.min.coerceIn(0, cur.text.length)
        val end = selection.max.coerceIn(0, cur.text.length)
        if (!selection.collapsed && start < end) {
            textElements[index] = cur.copy(
                shadowRanges = applyShadowRange(cur.shadowRanges, start, end, color, ox, oy, blur, cur.text.length)
            )
        }
    }

    fun activeTextElement(): TextElement? {
        val id = focusedTextId ?: selectedTextId ?: return null
        return textElements.firstOrNull { it.id == id }
    }

    fun selectedTextColorArgb(): Long {
        val index = activeTextIndex()
        if (index < 0) return 0xFF000000L
        val element = textElements[index]
        val selection = selectionForEdit()
        if (!selection.collapsed) {
            val start = selection.min.coerceIn(0, element.text.length)
            val end = selection.max.coerceIn(0, element.text.length)
            if (start < end) {
                val colorsInSel = element.colorRanges
                    .filter { it.range.first < end && it.range.last + 1 > start }
                    .map { it.colorArgb }
                    .distinct()
                if (colorsInSel.size == 1) return colorsInSel[0]
            }
        }
        return element.textColorArgb
    }

    fun selectedTextBgColorArgb(): Long? {
        val index = activeTextIndex()
        if (index < 0) return null
        val element = textElements[index]
        val selection = selectionForEdit()
        if (!selection.collapsed) {
            val start = selection.min.coerceIn(0, element.text.length)
            val end = selection.max.coerceIn(0, element.text.length)
            if (start < end) {
                val colorsInSel = element.bgColorRanges
                    .filter { it.range.first < end && it.range.last + 1 > start }
                    .map { it.colorArgb }
                    .distinct()
                if (colorsInSel.size == 1) return colorsInSel[0]
            }
        }
        return element.bgColorArgb
    }
}
