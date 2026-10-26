package org.phalanx.android.core.ai.photos

import javax.inject.Inject
import javax.inject.Singleton
import org.phalanx.android.core.ai.AiDisk
import org.phalanx.android.core.ai.vision.PhotoDescriber
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.PhotoRef
import org.phalanx.android.data.repository.RecentPhotosRepository

/**
 * Describes the photos of joined rooms that are not described yet, newest first, and keeps what it
 * finds sealed on [AiDisk]. Run by [PhotoIndexWorker] while the phone charges and sits idle.
 */
@Singleton
class PhotoIndexer @Inject internal constructor(
    private val photos: RecentPhotosRepository,
    private val media: MediaRepository,
    private val describer: PhotoDescriber,
    private val disk: AiDisk
) {
    /** Describes up to [BATCH] new photos; returns how many it described. */
    suspend fun indexSome(): Int {
        val known = disk.readAll(disk.photos).keys
        val next = photos.recent(PER_ROOM).filter { it.eventId !in known }.sortedByDescending { it.sentAtMillis }
        var described = 0
        for (photo in next.take(BATCH)) {
            if (describe(photo)) described++
        }
        return described
    }

    // A photo that cannot be fetched or read is tried again next run; the rest carry on.
    private suspend fun describe(photo: PhotoRef): Boolean {
        val image = photo.image
        val file = media.openable(image.media, image.filename, image.mimeType).getOrNull()
        val words = file?.let { describer.describe(it).getOrNull() }
        if (words != null) {
            val note = PhotoNote(
                roomId = photo.roomId,
                description = words.description,
                text = words.text,
                sentAtMillis = photo.sentAtMillis,
                thumbnail = (image.thumbnail ?: image.media).serialized,
                mimeType = image.mimeType
            )
            disk.keep(disk.photos, photo.eventId, note)
        }
        return words != null
    }

    private companion object {
        const val PER_ROOM = 30

        // About ten seconds a photo: a few minutes of a night's charge.
        const val BATCH = 40
    }
}
