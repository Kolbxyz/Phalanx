package org.phalanx.android.core.matrix

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.phalanx.android.data.repository.ReactionPushRepository

/**
 * Turns the spec's `.m.rule.reaction` override between notify and silent (its default). The rule
 * is enabled too, since a server may ship it disabled. Which reactions are shown is decided on the
 * phone: only those to this account's own messages (see [reactionToOwn]).
 */
@Singleton
internal class ReactionPushRule @Inject constructor(private val api: ClientServerApi) : ReactionPushRepository {
    override suspend fun setNotify(enabled: Boolean): Result<Unit> = try {
        var response = api.call("PUT", "$RULE/actions", if (enabled) NOTIFY else SILENT)
        if (response.isSuccess) response = api.call("PUT", "$RULE/enabled", ENABLED)
        if (response.isSuccess) Result.success(Unit) else Result.failure(IOException("HTTP ${response.code}"))
    } catch (e: IOException) {
        Result.failure(e)
    }

    private companion object {
        const val RULE = "pushrules/global/override/.m.rule.reaction"
        const val NOTIFY = """{"actions":["notify"]}"""
        const val SILENT = """{"actions":[]}"""
        const val ENABLED = """{"enabled":true}"""
    }
}
