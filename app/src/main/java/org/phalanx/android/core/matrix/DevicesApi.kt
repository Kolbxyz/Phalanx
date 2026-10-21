package org.phalanx.android.core.matrix

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.phalanx.android.data.model.Device
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The client-server `/devices` endpoints, called directly: the SDK bindings have no device list.
 * Only used for password accounts; on OAuth servers the provider's own pages manage devices.
 */
@Singleton
internal class DevicesApi @Inject constructor(private val api: ClientServerApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): List<Device> {
        val session = api.session()
        val response = api.call("GET", "devices")
        if (response.code != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${response.code}")
        return json.decodeFromString<DevicesResponse>(response.body).devices.map {
            Device(
                id = it.deviceId,
                name = it.displayName,
                lastSeenIp = it.lastSeenIp,
                lastSeenMillis = it.lastSeenTs,
                isCurrent = it.deviceId == session.deviceId
            )
        }
    }

    /**
     * Deletes [deviceId], which signs it out and drops its keys. The server demands the password
     * again (user-interactive auth): the first call fetches the auth session, the second answers it.
     */
    suspend fun delete(deviceId: String, password: String): DeleteResult {
        val session = api.session()
        val path = "devices/" + URLEncoder.encode(deviceId, "UTF-8")
        val first = api.call("DELETE", path, body = "{}")
        if (first.code == HttpURLConnection.HTTP_OK) return DeleteResult.Deleted
        if (first.code != HttpURLConnection.HTTP_UNAUTHORIZED) throw IOException("HTTP ${first.code}")
        val authSession = json.parseToJsonElement(first.body).let { (it as JsonObject)["session"]?.jsonPrimitive?.content }
        val auth = buildJsonObject {
            putJsonObject("auth") {
                put("type", "m.login.password")
                putJsonObject("identifier") {
                    put("type", "m.id.user")
                    put("user", session.userId)
                }
                put("password", password)
                if (authSession != null) put("session", authSession)
            }
        }
        val second = api.call("DELETE", path, body = auth.toString())
        return when (second.code) {
            HttpURLConnection.HTTP_OK -> DeleteResult.Deleted
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> DeleteResult.WrongPassword
            else -> throw IOException("HTTP ${second.code}")
        }
    }

    enum class DeleteResult { Deleted, WrongPassword }

    @Serializable
    private class DevicesResponse(val devices: List<DeviceJson> = emptyList())

    @Serializable
    private class DeviceJson(
        @SerialName("device_id") val deviceId: String,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("last_seen_ip") val lastSeenIp: String? = null,
        @SerialName("last_seen_ts") val lastSeenTs: Long? = null
    )
}
