package org.phalanx.android.core.ai

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Job

/**
 * Downloads [url] into [target] while hashing it, as [copyHashed] does for an imported file: the
 * caller keeps it only if the hash is one it accepts. HTTPS only, redirects included (Hugging Face
 * hands its files to a CDN), with no cookie, token or identifier sent.
 */
internal fun downloadHashed(url: String, target: File, limit: Long, job: Job, onProgress: (Float) -> Unit): HashedCopy {
    val connection = requireHttps(URL(url).openConnection())
    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = READ_TIMEOUT_MILLIS
    connection.useCaches = false
    try {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
        // A redirect to plain HTTP is never followed, but check where the bytes came from anyway.
        requireHttps(connection)
        val size = connection.contentLengthLong.takeIf { it > 0 }
        return connection.inputStream.use { copyHashed(it, size, target, limit, job, onProgress) }
    } finally {
        connection.disconnect()
    }
}

private fun requireHttps(connection: URLConnection): HttpsURLConnection =
    (connection as? HttpsURLConnection)?.takeIf { it.url.protocol == "https" } ?: throw IOException("Not HTTPS")

private const val CONNECT_TIMEOUT_MILLIS = 15_000
private const val READ_TIMEOUT_MILLIS = 30_000
