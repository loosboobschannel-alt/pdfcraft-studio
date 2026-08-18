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
    val approxSizeBytes: Int? = null
)

data class TextElement(
    val id: String,
    val pageIndex: Int,
    val text: String = "",
    val xFraction: Float = 0.1f,
    val yFraction: Float = 0.1f,
    val boldRanges: List<IntRange> = emptyList(),
    val italicRanges: List<IntRange> = emptyList(),
    val fontId: String = FontCatalog.ID_DEFAULT,
    val fontSizeSp: Float = 16f,
    val textColorArgb: Long = 0xFF000000,
    val bgColorArgb: Long? = null,
    val shadowColorArgb: Long = 0x80000000,
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

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageHandler = ImageHandler(application.contentResolver)
    private val imageCompressor = ImageCompressor()
    private val imageSizePreferences = ImageSizePreferences(application)

    val importedImages: SnapshotStateList<ImportedImage> = mutableStateListOf()

    var selectedImageSizeOption: ImageSizeOption by mutableStateOf(imageSizePreferences.getSavedOption())
        private set

    var imagesPerRow: Int by mutableStateOf(3)
        private set

    var imageSpacingDp: Int by mutableStateOf(6)
        private set

    var imageCellAspectRatio: Float by mutableStateOf(0.526f)
        private set

    var imageCornerRadiusPercent: Int by mutableStateOf(0)
        private set

    // ---- Page Tools state ----
    var pageAspectRatio: Float by mutableStateOf(9f / 16f)
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

    var pageBackgroundImageUri: Uri? by mutableStateOf(null)
        private set

    var pageBackgroundBitmap: Bitmap? by mutableStateOf(null)
    val pageBackgroundBitmapOverrides = mutableStateMapOf<Int, Bitmap>()
        private set

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
            textElements[index] = current.copy(
                text = newText,
                boldRanges = adjustedBold,
                italicRanges = adjustedItalic
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

    fun updateImagesPerRow(count: Int) {
        imagesPerRow = count.coerceIn(1, 20)
    }

    fun updateImageSpacing(dp: Int) {
        imageSpacingDp = dp.coerceIn(0, 20)
    }

    fun updateImageCellAspectRatio(ratio: Float) {
        imageCellAspectRatio = ratio.coerceIn(0.4f, 2.5f)
    }

    fun updateImageCornerRadiusPercent(percent: Int) {
        imageCornerRadiusPercent = percent.coerceIn(0, 100)
    }

    fun importImages(uris: List<Uri>) {
        val targetBytes = selectedImageSizeOption.targetBytes
        isImporting = true

        val imageIds = uris.mapIndexed { index, uri ->
            val imageId = "" + uri + "_" + (importedImages.size + index)
            importedImages.add(ImportedImage(id = imageId, imageUri = uri))
            imageId
        }

        viewModelScope.launch {
            uris.forEachIndexed { index, uri ->
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

    fun openImageMenu(id: String) {
        if (selectionMode) {
            toggleSelection(id)
        } else {
            // Toggle: same image click closes the menu (Windows-style)
            singleMenuImageId = if (singleMenuImageId != null) null else id
        }
    }

    fun dismissImageMenu() {
        singleMenuImageId = null
    }

    fun longPressImage(id: String) {
        if (!selectionMode) {
            selectionMode = true
            selectedImageIds.clear()
            selectedImageIds.add(id)
        }
    }

    private fun toggleSelection(id: String) {
        if (selectedImageIds.contains(id)) {
            selectedImageIds.remove(id)
        } else {
            selectedImageIds.add(id)
        }
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
        importedImages.removeAll { it.id == id }
        singleMenuImageId = null
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

    fun moveSingleImageTo(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        val sourceIndex = importedImages.indexOfFirst { it.id == sourceId }
        if (sourceIndex < 0) return
        val item = importedImages.removeAt(sourceIndex)
        val targetIndex = importedImages.indexOfFirst { it.id == targetId }
        val insertAt = if (targetIndex < 0) importedImages.size else targetIndex
        importedImages.add(insertAt, item)
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

    fun aspectRatioForPage(pageIndex: Int): Float {
        return pageAspectOverrides[pageIndex] ?: pageAspectRatio
    }

    fun currentPageCountEstimate(imagesPerPage: Int): Int {
        val perPage = imagesPerPage.coerceAtLeast(1)
        val imagePages = if (importedImages.isEmpty()) 0
            else (importedImages.size + perPage - 1) / perPage
        val textPages = (textElements.maxOfOrNull { it.pageIndex } ?: -1) + 1
        return maxOf(imagePages, textPages, minPageCount, 1)
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

    fun updatePageOrientation(landscape: Boolean) {
        isPageLandscape = landscape
        val magnitude = if (pageAspectRatio >= 1f) pageAspectRatio else 1f / pageAspectRatio
        pageAspectRatio = if (landscape) magnitude else 1f / magnitude
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

    fun updateSelectedTextColor(colorArgb: Long) {
        val id = focusedTextId ?: selectedTextId ?: return
        val index = textElements.indexOfFirst { it.id == id }
        if (index < 0) return
        textElements[index] = textElements[index].copy(textColorArgb = colorArgb)
    }

    fun updateSelectedTextBgColor(colorArgb: Long?) {
        val id = focusedTextId ?: selectedTextId ?: return
        val index = textElements.indexOfFirst { it.id == id }
        if (index < 0) return
        textElements[index] = textElements[index].copy(bgColorArgb = colorArgb)
    }

    fun updateSelectedTextShadow(colorArgb: Long? = null, offsetPx: Float? = null, blurPx: Float? = null) {
        val id = focusedTextId ?: selectedTextId ?: return
        val index = textElements.indexOfFirst { it.id == id }
        if (index < 0) return
        val cur = textElements[index]
        textElements[index] = cur.copy(
            shadowColorArgb = colorArgb ?: cur.shadowColorArgb,
            shadowOffsetPx = offsetPx ?: cur.shadowOffsetPx,
            shadowBlurPx = blurPx ?: cur.shadowBlurPx
        )
    }

    fun selectedTextColorArgb(): Long {
        val id = focusedTextId ?: selectedTextId ?: return 0xFF000000
        return textElements.firstOrNull { it.id == id }?.textColorArgb ?: 0xFF000000
    }

    fun selectedTextBgColorArgb(): Long? {
        val id = focusedTextId ?: selectedTextId ?: return null
        return textElements.firstOrNull { it.id == id }?.bgColorArgb
    }
}
