package com.pdfcraft.studio.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

class ImageCompressor {

    data class Result(val bitmap: Bitmap, val approxSizeBytes: Int)

    fun compressToTarget(original: Bitmap, targetBytes: Int): Result {
        val originalBytes = encode(original, quality = 100)
        if (originalBytes.size <= targetBytes) {
            return Result(original, originalBytes.size)
        }

        var bestBytes = encode(original, quality = 2)

        var low = 2
        var high = 100
        var iterations = 0

        while (low <= high && iterations < 8) {
            val mid = (low + high) / 2
            val candidate = encode(original, mid)
            if (candidate.size <= targetBytes) {
                bestBytes = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
            iterations++
        }

        val decoded = BitmapFactory.decodeByteArray(bestBytes, 0, bestBytes.size) ?: original
        return Result(decoded, bestBytes.size)
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
        return stream.toByteArray()
    }
}
