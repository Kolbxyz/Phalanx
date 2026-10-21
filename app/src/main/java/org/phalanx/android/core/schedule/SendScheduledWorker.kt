package org.phalanx.android.core.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Sends one scheduled message once there is a network; retried with backoff, then marked failed. */
class SendScheduledWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun scheduler(): MessageScheduler
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val scheduler = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).scheduler()
        return when {
            scheduler.deliver(id) -> Result.success()
            runAttemptCount + 1 >= MAX_ATTEMPTS -> {
                // Shown in the conversation, where the user can send it by hand or cancel it.
                scheduler.markFailed(id)
                Result.failure()
            }
            else -> Result.retry()
        }
    }

    internal companion object {
        const val KEY_ID = "id"
        const val MAX_ATTEMPTS = 6
    }
}
