package org.phalanx.android.data.repository

import org.phalanx.android.data.model.KitchenMix

interface StickerRepository {
    /**
     * Sends an Emoji Kitchen mix to [roomId] as an `m.sticker` event, downloading its image first
     * if not cached; it shows once the server has it.
     */
    suspend fun send(roomId: String, mix: KitchenMix): Result<Unit>
}
