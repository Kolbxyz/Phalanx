package org.phalanx.android.ui.screens.account

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.rememberCopyState
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** A value worth pasting into a support thread or a verification prompt; tapping copies it, and says so. */
@Composable
internal fun CopyRow(label: String, value: String, icon: ImageVector, index: Int, count: Int) {
    val copy = rememberCopyState()
    SegmentedRow(
        headline = label,
        supporting = if (copy.copied) stringResource(R.string.copied) else value,
        shape = segmentedShape(index, count),
        leading = { IconTile(icon) },
        trailing = {
            Icon(
                if (copy.copied) Symbols.Check else Symbols.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        interaction = Modifier.clickable(onClickLabel = stringResource(R.string.account_copy)) {
            copy.copy(label, value)
        }
    )
}

/** A row's icon on a small tile, tinted as [container] says: error for what can't be taken back. */
@Composable
internal fun IconTile(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, contentColor = content) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun OpenBrowser(url: String?, onOpened: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
        } catch (_: ActivityNotFoundException) {
            // No browser at all: nothing else could show the page either.
        }
        onOpened()
    }
}
