package com.pdfcraft.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfcraft.studio.core.image.ImageHandler
import kotlinx.coroutines.launch

data class PdfPage(
    val id: String,
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageHandler = ImageHandler(application.contentResolver)

    val pages: SnapshotStateList<PdfPage> = mutableStateListOf()

    fun importImages(uris: List<Uri>) {
        uris.forEachIndexed { index, uri ->
            val pageId = "${uri}_${pages.size + index}"
            pages.add(PdfPage(id = pageId, imageUri = uri))

            viewModelScope.launch {
                val bitmap = imageHandler.decode(uri)
                val pageIndex = pages.indexOfFirst { it.id == pageId }
                if (pageIndex >= 0 && bitmap != null) {
                    pages[pageIndex] = pages[pageIndex].copy(bitmap = bitmap)
                }
            }
        }
    }
}
