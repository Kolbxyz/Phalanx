package org.phalanx.android.core.format

import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What a page says about itself for previews: its OpenGraph tags, or Twitter's and plain HTML's
 * when it has none. Read from the page itself, or from the homeserver's JSON of the same tags.
 */
internal data class OpenGraph(
    val title: String?,
    val description: String?,
    val siteName: String?,
    /** Absolute: an `https://` URL from a page, an `mxc://` one from the homeserver. */
    val image: String?
) {
    companion object {
        /** Null when the page names neither a title nor a description: nothing worth a card. */
        fun fromHtml(html: String, pageUrl: String): OpenGraph? {
            val tags = HashMap<String, String>()
            META.findAll(html).forEach { meta ->
                val attributes = ATTRIBUTE.findAll(meta.value).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] }
                }
                val key = (attributes["property"] ?: attributes["name"])?.lowercase()
                val content = attributes["content"]?.let(::decodeEntities)?.trim()
                if (key != null && !content.isNullOrEmpty()) tags.putIfAbsent(key, content)
            }
            val title = tags["og:title"] ?: tags["twitter:title"]
                ?: TITLE.find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }
            val description = tags["og:description"] ?: tags["twitter:description"] ?: tags["description"]
            val image = (tags["og:image:secure_url"] ?: tags["og:image"] ?: tags["twitter:image"])
                ?.let { resolve(pageUrl, it) }
            return if (title == null && description == null) {
                null
            } else {
                OpenGraph(title, description, tags["og:site_name"], image)
            }
        }

        /** The homeserver's `preview_url` answer; null when it is empty or not that shape. */
        fun fromJson(json: String): OpenGraph? = try {
            val tags = Json.parseToJsonElement(json).jsonObject
            fun tag(key: String) = (tags[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
            val title = tag("og:title")
            val description = tag("og:description")
            if (title == null && description == null) {
                null
            } else {
                OpenGraph(title, description, tag("og:site_name"), tag("og:image")?.takeIf { it.startsWith("mxc://") })
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // Thrown by jsonObject when the answer is JSON of another shape.
            null
        }

        // A relative image is relative to the page; only https ever loads, as everywhere in the app.
        private fun resolve(pageUrl: String, image: String): String? = try {
            URI(pageUrl).resolve(image.trim()).toString().takeIf { it.startsWith("https://") }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(HEX)?.toCodePoint()
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.toCodePoint()
                else -> NAMED[name]
            } ?: match.value
        }

        private fun Int.toCodePoint(): String? =
            takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

        private const val HEX = 16
        private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
        private val META = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        private val TITLE = Regex("<title[^>]*>([^<]{1,300})</title>", RegexOption.IGNORE_CASE)
        private val ENTITY = Regex("&(#x[0-9a-f]+|#[0-9]+|[a-z]+);", RegexOption.IGNORE_CASE)
    }
}
