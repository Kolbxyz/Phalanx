package org.phalanx.android.core.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.phalanx.android.core.matrix.NotificationFetcher

/** Fetches and decrypts the event a push named, then shows it. Retried with backoff when the fetch fails. */
class PushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun fetcher(): NotificationFetcher
        fun notifier(): Notifier
        fun voice(): VoiceNotification
    }

    private val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)

    override suspend fun doWork(): Result {
        val roomId = inputData.getString(KEY_ROOM_ID) ?: return Result.failure()
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        return dependencies.fetcher().fetch(roomId, eventId).fold(
            onSuccess = { message ->
                message?.let {
                    dependencies.notifier().show(it)
                    // After showing, so the message never waits on the model to be heard of.
                    dependencies.voice().writeOut(it)
                }
                Result.success()
            },
            // A message too old to matter by the last try is dropped, not shown late.
            onFailure = { if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry() }
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifier.CHECKING_ID, dependencies.notifier().checking())

    internal companion object {
        const val KEY_ROOM_ID = "room_id"
        const val KEY_EVENT_ID = "event_id"
        const val MAX_ATTEMPTS = 3
    }
}
