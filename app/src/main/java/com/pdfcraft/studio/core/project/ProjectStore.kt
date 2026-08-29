package com.pdfcraft.studio.core.project

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.pdfcraft.studio.core.image.ImageSizeOption
import com.pdfcraft.studio.ui.editor.ColorRange
import com.pdfcraft.studio.ui.editor.EditorViewModel
import com.pdfcraft.studio.ui.editor.LinkRange
import com.pdfcraft.studio.ui.editor.ShadowRange
import com.pdfcraft.studio.ui.editor.TextElement
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectStore {

    const val PROJECT_VERSION = 1
    const val EXTENSION = ".pdfcraft"

    data class SaveResult(
        val success: Boolean,
        val displayName: String = "",
        val path: String = "",
        val error: String? = null
    )

    fun save(context: Context, vm: EditorViewModel, displayName: String = "Untitled Project"): SaveResult {
        return try {
            val dir = projectsDir(context)
            if (!dir.exists() && !dir.mkdirs()) {
                return SaveResult(false, error = "Could not create projects folder")
            }
            val safeBase = sanitizeFileName(displayName.ifBlank { "Untitled Project" })
            val outFile = uniqueFile(dir, safeBase)
            val json = JSONObject()
            json.put("projectVersion", PROJECT_VERSION)
            json.put("name", safeBase)
            json.put("savedAt", System.currentTimeMillis())

            val page = JSONObject()
            page.put("minPageCount", vm.minPageCount)
            page.put("documentPageCount", vm.documentPageCount())
            page.put("pageAspectRatio", vm.pageAspectRatio.toDouble())
            page.put("isPageLandscape", vm.isPageLandscape)
            page.put("pageMarginDp", vm.pageMarginDp)
            page.put("pageBackgroundColor", vm.pageBackgroundColor)
            page.put("pageNumberPosition", vm.pageNumberPosition.name)
            page.put("pageNumberStyle", vm.pageNumberStyle.name)
            page.put("pageBackgroundImageUri", vm.pageBackgroundImageUri?.toString())
            page.put("pageAspectOverrides", mapToJson(vm.pageAspectOverrides) { it.toDouble() })
            page.put("pageBackgroundColorOverrides", mapToJson(vm.pageBackgroundColorOverrides) { it })
            json.put("page", page)

            val layout = JSONObject()
            layout.put("imagesPerRow", vm.imagesPerRow)
            layout.put("imageSpacingDp", vm.imageSpacingDp)
            layout.put("imageCellAspectRatio", vm.imageCellAspectRatio.toDouble())
            layout.put("layoutCellAspectRatio", vm.layoutCellAspectRatio.toDouble())
            layout.put("imageCornerRadiusPercent", vm.imageCornerRadiusPercent)
            layout.put("selectedImageSizeOption", imageSizeToJson(vm.selectedImageSizeOption))
            json.put("layout", layout)

            val numbering = JSONObject()
            numbering.put("alpha", vm.numberingAlpha.toDouble())
            numbering.put("sizeFrac", vm.numberingSizeFrac.toDouble())
            numbering.put("xFrac", vm.numberingXFrac.toDouble())
            numbering.put("yFrac", vm.numberingYFrac.toDouble())
            numbering.put("bgArgb", vm.numberingBgArgb)
            numbering.put("fgArgb", vm.numberingFgArgb)
            numbering.put("weight", vm.numberingWeight.toDouble())
            json.put("numberingDefaults", numbering)

            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zip ->
                val imagesArr = JSONArray()
                vm.importedImages.toList().forEach { img ->
                    val obj = JSONObject()
                    obj.put("id", img.id)
                    obj.put("imageUri", img.imageUri?.toString())
                    obj.put("approxSizeBytes", img.approxSizeBytes ?: JSONObject.NULL)
                    obj.put("linkUrl", img.linkUrl)
                    obj.put("numberLabel", img.numberLabel ?: JSONObject.NULL)
                    obj.put("numberXFrac", img.numberXFrac.toDouble())
                    obj.put("numberYFrac", img.numberYFrac.toDouble())
                    obj.put("numberSizeFrac", img.numberSizeFrac.toDouble())
                    obj.put("numberAlpha", img.numberAlpha.toDouble())
                    obj.put("numberBgArgb", img.numberBgArgb)
                    obj.put("numberFgArgb", img.numberFgArgb)
                    obj.put("numberWeight", img.numberWeight.toDouble())
                    obj.put("dragOffsetXFrac", img.dragOffsetXFrac.toDouble())
                    obj.put("dragOffsetYFrac", img.dragOffsetYFrac.toDouble())
                    obj.put("cornerRadiusPercent", img.cornerRadiusPercent)
                    if (img.aspectRatioOverride != null) {
                        obj.put("aspectRatioOverride", img.aspectRatioOverride.toDouble())
                    } else {
                        obj.put("aspectRatioOverride", JSONObject.NULL)
                    }
                    obj.put("file", putBitmap(zip, "images/${img.id}.jpg", img.bitmap))
                    imagesArr.put(obj)
                }
                json.put("images", imagesArr)

                json.getJSONObject("page").put(
                    "backgroundFile",
                    putBitmap(zip, "backgrounds/default.jpg", vm.pageBackgroundBitmap)
                )
                val bgFiles = JSONObject()
                vm.pageBackgroundBitmapOverrides.toMap().forEach { (pageIndex, bmp) ->
                    val f = putBitmap(zip, "backgrounds/page_$pageIndex.jpg", bmp)
                    if (f != null) bgFiles.put(pageIndex.toString(), f)
                }
                json.getJSONObject("page").put("backgroundFiles", bgFiles)

                val usedFontIds = vm.textElements.map { it.fontId }.toSet()
                val fontsArr = JSONArray()
                vm.availableFonts.filter { it.isCustom && it.id in usedFontIds }.forEach { font ->
                    val src = font.filePath?.let { File(it) }
                    val zipPath = if (src != null && src.isFile) {
                        val dest = "fonts/${src.name}"
                        zip.putNextEntry(ZipEntry(dest))
                        src.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        dest
                    } else null
                    fontsArr.put(
                        JSONObject()
                            .put("id", font.id)
                            .put("displayName", font.displayName)
                            .put("file", zipPath)
                    )
                }
                json.put("customFonts", fontsArr)

                val textsArr = JSONArray()
                vm.textElements.toList().forEach { t -> textsArr.put(textToJson(t)) }
                json.put("texts", textsArr)

                zip.putNextEntry(ZipEntry("project.json"))
                zip.write(json.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            SaveResult(success = true, displayName = outFile.name, path = outFile.absolutePath)
        } catch (e: Exception) {
            SaveResult(success = false, error = e.message ?: "Save failed")
        }
    }

    fun projectsDir(context: Context): File {
        val ext = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val base = ext ?: context.filesDir
        return File(base, "PDFCraftStudio/Projects")
    }

    private fun uniqueFile(dir: File, base: String): File {
        var candidate = File(dir, "$base$EXTENSION")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$EXTENSION")
            n++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        return cleaned.ifBlank { "Untitled Project" }
    }

    private fun imageSizeToJson(opt: ImageSizeOption): JSONObject = when (opt) {
        is ImageSizeOption.Default -> JSONObject().put("type", "original")
        is ImageSizeOption.Preset -> JSONObject().put("type", "preset").put("kb", opt.kb)
        is ImageSizeOption.Custom -> JSONObject().put("type", "custom").put("kb", opt.kb)
    }

    private fun <T> mapToJson(map: Map<Int, T>, value: (T) -> Any): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), value(v)) }
        return obj
    }

    private fun textToJson(t: TextElement): JSONObject {
        val obj = JSONObject()
        obj.put("id", t.id)
        obj.put("pageIndex", t.pageIndex)
        obj.put("text", t.text)
        obj.put("xFraction", t.xFraction.toDouble())
        obj.put("yFraction", t.yFraction.toDouble())
        obj.put("fontId", t.fontId)
        obj.put("fontSizeSp", t.fontSizeSp.toDouble())
        obj.put("textColorArgb", t.textColorArgb)
        obj.put("bgColorArgb", t.bgColorArgb ?: JSONObject.NULL)
        obj.put("shadowColorArgb", t.shadowColorArgb)
        obj.put("shadowOffsetXPx", t.shadowOffsetXPx.toDouble())
        obj.put("shadowOffsetYPx", t.shadowOffsetYPx.toDouble())
        obj.put("shadowBlurPx", t.shadowBlurPx.toDouble())
        obj.put("boldRanges", rangesToJson(t.boldRanges))
        obj.put("italicRanges", rangesToJson(t.italicRanges))
        obj.put("colorRanges", colorRangesToJson(t.colorRanges))
        obj.put("bgColorRanges", colorRangesToJson(t.bgColorRanges))
        obj.put("linkRanges", linkRangesToJson(t.linkRanges))
        obj.put("shadowRanges", shadowRangesToJson(t.shadowRanges))
        return obj
    }

    private fun rangesToJson(ranges: List<IntRange>): JSONArray {
        val arr = JSONArray()
        ranges.forEach { r -> arr.put(JSONArray().put(r.first).put(r.last)) }
        return arr
    }

    private fun colorRangesToJson(ranges: List<ColorRange>): JSONArray {
        val arr = JSONArray()
        ranges.forEach { cr ->
            arr.put(JSONObject().put("start", cr.range.first).put("end", cr.range.last).put("colorArgb", cr.colorArgb))
        }
        return arr
    }

    private fun linkRangesToJson(ranges: List<LinkRange>): JSONArray {
        val arr = JSONArray()
        ranges.forEach { lr ->
            arr.put(JSONObject().put("start", lr.range.first).put("end", lr.range.last).put("url", lr.url))
        }
        return arr
    }

    private fun shadowRangesToJson(ranges: List<ShadowRange>): JSONArray {
        val arr = JSONArray()
        ranges.forEach { sr ->
            arr.put(
                JSONObject()
                    .put("start", sr.range.first)
                    .put("end", sr.range.last)
                    .put("colorArgb", sr.colorArgb)
                    .put("offsetXPx", sr.offsetXPx.toDouble())
                    .put("offsetYPx", sr.offsetYPx.toDouble())
                    .put("blurPx", sr.blurPx.toDouble())
            )
        }
        return arr
    }

    private fun putBitmap(zip: ZipOutputStream, zipPath: String, bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.isRecycled) return null
        val source = try {
            if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else bitmap
        } catch (_: Exception) {
            bitmap
        }
        val bytes = ByteArrayOutputStream()
        val ok = source.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
        if (source !== bitmap && !source.isRecycled) source.recycle()
        if (!ok || bytes.size() == 0) return null
        zip.putNextEntry(ZipEntry(zipPath))
        zip.write(bytes.toByteArray())
        zip.closeEntry()
        return zipPath
    }
}
