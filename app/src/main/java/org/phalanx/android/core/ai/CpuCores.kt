package org.phalanx.android.core.ai

import java.io.File
import java.io.IOException

/**
 * How many threads the model runs on. Writing the reply is bound by memory bandwidth, which a few
 * big cores already saturate. Reading the prompt is bound by arithmetic, so it takes every fast
 * core and leaves out only the little ones, which would hold the others back.
 */
internal object CpuCores {
    private val count = Runtime.getRuntime().availableProcessors()

    /** The big cores of an 8-core phone; more threads fight over memory bandwidth for no gain. */
    val replyThreads: Int = (count / 2).coerceIn(2, 4)

    /** The cores clocked within [FAST_SHARE] of the fastest, or [replyThreads] if that is unknown. */
    val promptThreads: Int = fastCores()?.coerceIn(replyThreads, count) ?: replyThreads

    private fun fastCores(): Int? {
        val speeds = (0 until count).map { maxSpeed(it) ?: return null }
        val fastest = speeds.max()
        return speeds.count { it >= fastest * FAST_SHARE }
    }

    private fun maxSpeed(cpu: Int): Long? = try {
        File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
    } catch (_: IOException) {
        null
    }

    // Above the little cores of current phones, which top out near 60% of the fastest core.
    private const val FAST_SHARE = 0.7
}
