package org.phalanx.android.core.ai.speech

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** The audio could not be read: no audio track, or a format this phone cannot decode. */
class UndecodableAudioException(cause: Throwable? = null) : IOException("Undecodable audio", cause)

/** [samples] at 16 kHz mono; [cut] when the clip ran past [MAX_SECONDS] and only its start is here. */
internal class DecodedAudio(val samples: FloatArray, val cut: Boolean)

/**
 * Decodes an audio file (Opus, AAC, AMR, MP3, ... whatever the phone's own decoders read) into the
 * 16 kHz mono floats Whisper takes. Channels are averaged, then resampled linearly: speech has
 * nothing above 8 kHz that Whisper would use.
 */
internal suspend fun decodeAudio(context: Context, uri: Uri): DecodedAudio {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        val track = audioTrackOf(extractor)
        extractor.selectTrack(track)
        return decodeTrack(extractor, extractor.getTrackFormat(track))
    } catch (e: IllegalArgumentException) {
        throw UndecodableAudioException(e)
    } catch (e: IllegalStateException) {
        throw UndecodableAudioException(e)
    } finally {
        extractor.release()
    }
}

private fun audioTrackOf(extractor: MediaExtractor): Int = (0 until extractor.trackCount).firstOrNull {
    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
} ?: throw UndecodableAudioException()

private suspend fun decodeTrack(extractor: MediaExtractor, format: MediaFormat): DecodedAudio {
    val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
    val codec = try {
        MediaCodec.createDecoderByType(mime)
    } catch (e: IOException) {
        throw UndecodableAudioException(e)
    }
    try {
        codec.configure(format, null, null, 0)
        codec.start()
        return drain(codec, extractor, format)
    } finally {
        codec.release()
    }
}

// Feeds the extractor's packets in and collects the decoded 16-bit PCM, mixed down to mono.
private suspend fun drain(codec: MediaCodec, extractor: MediaExtractor, input: MediaFormat): DecodedAudio {
    val mono = FloatList()
    var rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var cut = false
    while (true) {
        coroutineContext.ensureActive()
        if (!inputDone) inputDone = feed(codec, extractor)
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else if (index >= 0) {
            mixDown(checkNotNull(codec.getOutputBuffer(index)), channels, mono)
            codec.releaseOutputBuffer(index, false)
            if (mono.size >= MAX_SECONDS * rate) cut = true
            if (cut || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }
    return DecodedAudio(resample(mono.toArray(MAX_SECONDS * rate), rate), cut)
}

// 16-bit PCM with [channels] interleaved, averaged into [mono] as floats between -1 and 1.
private fun mixDown(buffer: ByteBuffer, channels: Int, mono: FloatList) {
    val pcm = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
    while (pcm.remaining() >= channels) {
        var sum = 0f
        repeat(channels) { sum += pcm.get() }
        mono.add(sum / channels / Short.MAX_VALUE)
    }
}

// True once the end of the stream has been queued.
private fun feed(codec: MediaCodec, extractor: MediaExtractor): Boolean {
    val index = codec.dequeueInputBuffer(TIMEOUT_MICROS)
    if (index < 0) return false
    val size = extractor.readSampleData(checkNotNull(codec.getInputBuffer(index)), 0)
    return if (size < 0) {
        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        true
    } else {
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        false
    }
}

/** [samples] at [rate] Hz, linearly resampled to [WHISPER_RATE]. */
internal fun resample(samples: FloatArray, rate: Int): FloatArray {
    if (rate == WHISPER_RATE || samples.isEmpty()) return samples
    val ratio = rate.toDouble() / WHISPER_RATE
    return FloatArray((samples.size / ratio).toInt()) { i ->
        val at = i * ratio
        val low = at.toInt().coerceAtMost(samples.lastIndex)
        val high = (low + 1).coerceAtMost(samples.lastIndex)
        val weight = (at - low).toFloat()
        samples[low] * (1 - weight) + samples[high] * weight
    }
}

/** A growable float array: a voice note is hundreds of thousands of samples, too many to box. */
private class FloatList {
    private var data = FloatArray(INITIAL_CAPACITY)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(limit: Int): FloatArray = data.copyOf(size.coerceAtMost(limit))
}

const val WHISPER_RATE = 16_000

/** Longer clips are transcribed up to here: their start is what a summary or a search needs. */
const val MAX_SECONDS = 600

private const val TIMEOUT_MICROS = 10_000L
private const val INITIAL_CAPACITY = 1 shl 16
