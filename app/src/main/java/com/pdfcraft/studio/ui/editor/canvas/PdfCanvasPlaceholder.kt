package com.pdfcraft.studio.ui.editor.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.R
import com.pdfcraft.studio.ui.editor.ImportedImage

private const val PAGE_ASPECT_RATIO = 0.707f
private const val PAGE_INNER_PADDING_DP = 10f

@Composable
fun PdfPagesPreview(
    images: List<ImportedImage>,
    imagesPerRow: Int,
    imageSpacingDp: Int,
    imageCellAspectRatio: Float,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        EmptyStatePage(modifier = modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val pageWidthDp = maxWidth.value
        val pageHeightDp = pageWidthDp / PAGE_ASPECT_RATIO

        val gridWidthDp = pageWidthDp - (PAGE_INNER_PADDING_DP * 2)
        val gridHeightDp = pageHeightDp - (PAGE_INNER_PADDING_DP * 2)
        val spacing = imageSpacingDp.toFloat()

        val cellWidthDp = (gridWidthDp - spacing * (imagesPerRow - 1)) / imagesPerRow
        val cellHeightDp = if (imageCellAspectRatio > 0f) cellWidthDp / imageCellAspectRatio else cellWidthDp

        val rowsPerPage = if (cellHeightDp > 0f) {
            (((gridHeightDp + spacing) / (cellHeightDp + spacing)).toInt()).coerceAtLeast(1)
        } else {
            1
        }

        val imagesPerPage = (imagesPerRow * rowsPerPage).coerceAtLeast(1)
        val pages = images.chunked(imagesPerPage)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(pages, key = { index, _ -> index }) { pageIndex, pageImages ->
                Column {
                    Text(
                        text = stringResource(R.string.page_label, pageIndex + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    PageCard {
                        ImageGrid(
                            images = pageImages,
                            imagesPerRow = imagesPerRow,
                            spacingDp = imageSpacingDp,
                            cellAspectRatio = imageCellAspectRatio
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<ImportedImage>,
    imagesPerRow: Int,
    spacingDp: Int,
    cellAspectRatio: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PAGE_INNER_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
    ) {
        images.chunked(imagesPerRow).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
            ) {
                rowImages.forEach { image ->
                    ImageCell(
                        image = image,
                        aspectRatio = cellAspectRatio,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(imagesPerRow - rowImages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageCell(image: ImportedImage, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (image.bitmap != null) {
            Image(
                bitmap = image.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyStatePage(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PageCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.height(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.editor_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun PageCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PAGE_ASPECT_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
