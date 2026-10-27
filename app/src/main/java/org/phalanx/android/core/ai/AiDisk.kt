package org.phalanx.android.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import org.phalanx.android.core.ai.speech.Transcript
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.di.ApplicationScope

/**
 * AI results kept on this phone while "Keep AI results" is on, so a transcript or a room's
 * catch-up is not written twice, and photos can be found by what they show. Sealed like the
 * session; turning the setting off or signing out deletes them all.
 */
@Singleton
class AiDisk @Inject internal constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox,
    private val settings: SettingsStore,
    private val active: ActiveClient,
    @ApplicationScope private val scope: CoroutineScope
) {
    private enum class Mode { Keep, Erase, Wait }

    private val dir = File(context.filesDir, DIR_NAME)
    private val enabled = MutableStateFlow(false)

    internal val transcripts =
        shelf("transcripts", Transcript.serializer(), TRANSCRIPTS, TimeUnit.DAYS.toMillis(TRANSCRIPT_DAYS))

    // A catch-up goes stale fast: by the next day the user has read the room or more came in.
    internal val catchUps = shelf("catch_ups", CatchUp.serializer(), CATCH_UPS, TimeUnit.DAYS.toMillis(1))

    private val shelves = listOf(transcripts, catchUps)

    fun start() {
        scope.launch {
            combine(settings.settings.map { it.aiOnDisk }, active.state) { keep, state ->
                when {
                    // Still restoring at launch: neither on nor gone, so the results are left alone.
                    state is SessionState.Restoring -> Mode.Wait
                    keep && state is SessionState.LoggedIn -> Mode.Keep
                    // Off, or signed out: the results belong to no one who still wants them.
                    else -> Mode.Erase
                }
            }.distinctUntilChanged().collect { mode ->
                enabled.value = mode == Mode.Keep
                if (mode == Mode.Erase) shelves.forEach { it.clear() }
            }
        }
    }

    /** The result kept as [key], or null when there is none or keeping them is off. */
    internal suspend fun <T> read(shelf: AiShelf<T>, key: String): T? = if (enabled.value) shelf.get(key) else null

    /** Every result on [shelf], by key; empty when keeping them is off. */
    internal suspend fun <T> readAll(shelf: AiShelf<T>): Map<String, T> = if (enabled.value) shelf.all() else emptyMap()

    /** Keeps [value] as [key] while the setting is on; does nothing otherwise. */
    internal suspend fun <T> keep(shelf: AiShelf<T>, key: String, value: T) {
        if (!enabled.value) return
        shelf.put(key, value)
        // Turned off while it was being written: the erase may have run first, so run it again.
        if (!enabled.value) shelf.clear()
    }

    private fun <T> shelf(name: String, serializer: KSerializer<T>, capacity: Int, maxAgeMillis: Long) =
        AiShelf(File(dir, "$name.bin"), box, serializer, capacity, maxAgeMillis)

    private companion object {
        const val DIR_NAME = "ai_results"
        const val TRANSCRIPTS = 2_000
        const val TRANSCRIPT_DAYS = 30L
        const val CATCH_UPS = 200
    }
}
