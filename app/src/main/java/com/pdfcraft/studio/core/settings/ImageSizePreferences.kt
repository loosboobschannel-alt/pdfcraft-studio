package com.pdfcraft.studio.core.settings

import android.content.Context
import com.pdfcraft.studio.core.image.ImageSizeOption

class ImageSizePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedOption(): ImageSizeOption {
        val kb = prefs.getInt(KEY_KB, -1)
        if (kb <= 0) return ImageSizeOption.Default

        val isCustom = prefs.getBoolean(KEY_IS_CUSTOM, false)
        return if (isCustom) ImageSizeOption.Custom(kb) else ImageSizeOption.Preset(kb)
    }

    fun saveOption(option: ImageSizeOption) {
        prefs.edit().apply {
            when (option) {
                is ImageSizeOption.Default -> putInt(KEY_KB, -1)
                is ImageSizeOption.Preset -> {
                    putInt(KEY_KB, option.kb)
                    putBoolean(KEY_IS_CUSTOM, false)
                }
                is ImageSizeOption.Custom -> {
                    putInt(KEY_KB, option.kb)
                    putBoolean(KEY_IS_CUSTOM, true)
                }
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "pdfcraft_settings"
        const val KEY_KB = "selected_image_size_kb"
        const val KEY_IS_CUSTOM = "selected_image_size_is_custom"
    }
}
