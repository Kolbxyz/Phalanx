package org.phalanx.android.core.ai.photos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** One run of [PhotoIndexer], scheduled by [PhotoIndexScheduler]; stopped when the phone is used or unplugged. */
class PhotoIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Dependencies {
        fun indexer(): PhotoIndexer
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).indexer().indexSome()
        return Result.success()
    }
}
