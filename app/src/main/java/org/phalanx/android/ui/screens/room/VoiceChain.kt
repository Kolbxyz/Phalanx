package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry

/** Lets a voice message that finished hand over to the next one, as a listener would expect. */
@Stable
internal class VoiceChain(private val nextAfter: (entryId: String) -> String?) {
    /** The entry that should start playing, until it has picked the request up. */
    var pending by mutableStateOf<String?>(null)

    fun ended(entryId: String) {
        pending = nextAfter(entryId)
    }
}

internal val LocalVoiceChain = compositionLocalOf<VoiceChain?> { null }

/** The voice message right after [entryId] in time, when that is the very next message. */
internal fun nextVoiceAfter(entries: List<TimelineEntry>, entryId: String): String? {
    // Newest first, so the following message sits before this one.
    val next = entries.getOrNull(entries.indexOfFirst { it.id == entryId } - 1) as? TimelineEntry.Message
    return next?.takeIf { (it.body as? MessageBody.Audio)?.isVoice == true }?.id
}

/** The next message may be off screen, and a row that is not composed cannot start playing. */
@Composable
internal fun VoiceChainScroll(
    chain: VoiceChain,
    listState: LazyListState,
    entries: List<TimelineEntry>,
    reducedMotion: Boolean
) {
    val pending = chain.pending
    LaunchedEffect(pending) {
        val index = entries.indexOfFirst { it.id == pending }
        if (pending == null || index < 0) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            if (reducedMotion) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        }
    }
}
