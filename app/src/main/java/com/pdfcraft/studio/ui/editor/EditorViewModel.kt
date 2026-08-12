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

data class PdfPage(
    val id: String,
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val approxSizeBytes: Int? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageHandler = ImageHandler(application.contentResolver)
    private val imageCompressor = ImageCompressor()

    val pages: SnapshotStateList<PdfPage> = mutableStateListOf()

    var selectedImageSizeOption: ImageSizeOption by mutableStateOf(ImageSizeOption.Default)
        private set

    fun selectImageSizeOption(option: ImageSizeOption) {
        selectedImageSizeOption = option
    }

    fun importImages(uris: List<Uri>) {
        val targetBytes = selectedImageSizeOption.targetBytes

        uris.forEachIndexed { index, uri ->
            val pageId = "${uri}_${pages.size + index}"
            pages.add(PdfPage(id = pageId, imageUri = uri))

            viewModelScope.launch {
                val decoded = imageHandler.decode(uri) ?: return@launch

                val pageIndex = pages.indexOfFirst { it.id == pageId }
                if (pageIndex < 0) return@launch

                if (targetBytes == null) {
                    pages[pageIndex] = pages[pageIndex].copy(bitmap = decoded)
                } else {
                    val result = imageCompressor.compressToTarget(decoded, targetBytes)
                    pages[pageIndex] = pages[pageIndex].copy(
                        bitmap = result.bitmap,
                        approxSizeBytes = result.approxSizeBytes
                    )
                }
            }
        }
    }
}
