package org.phalanx.android.core.matrix

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Direct client-server API calls, for the few endpoints the SDK bindings do not cover. */
@Singleton
internal class ClientServerApi @Inject constructor(private val sessionStore: SessionStore) {
    class Response(val code: Int, val body: String) {
        val isSuccess: Boolean get() = code in 200..299
    }

    /** The signed-in session, read fresh each call so a refreshed token is used. */
    fun session(): SessionData = sessionStore.load() ?: throw IOException("Signed out")

    /** [path] is relative to `/_matrix/client/v3/`, with each segment already URL-encoded. */
    suspend fun call(method: String, path: String, body: String? = null): Response = withContext(Dispatchers.IO) {
        val session = session()
        val url = URL(session.homeserverUrl.trimEnd('/') + "/_matrix/client/v3/" + path)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000
    }
}
