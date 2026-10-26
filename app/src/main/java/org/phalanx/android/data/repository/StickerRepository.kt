package org.phalanx.android.data.repository

import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.Sticker
import org.phalanx.android.data.model.StickerPack

interface StickerRepository {
    /** The packs shipped with the app. */
    suspend fun packs(): List<StickerPack>

    /** Sends [sticker] to [roomId] as an `m.sticker` event; it shows once the server has it. */
    suspend fun send(roomId: String, sticker: Sticker): Result<Unit>

    /** Sends an Emoji Kitchen mix the same way, downloading its image first if not cached. */
    suspend fun sendKitchen(roomId: String, mix: KitchenMix): Result<Unit>
}
