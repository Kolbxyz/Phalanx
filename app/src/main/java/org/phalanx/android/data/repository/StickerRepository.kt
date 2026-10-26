package org.phalanx.android.data.repository

import org.phalanx.android.data.model.KitchenMix

interface StickerRepository {
    /**
     * Sends an Emoji Kitchen mix to [roomId] as an `m.sticker` event, downloading its image first
     * if not cached; it shows once the server has it.
     */
    suspend fun send(roomId: String, mix: KitchenMix): Result<Unit>

    /**
     * Uploads the mix's image, unencrypted like a sticker's, and returns its `mxc://` URL: a
     * reaction with that URL as its key shows the mix where clients support image reactions.
     */
    suspend fun upload(mix: KitchenMix): Result<String>
}
