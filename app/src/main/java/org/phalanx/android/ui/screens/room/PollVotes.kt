package org.phalanx.android.ui.screens.room

import org.phalanx.android.data.model.MessageBody

/**
 * The vote to send once [tapped] is pressed, or null when the tap changes nothing. With a single
 * choice, tapping another answer moves the vote and tapping the chosen one takes it back. With
 * several, a tap adds or removes that answer, up to the poll's limit.
 */
internal fun MessageBody.Poll.voteAfterTapping(tapped: String): List<String>? {
    if (ended || answers.none { it.id == tapped }) return null
    val chosen = tapped in myAnswers
    return when {
        maxSelections == 1 -> if (chosen) emptyList() else listOf(tapped)
        chosen -> inPollOrder(myAnswers - tapped)
        myAnswers.size >= maxSelections -> null
        else -> inPollOrder(myAnswers + tapped)
    }
}

/** Whether the counts can be shown: an undisclosed poll keeps them hidden until it ends. */
internal val MessageBody.Poll.showsResults: Boolean get() = disclosed || ended

/** Votes cast across every answer; a voter with several choices counts once per choice. */
internal val MessageBody.Poll.totalVotes: Int get() = answers.sumOf { it.votes }

private fun MessageBody.Poll.inPollOrder(ids: Set<String>): List<String> =
    answers.map { it.id }.filter { it in ids }
