package com.pdfcraft.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfcraft.studio.core.image.ImageCompressor
import com.pdfcraft.studio.core.image.ImageHandler
import com.pdfcraft.studio.core.image.ImageSizeOption
import com.pdfcraft.studio.core.settings.ImageSizePreferences
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
    val text: String = "Text",
    val xFraction: Float = 0.1f,
    val yFraction: Float = 0.1f,
    val isBold: Boolean = false
)

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

    var editingTextId: String? by mutableStateOf(null)
        private set

    var movingTextId: String? by mutableStateOf(null)
        private set

    fun enterAddTextMode() {
        addTextMode = true
        selectedTextId = null
    }

    fun addTextAt(pageIndex: Int, xFraction: Float, yFraction: Float) {
        val id = "text_${System.currentTimeMillis()}_${textElements.size}"
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
        editingTextId = id
    }

    fun selectText(id: String) {
        selectedTextId = id
    }

    fun deselectText() {
        selectedTextId = null
    }

    fun openTextEditor(id: String) {
        selectedTextId = id
        editingTextId = id
    }

    fun closeTextEditor() {
        editingTextId = null
    }

    fun updateTextContent(id: String, newText: String) {
        val index = textElements.indexOfFirst { it.id == id }
        if (index >= 0) {
            textElements[index] = textElements[index].copy(text = newText)
        }
    }

    fun startMovingText(id: String) {
        selectedTextId = id
        movingTextId = id
    }

    fun finishMovingText() {
        movingTextId = null
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

    fun toggleBoldForSelectedText() {
        val id = selectedTextId ?: return
        val index = textElements.indexOfFirst { it.id == id }
        if (index >= 0) {
            textElements[index] = textElements[index].copy(
                isBold = !textElements[index].isBold
            )
        }
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
            val imageId = "${uri}_${importedImages.size + index}"
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
            singleMenuImageId = id
        }
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
}
