package org.phalanx.android.core.matrix

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Image packs (MSC2545), the custom emoji Matrix clients such as Element, Cinny and FluffyChat
 * share: the user's own in `im.ponies.user_emotes`, a room's in `im.ponies.room_emotes` state, and
 * the rooms whose packs the user turned on everywhere in `im.ponies.emote_rooms`.
 */
internal object ImagePacks {
    const val USER_PACK = "im.ponies.user_emotes"
    const val ROOM_PACK = "im.ponies.room_emotes"
    const val GLOBAL_PACKS = "im.ponies.emote_rooms"

    /** A pack's emoji as shortcode to `mxc://` URL, in the pack's order; stickers-only images left out. */
    fun emotesOf(json: String): List<Pair<String, String>> {
        val content = parse(json)
        val packUsage = content?.get("pack")?.objectOrNull()?.usage()
        val images = content?.get("images")?.objectOrNull() ?: return emptyList()
        return images.mapNotNull { (shortcode, value) ->
            val image = value.objectOrNull() ?: return@mapNotNull null
            val url = image["url"]?.stringOrNull()?.takeIf { it.startsWith("mxc://") } ?: return@mapNotNull null
            // An image's own usage wins over its pack's; with neither, it is both emoji and sticker.
            val usage = image.usage() ?: packUsage
            (shortcode to url).takeIf { SHORTCODE.matches(shortcode) && (usage == null || EMOTICON in usage) }
        }
    }

    /** The packs of `im.ponies.emote_rooms` as room ID to their state keys. */
    fun globalPacksOf(json: String): Map<String, Set<String>> {
        val rooms = parse(json)?.get("rooms")?.objectOrNull() ?: return emptyMap()
        return rooms.mapValues { (_, keys) -> keys.objectOrNull()?.keys.orEmpty() }
    }

    private fun parse(json: String): JsonObject? = try {
        Json.parseToJsonElement(json).objectOrNull()
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.usage(): List<String>? = try {
        get("usage")?.jsonArray?.mapNotNull { it.stringOrNull() }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.stringOrNull(): String? = try {
        jsonPrimitive.contentOrNull
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val EMOTICON = "emoticon"

    /** What can be typed between colons: what Discord, Slack and the packs use in practice. */
    val SHORTCODE = Regex("[A-Za-z0-9_+-]{2,64}")
}
