package org.phalanx.android.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.phalanx.android.data.model.Sticker
import org.phalanx.android.data.model.StickerPack
import javax.inject.Inject
import javax.inject.Singleton

/** The sticker packs bundled in `assets/stickers`, listed by its `index.json`. */
@Singleton
class StickerCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile
    private var cached: List<StickerPack>? = null

    suspend fun packs(): List<StickerPack> = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open("$DIR/index.json").use { it.readBytes().decodeToString() }
        Json.decodeFromString<Index>(text).packs
            .map { pack -> StickerPack(pack.id, pack.stickers.map { Sticker(it.file, it.body) }) }
            .also { cached = it }
    }

    suspend fun read(sticker: Sticker): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open("$DIR/${sticker.assetPath}").use { it.readBytes() }
    }

    @Serializable
    private class Index(val packs: List<PackEntry>)

    @Serializable
    private class PackEntry(val id: String, val stickers: List<StickerEntry>)

    @Serializable
    private class StickerEntry(val file: String, val body: String)

    companion object {
        const val DIR = "stickers"
        const val MIME_TYPE = "image/webp"
        const val SIZE_PX = 512
    }
}
