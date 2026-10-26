package org.phalanx.android.core.matrix

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.media.EmojiKitchen
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.repository.StickerRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads the mix's image, then sends the `m.sticker` event through the SDK, which encrypts it in
 * encrypted rooms. Mixes are public images, so the upload itself is not encrypted.
 * Bridges differ in what they do with `m.sticker`, so a bridged room gets a plain `m.image`, which
 * every bridge delivers.
 */
@Singleton
internal class RustStickerRepository @Inject constructor(
    private val active: ActiveClient,
    private val kitchen: EmojiKitchen
) : StickerRepository {
    override suspend fun send(roomId: String, mix: KitchenMix): Result<Unit> {
        val file = kitchen.image(mix).getOrElse { return Result.failure(it) }
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return sendImage(roomId, StickerImage(bytes, mix.body, file.name, bounds.outWidth))
    }

    private suspend fun sendImage(roomId: String, image: StickerImage): Result<Unit> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(TimelineUnavailableException())
        val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            room.use {
                val asImage = room.roomInfo().bridge() != null
                val url = client.uploadMedia(MIME_TYPE, image.bytes, null)
                val type = if (asImage) IMAGE_EVENT_TYPE else STICKER_EVENT_TYPE
                room.sendRaw(type, contentOf(image, url, asImage))
            }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        } catch (e: RoomException) {
            Result.failure(e)
        }
    }

    private fun contentOf(image: StickerImage, url: String, asImage: Boolean): String = buildJsonObject {
        // An image's body is its file name, which bridges do not show as a caption.
        put("body", if (asImage) image.fileName else image.body)
        if (asImage) put("msgtype", "m.image")
        put("url", url)
        putJsonObject("info") {
            put("w", image.sizePx)
            put("h", image.sizePx)
            put("mimetype", MIME_TYPE)
            put("size", image.bytes.size)
        }
    }.toString()

    private class StickerImage(val bytes: ByteArray, val body: String, val fileName: String, val sizePx: Int)

    private companion object {
        const val STICKER_EVENT_TYPE = "m.sticker"
        const val IMAGE_EVENT_TYPE = "m.room.message"
        const val MIME_TYPE = "image/png"
    }
}
