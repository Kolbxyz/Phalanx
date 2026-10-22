package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.media.StickerCatalog
import org.phalanx.android.data.model.Sticker
import org.phalanx.android.data.model.StickerPack
import org.phalanx.android.data.repository.StickerRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads the sticker's image, then sends the `m.sticker` event through the SDK, which encrypts it
 * in encrypted rooms. Stickers are public bundled images, so the upload itself is not encrypted.
 * Bridges differ in what they do with `m.sticker`, so a bridged room gets a plain `m.image`, which
 * every bridge delivers.
 */
@Singleton
internal class RustStickerRepository @Inject constructor(
    private val active: ActiveClient,
    private val catalog: StickerCatalog
) : StickerRepository {
    override suspend fun packs(): List<StickerPack> = catalog.packs()

    override suspend fun send(roomId: String, sticker: Sticker): Result<Unit> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(TimelineUnavailableException())
        val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            room.use {
                val asImage = room.roomInfo().bridge() != null
                val bytes = catalog.read(sticker)
                val url = client.uploadMedia(StickerCatalog.MIME_TYPE, bytes, null)
                val type = if (asImage) IMAGE_EVENT_TYPE else STICKER_EVENT_TYPE
                room.sendRaw(type, contentOf(sticker, url, bytes.size, asImage))
            }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        } catch (e: RoomException) {
            Result.failure(e)
        }
    }

    private fun contentOf(sticker: Sticker, url: String, size: Int, asImage: Boolean): String = buildJsonObject {
        // An image's body is its file name, which bridges do not show as a caption.
        put("body", if (asImage) sticker.assetPath.substringAfterLast('/') else sticker.body)
        if (asImage) put("msgtype", "m.image")
        put("url", url)
        putJsonObject("info") {
            put("w", StickerCatalog.SIZE_PX)
            put("h", StickerCatalog.SIZE_PX)
            put("mimetype", StickerCatalog.MIME_TYPE)
            put("size", size)
        }
    }.toString()

    private companion object {
        const val STICKER_EVENT_TYPE = "m.sticker"
        const val IMAGE_EVENT_TYPE = "m.room.message"
    }
}
