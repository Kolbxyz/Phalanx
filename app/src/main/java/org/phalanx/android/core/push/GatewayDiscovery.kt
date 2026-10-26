package org.phalanx.android.core.push

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Picks the Matrix push gateway for a distributor endpoint. A homeserver cannot push to a
 * UnifiedPush endpoint directly; a gateway translates. Distributor servers that run one (ntfy,
 * NextPush) answer on the endpoint's own host, which keeps pushes off third-party servers; the
 * UnifiedPush project's public gateway covers the rest.
 */
internal object GatewayDiscovery {
    const val PUBLIC_GATEWAY = "https://matrix.gateway.unifiedpush.org$NOTIFY_PATH"

    /** Fails only when the endpoint's host cannot be reached; "no gateway there" is the public one. */
    suspend fun resolve(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        // Same host and port, never the endpoint's own path or credentials.
        val candidate = try {
            URI(endpoint).takeIf { it.scheme == "https" && it.host != null }
                ?.let { URI("https", null, it.host, it.port, NOTIFY_PATH, null, null).toString() }
        } catch (_: URISyntaxException) {
            null
        } ?: return@withContext Result.success(PUBLIC_GATEWAY)
        try {
            Result.success(if (isMatrixGateway(candidate)) candidate else PUBLIC_GATEWAY)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** A gateway answers a GET on its notify path with `{"unifiedpush":{"gateway":"matrix"}}`. */
    private fun isMatrixGateway(url: String): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            return parsesAsMatrixGateway(connection.inputStream.use { it.readAtMost(MAX_BODY_BYTES) }.decodeToString())
        } finally {
            connection.disconnect()
        }
    }

    fun parsesAsMatrixGateway(body: String): Boolean = try {
        val gateway = Json.parseToJsonElement(body).jsonObject["unifiedpush"]?.jsonObject?.get("gateway")
        gateway?.jsonPrimitive?.content == "matrix"
    } catch (_: SerializationException) {
        false
    } catch (_: IllegalArgumentException) {
        // Thrown by jsonObject/jsonPrimitive when the JSON has another shape.
        false
    }
}

/** InputStream.readNBytes needs API 33. */
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

private const val NOTIFY_PATH = "/_matrix/push/v1/notify"
private const val TIMEOUT_MILLIS = 10_000
private const val MAX_BODY_BYTES = 4096
