package com.pdfcraft.studio.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small hand-built vector icons used instead of androidx.compose.material.icons.extended,
 * which is intentionally NOT a dependency here (it added ~9MB to the release APK for a
 * handful of icons). Add new icons here only when material-icons-core doesn't have them.
 */
object AppIcons {

    val FileDocument: ImageVector by lazy {
        ImageVector.Builder(
            name = "FileDocument",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(6f, 2f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(4f, 20f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
            horizontalLineTo(18f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            lineToRelative(-6f, -6f)
            horizontalLineTo(6f)
            close()
            moveToRelative(7f, 7f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
        }.build()
    }
}
