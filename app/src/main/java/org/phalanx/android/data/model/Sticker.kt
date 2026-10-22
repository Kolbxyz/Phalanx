package org.phalanx.android.data.model

/** A bundled sticker: [assetPath] is relative to the stickers folder, [body] is its text fallback. */
data class Sticker(val assetPath: String, val body: String)

/** [id] names the pack; the UI maps it to a title. */
data class StickerPack(val id: String, val stickers: List<Sticker>)
