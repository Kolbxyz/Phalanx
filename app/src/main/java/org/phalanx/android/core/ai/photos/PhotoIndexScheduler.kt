package org.phalanx.android.core.ai.photos

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.AiDisk
import org.phalanx.android.core.ai.vision.PhotoDescriber
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.di.ApplicationScope

/**
 * Keeps [PhotoIndexWorker] scheduled once a day while "Make photos searchable" and "Keep AI
 * results" are on and both models are imported, and only ever while the phone charges, sits idle
 * on an unmetered network with battery to spare. Turning photo search off erases the descriptions.
 */
@Singleton
class PhotoIndexScheduler @Inject internal constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsStore,
    private val describer: PhotoDescriber,
    private val disk: AiDisk,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val work = WorkManager.getInstance(context)

    fun start() {
        scope.launch {
            combine(settings.settings, describer.ready) { current, ready ->
                current.photoSearch to (current.photoSearch && current.aiOnDisk && ready)
            }.distinctUntilChanged().collect { (wanted, runnable) ->
                if (runnable) schedule() else work.cancelUniqueWork(WORK_NAME)
                if (!wanted) disk.photos.clear()
            }
        }
    }

    private fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<PhotoIndexWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "photo_index"
    }
}
