package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.phalanx.android.data.repository.RoomGallery
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import org.phalanx.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps one room's gallery open at a time: the details screen and the viewer it opens share it. */
@Singleton
internal class RustRoomGalleryRepository @Inject constructor(
    private val active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) : RoomGalleryRepository {
    private val lock = Mutex()
    private var openRoomId: String? = null
    private var gallery: RustRoomGallery? = null

    init {
        // A new sync service means another sign-in; the gallery belongs to the old client.
        scope.launch { active.syncService.collect { lock.withLock { release() } } }
    }

    override suspend fun open(roomId: String): Result<RoomGallery> = lock.withLock {
        gallery?.takeIf { openRoomId == roomId }?.let { return@withLock Result.success(it) }
        release()
        withContext(Dispatchers.IO) {
            try {
                val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
                val opened = RustRoomGallery.open(room)
                gallery = opened
                openRoomId = roomId
                Result.success(opened)
            } catch (e: ClientException) {
                Result.failure(TimelineUnavailableException(e))
            }
        }
    }

    override fun current(roomId: String): RoomGallery? = gallery?.takeIf { openRoomId == roomId }

    private fun release() {
        gallery?.close()
        gallery = null
        openRoomId = null
    }
}
