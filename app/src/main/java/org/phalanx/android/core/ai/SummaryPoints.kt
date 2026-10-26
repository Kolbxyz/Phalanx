package org.phalanx.android.core.ai

/** One point of a summary; [eventId] is the message it cites, when the model cited one it was given. */
data class SummaryPoint(val text: String, val eventId: String?)

/**
 * A finished summary as its points. [messageCount] is how many messages the model was given, fewer
 * than asked when the oldest did not fit.
 */
data class Summary(val points: List<SummaryPoint>, val messageCount: Int)

private val CITATIONS = Regex("""\s*(\[\s*\d+(\s*[,;]\s*\d+)*\s*])+\s*""")
private val FIRST_NUMBER = Regex("""\d+""")
private val BULLET = Regex("""^\s*(?:[-*•]|\d+[.)])\s+""")

// A citation still being written at the end of the reply, as "[1" or "[".
private val PARTIAL_CITATION = Regex("""\s*\[[\d\s,;]*$""")

/** [reply] without the bracketed message numbers, for showing while it is still being written. */
fun withoutCitations(reply: String): String = reply.lines().joinToString("\n") {
    it.replace(CITATIONS, " ").replace(PARTIAL_CITATION, "").trimEnd()
}

/** Splits the model's bullet list into points; each keeps the first message it cites. */
internal fun pointsOf(reply: String, eventIds: List<String?>): List<SummaryPoint> = reply.lines()
    .map { it.replace(BULLET, "").trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        val cited = CITATIONS.find(line)?.let { FIRST_NUMBER.find(it.value)?.value?.toIntOrNull() }
        SummaryPoint(line.replace(CITATIONS, " ").trim(), cited?.let { eventIds.getOrNull(it - 1) })
    }
