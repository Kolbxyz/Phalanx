package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.PollKind
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.PollAnswer

/**
 * A poll as the SDK aggregates it: the start event with every response and the end folded in. The
 * SDK reads both the stable `m.poll.*` events and the `org.matrix.msc3381.*` ones bridges send.
 */
internal fun MsgLikeKind.Poll.toPoll(ownUserId: String?): MessageBody.Poll = MessageBody.Poll(
    question = question,
    answers = answers.map { PollAnswer(it.id, it.text, votes[it.id]?.size ?: 0) },
    maxSelections = maxSelections.toInt().coerceAtLeast(1),
    disclosed = kind == PollKind.DISCLOSED,
    ended = endTime != null,
    myAnswers = if (ownUserId == null) emptySet() else votes.filterValues { ownUserId in it }.keys
)

/** A poll known only by its question, as a notification carries it. */
internal fun pollQuestion(question: String): MessageBody.Poll =
    MessageBody.Poll(question, emptyList(), maxSelections = 1, disclosed = true, ended = false, myAnswers = emptySet())
