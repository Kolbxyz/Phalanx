package org.phalanx.android.core.preview

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches pages and their preview images from this phone, for link previews the user asked to be
 * built here. HTTPS only, every redirect included; no cookie, no referrer, and a user agent naming
 * neither the app nor the phone. Hosts on the local network are refused, so a link someone sends
 * can't make the phone call the user's router or anything else behind their firewall.
 */
internal class PageFetcher @Inject constructor() {
    /** The start of the page's HTML, where its head and preview tags are. */
    fun page(url: String): String = open(url, accept = "text/html") { connection ->
        val type = connection.contentType.orEmpty()
        if (!type.startsWith("text/html", ignoreCase = true)) throw IOException("Not a page")
        val bytes = connection.inputStream.use { it.readAtMost(MAX_PAGE_BYTES) }
        String(bytes, charsetOf(type))
    }

    fun image(url: String): ByteArray = open(url, accept = "image/*") { connection ->
        if (!connection.contentType.orEmpty().startsWith("image/")) throw IOException("Not an image")
        val bytes = connection.inputStream.use { it.readAtMost(MAX_IMAGE_BYTES + 1) }
        if (bytes.size > MAX_IMAGE_BYTES) throw IOException("Image too large")
        bytes
    }

    // Redirects are followed by hand, so each hop is checked as the first was.
    private fun <T> open(url: String, accept: String, read: (HttpsURLConnection) -> T): T {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            val connection = connect(current, accept)
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) return read(connection)
                current = redirectOf(current, connection)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    /** Where a redirect leads; any other answer but success is a failure. */
    private fun redirectOf(from: URL, connection: HttpURLConnection): URL {
        val location = connection.getHeaderField("Location")?.takeIf { connection.responseCode in REDIRECTS }
            ?: throw IOException("HTTP ${connection.responseCode}")
        return URL(from, location)
    }

    private fun connect(url: URL, accept: String): HttpsURLConnection {
        // Refused before anything is sent: plain HTTP, and any host that resolves into a local network.
        if (url.protocol != "https" || InetAddress.getAllByName(url.host).any { it.isPrivate() }) {
            throw IOException("Refused host")
        }
        val connection = url.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", accept)
        return connection
    }

    private fun InetAddress.isPrivate(): Boolean {
        val first = address[0].toInt() and BYTE
        // IPv6 unique local addresses, fc00::/7, which isSiteLocalAddress does not cover.
        val uniqueLocal = this is Inet6Address && (first and ULA_MASK) == ULA_PREFIX
        // Carrier-grade NAT, 100.64.0.0/10, as private as 10/8 in practice.
        val sharedNat = address.size == IPV4_BYTES && first == CGNAT_FIRST &&
            (address[1].toInt() and CGNAT_MASK) == CGNAT_SECOND
        return isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress ||
            isMulticastAddress || uniqueLocal || sharedNat
    }

    private fun charsetOf(contentType: String): Charset =
        CHARSET.find(contentType)?.groupValues?.get(1)?.let { name ->
            try {
                Charset.forName(name)
            } catch (_: IllegalCharsetNameException) {
                null
            } catch (_: UnsupportedCharsetException) {
                null
            }
        } ?: Charsets.UTF_8

    /** Up to [limit] bytes, the rest left unread. */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = read(buffer, filled, limit - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible)"
        const val TIMEOUT_MILLIS = 8_000
        const val MAX_REDIRECTS = 4
        const val MAX_PAGE_BYTES = 256 * 1024
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val IPV4_BYTES = 4
        const val ULA_MASK = 0xfe
        const val ULA_PREFIX = 0xfc
        const val CGNAT_FIRST = 100
        const val CGNAT_MASK = 0xc0
        const val CGNAT_SECOND = 64
        const val BYTE = 0xff
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val CHARSET = Regex("charset=\"?([\\w.:-]+)", RegexOption.IGNORE_CASE)
    }
}
