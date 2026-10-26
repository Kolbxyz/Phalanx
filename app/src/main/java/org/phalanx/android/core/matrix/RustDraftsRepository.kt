package org.phalanx.android.core.matrix

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.repository.DraftsRepository
import org.phalanx.android.di.ApplicationScope

/** Mirrors the drafts the SDK keeps in its store; [RustTimelineSession] reports each one it saves. */
@Singleton
internal class RustDraftsRepository @Inject constructor(
    private val active: ActiveClient,
    @ApplicationScope private val scope: CoroutineScope
) : DraftsRepository {
    private val mutableDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    override val drafts: StateFlow<Map<String, String>> = mutableDrafts.asStateFlow()

    private val lookedUp = ConcurrentHashMap.newKeySet<String>()

    init {
        // A new sync service means another account, or none: its drafts aren't this one's.
        scope.launch {
            active.syncService.collect {
                lookedUp.clear()
                mutableDrafts.value = emptyMap()
            }
        }
    }

    override fun lookUp(roomIds: Collection<String>) {
        val fresh = roomIds.filter(lookedUp::add)
        if (fresh.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            fresh.forEach { roomId ->
                val text = try {
                    active.room(roomId)?.use { room -> room.loadComposerDraft(threadRoot = null)?.use { it.plainText } }
                } catch (_: ClientException) {
                    null
                }
                if (!text.isNullOrBlank()) mutableDrafts.update { it + (roomId to text) }
            }
        }
    }

    /** Called as a room's draft is saved; blank text means it was cleared. */
    fun saved(roomId: String, text: String) {
        lookedUp += roomId
        mutableDrafts.update { if (text.isBlank()) it - roomId else it + (roomId to text) }
    }
}
