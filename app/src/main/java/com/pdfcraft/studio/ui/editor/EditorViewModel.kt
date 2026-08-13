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
import kotlinx.coroutines.launch

data class ImportedImage(
    val id: String,
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val approxSizeBytes: Int? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageHandler = ImageHandler(application.contentResolver)
    private val imageCompressor = ImageCompressor()

    val importedImages: SnapshotStateList<ImportedImage> = mutableStateListOf()

    var selectedImageSizeOption: ImageSizeOption by mutableStateOf(ImageSizeOption.Preset(50))
        private set

    var imagesPerRow: Int by mutableStateOf(3)
        private set

    var imageSpacingDp: Int by mutableStateOf(6)
        private set

    var imageCellAspectRatio: Float by mutableStateOf(0.526f)
        private set

    var importTotalCount: Int by mutableStateOf(0)
        private set

    var importCompletedCount: Int by mutableStateOf(0)
        private set

    val isImporting: Boolean
        get() = importCompletedCount < importTotalCount

    fun selectImageSizeOption(option: ImageSizeOption) {
        selectedImageSizeOption = option
    }

    fun updateImagesPerRow(count: Int) {
        imagesPerRow = count.coerceIn(1, 20)
    }

    fun updateImageSpacing(dp: Int) {
        imageSpacingDp = dp.coerceIn(0, 40)
    }

    fun updateImageCellAspectRatio(ratio: Float) {
        imageCellAspectRatio = ratio.coerceIn(0.4f, 2.5f)
    }

    fun importImages(uris: List<Uri>) {
        val targetBytes = selectedImageSizeOption.targetBytes
        importTotalCount = uris.size
        importCompletedCount = 0

        uris.forEachIndexed { index, uri ->
            val imageId = "${uri}_${importedImages.size + index}"
            importedImages.add(ImportedImage(id = imageId, imageUri = uri))

            viewModelScope.launch {
                val decoded = imageHandler.decode(uri)
                if (decoded == null) {
                    importCompletedCount += 1
                    return@launch
                }

                val imageIndex = importedImages.indexOfFirst { it.id == imageId }
                if (imageIndex < 0) {
                    importCompletedCount += 1
                    return@launch
                }

                if (targetBytes == null) {
                    importedImages[imageIndex] = importedImages[imageIndex].copy(bitmap = decoded)
                } else {
                    val result = imageCompressor.compressToTarget(decoded, targetBytes)
                    importedImages[imageIndex] = importedImages[imageIndex].copy(
                        bitmap = result.bitmap,
                        approxSizeBytes = result.approxSizeBytes
                    )
                }

                importCompletedCount += 1
            }
        }
    }
}
