package org.phalanx.android.ui.screens.account

import android.content.ActivityNotFoundException
import android.content.ClipData
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/**
 * A cookie avatar with the name and Matrix ID under it, centred like a profile card. Tapping the
 * avatar picks a new photo; the small camera badge says so.
 */
@Composable
internal fun ProfileHeader(
    profile: AccountProfile,
    avatar: ImageBitmap?,
    busy: Boolean,
    failed: Boolean,
    onPickPhoto: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val changeLabel = stringResource(R.string.account_photo_change)
        val interactions = remember { MutableInteractionSource() }
        val cookie = MaterialShapes.Cookie9Sided.toShape()
        // The whole box takes the tap, badge included, but only the cookie ripples: clipping the box
        // would cut the badge that sits over its corner.
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = !busy,
                    onClickLabel = changeLabel,
                    onClick = onPickPhoto
                )
        ) {
            InitialAvatar(
                name = profile.label,
                shape = cookie,
                size = 112.dp,
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                image = avatar,
                textStyle = MaterialTheme.typography.displaySmall,
                modifier = Modifier.clip(cookie).indication(interactions, ripple())
            )
            if (busy) LoadingIndicator(Modifier.align(Alignment.Center).size(48.dp))
            Surface(
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Symbols.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (failed) {
            Text(
                text = stringResource(R.string.account_photo_failed),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(profile.label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (profile.hasDisplayName) {
            Text(
                text = profile.userId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A value worth pasting into a support thread or a verification prompt; tapping copies it. */
@Composable
internal fun CopyRow(label: String, value: String, index: Int, count: Int) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SegmentedRow(
        headline = label,
        supporting = value,
        shape = segmentedShape(index, count),
        interaction = Modifier.clickable(onClickLabel = stringResource(R.string.account_copy)) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) }
        }
    )
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
