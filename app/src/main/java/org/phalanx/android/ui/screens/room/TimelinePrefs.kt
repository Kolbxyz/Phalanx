package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import org.phalanx.android.data.model.AppSettings

/** The settings that change how a conversation is drawn, read deep inside its bubbles. */
@Immutable
internal data class TimelinePrefs(val textScale: Float = 1f, val autoLoadMedia: Boolean = true)

internal fun AppSettings.timelinePrefs() = TimelinePrefs(messageTextSize.scale, autoLoadMedia)

internal val LocalTimelinePrefs = staticCompositionLocalOf { TimelinePrefs() }
