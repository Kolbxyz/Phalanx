package org.phalanx.android.core.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Shows a reminder whose alarm was lost, as after a reboot; does nothing if the alarm showed it. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun reminders(): Reminders
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).reminders().fire(id)
        return Result.success()
    }

    internal companion object {
        const val KEY_ID = "id"
    }
}
