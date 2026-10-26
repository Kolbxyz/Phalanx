package org.phalanx.android.core.matrix

import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.phalanx.android.data.repository.MutedUsersRepository
import org.phalanx.android.di.ApplicationScope

/**
 * A muted user is a `sender` push rule with no actions (the spec's way to silence one sender). The
 * SDK can set push rules but not delete them, so both directions go through the API directly.
 */
@Singleton
internal class PushRulesMutedUsersRepository @Inject constructor(
    private val api: ClientServerApi,
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) : MutedUsersRepository {
    private val mutableMuted = MutableStateFlow<Set<String>>(emptySet())
    override val muted: StateFlow<Set<String>> = mutableMuted.asStateFlow()

    init {
        // A new sync service is a new sign-in (or a sign-out), with its own rules.
        scope.launch {
            active.syncService.collect { service ->
                mutableMuted.value = emptySet()
                if (service != null) load()
            }
        }
    }

    override suspend fun mute(userId: String): Result<Unit> =
        change(userId, "PUT", """{"actions":[]}""") { it + userId }

    override suspend fun unmute(userId: String): Result<Unit> =
        change(userId, "DELETE", body = null) { it - userId }

    private suspend fun change(
        userId: String,
        method: String,
        body: String?,
        update: (Set<String>) -> Set<String>
    ): Result<Unit> = try {
        val response = api.call(method, RULES + URLEncoder.encode(userId, "UTF-8"), body)
        // Unmuting someone whose rule is already gone (removed from another client) is done too.
        if (response.isSuccess || (method == "DELETE" && response.code == HTTP_NOT_FOUND)) {
            mutableMuted.update(update)
            Result.success(Unit)
        } else {
            Result.failure(IOException("HTTP ${response.code}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    }

    // Offline at sign-in leaves the list empty until the next mute or unmute; nothing here polls.
    private suspend fun load() {
        try {
            val response = api.call("GET", "pushrules/global/")
            if (response.isSuccess) mutableMuted.value = mutedIn(response.body)
        } catch (_: IOException) {
            return
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            // Not the documented shape; nothing to show rather than a guess.
            return
        }
    }

    private fun mutedIn(body: String): Set<String> {
        val rules = Json.parseToJsonElement(body).jsonObject["sender"] as? JsonArray ?: return emptySet()
        return rules.mapNotNullTo(HashSet()) { element ->
            val rule = element as? JsonObject ?: return@mapNotNullTo null
            val silent = rule["actions"]?.jsonArray?.all { it.jsonPrimitive.content == "dont_notify" } ?: false
            val enabled = rule["enabled"]?.jsonPrimitive?.content != "false"
            rule["rule_id"]?.jsonPrimitive?.content?.takeIf { silent && enabled }
        }
    }

    private companion object {
        const val RULES = "pushrules/global/sender/"
        const val HTTP_NOT_FOUND = 404
    }
}
