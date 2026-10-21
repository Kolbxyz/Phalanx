package org.phalanx.android.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.OutgoingMedia
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Records one voice message at a time: Ogg/Opus where the platform has it (what other Matrix
 * clients send), AAC in MP4 before Android 10. The owner calls [stop] or [cancel] before dropping it.
 */
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preparer: AttachmentPreparer
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var sampling: Job? = null

    /** Loudness from 0 to 1, one value per [SAMPLE_MILLIS] since the recording started. */
    private val mutableLevels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = mutableLevels

    /** Starts recording; [scope] only samples the loudness and should end with the screen. */
    fun start(scope: CoroutineScope): Result<Unit> {
        cancel()
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val output = preparer.newFile(if (opus) "voice.ogg" else "voice.m4a")
        val created = newRecorder()
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (opus) {
                created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            created.setAudioSamplingRate(SAMPLE_RATE)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setOutputFile(output.path)
            created.prepare()
            created.start()
            recorder = created
            file = output
            startedAt = SystemClock.elapsedRealtime()
            mutableLevels.value = emptyList()
            sampling = scope.launch { sample(created) }
            Result.success(Unit)
        } catch (e: IOException) {
            discard(created, output)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            discard(created, output)
            Result.failure(e)
        }
    }

    /** The finished message, or null when it was too short to be worth sending. */
    fun stop(): OutgoingMedia.Voice? {
        val active = recorder
        val output = file
        if (active == null || output == null) return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        sampling?.cancel()
        recorder = null
        file = null
        val finished = try {
            active.stop()
            true
        } catch (_: RuntimeException) {
            // Documented: thrown when stop() comes before any audio was captured.
            false
        } finally {
            active.release()
        }
        val mimeType = if (output.extension == "ogg") "audio/ogg" else "audio/mp4"
        val voice = OutgoingMedia.Voice(output, mimeType, duration, mutableLevels.value)
            .takeIf { finished && duration >= MIN_MILLIS }
        if (voice == null) output.parentFile?.deleteRecursively()
        return voice
    }

    fun cancel() {
        val active = recorder ?: return
        sampling?.cancel()
        discard(active, file)
        recorder = null
        file = null
        mutableLevels.value = emptyList()
    }

    private suspend fun CoroutineScope.sample(active: MediaRecorder) {
        while (isActive) {
            delay(SAMPLE_MILLIS)
            // Square root, because loudness is heard roughly that way: quiet speech still shows.
            val level = sqrt((active.maxAmplitude.toFloat() / MAX_AMPLITUDE).coerceIn(0f, 1f))
            mutableLevels.value = mutableLevels.value + level
        }
    }

    private fun discard(active: MediaRecorder, output: File?) {
        active.reset()
        active.release()
        output?.parentFile?.deleteRecursively()
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BIT_RATE = 32_000
        const val SAMPLE_MILLIS = 100L
        const val MIN_MILLIS = 800L
        const val MAX_AMPLITUDE = 32767f
    }
}
