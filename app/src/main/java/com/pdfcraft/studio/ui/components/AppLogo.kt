package com.pdfcraft.studio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pdfcraft.studio.R

/**
 * The app icon, reused anywhere the brand mark is needed (home screen,
 * splash/about, empty states) so it never has to be redrawn per-screen.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = stringResource(R.string.content_desc_app_icon),
        modifier = modifier.size(size)
    )
}
