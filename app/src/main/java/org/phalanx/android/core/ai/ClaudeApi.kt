package org.phalanx.android.core.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cloud writing is on but no API key is kept. */
class CloudKeyMissingException : Exception("No key")

/** Anthropic refused the API key. */
class CloudKeyRefusedException : Exception("Key refused")

/** Claude answered with an error, declined, or its reply was cut; [message] names which, never the content. */
class CloudFailedException(message: String) : Exception(message)

/**
 * Claude's Messages API over the phone's own HTTPS, like the app's other calls: the key and the
 * prompt go to api.anthropic.com and nowhere else, and neither is logged. A failed request is not
 * sent again on its own, since each one sends the messages out once more.
 */
@Singleton
internal class ClaudeApi @Inject constructor() {
    /**
     * Claude's reply to [prompt], with [onText] called as it grows. Throws [CloudKeyRefusedException],
     * [CloudFailedException], or [IOException] when Anthropic cannot be reached.
     */
    suspend fun complete(key: String, prompt: Prompt, onText: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val connection = URL(ENDPOINT).openConnection() as HttpsURLConnection
            // Cancelling closes the connection, which ends the read the thread is blocked in.
            cancellableNative({ connection.disconnect() }) {
                try {
                    send(connection, key, claudeRequest(prompt))
                    read(connection, onText)
                } catch (e: IOException) {
                    ensureActive()
                    throw e
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun send(connection: HttpsURLConnection, key: String, body: String) {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("content-type", "application/json")
        connection.setRequestProperty("x-api-key", key)
        connection.setRequestProperty("anthropic-version", API_VERSION)
        connection.outputStream.use { it.write(body.toByteArray()) }
    }

    private fun read(connection: HttpsURLConnection, onText: (String) -> Unit): String {
        refusalOf(connection.responseCode)?.let { throw it }
        val reply = StringBuilder()
        connection.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                when (val event = claudeEventOf(line)) {
                    is ClaudeEvent.Text -> onText(reply.append(event.text).toString())
                    else -> event?.failure()?.let { throw it }
                }
            }
        }
        return reply.toString().trim()
    }

    private fun refusalOf(code: Int): Exception? = when (code) {
        HttpURLConnection.HTTP_OK -> null
        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> CloudKeyRefusedException()
        else -> CloudFailedException("HTTP $code")
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2026-10-21"
        const val CONNECT_TIMEOUT_MILLIS = 15_000

        // Between two pieces of the reply; Claude may think for a while before the first.
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
