package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.core.ai.actions.MessageAction
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.navigation.RoomRoute

sealed interface ActionsState {
    data object Idle : ActionsState

    data object Looking : ActionsState

    /** [actions] found in [message]; empty when there was nothing to act on. */
    data class Found(val message: TimelineEntry.Message, val actions: List<MessageAction>) : ActionsState

    data class Failed(@StringRes val message: Int) : ActionsState
}

/** Finds the dates, tasks, places and numbers in one message with the local model, when asked. */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    private val model: LocalSummarizer,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId
    private val mutableState = MutableStateFlow<ActionsState>(ActionsState.Idle)
    val state: StateFlow<ActionsState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun find(message: TimelineEntry.Message, text: String) {
        job?.cancel()
        mutableState.value = ActionsState.Looking
        job = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val sentAt = Instant.ofEpochMilli(message.timestampMillis).atZone(zone)
            val before = contextBefore(message)
            model.findActions(lineOf(message, text), before, sentAt).fold(
                onSuccess = { mutableState.value = ActionsState.Found(message, it) },
                onFailure = { mutableState.value = ActionsState.Failed(aiFailureMessage(it)) }
            )
        }
    }

    fun dismiss() {
        job?.cancel()
        mutableState.value = ActionsState.Idle
    }

    // The few messages just before, which often say what "it" or "there" is.
    private suspend fun contextBefore(message: TimelineEntry.Message): List<SummaryLine> {
        val messages = timelines.open(roomId).getOrNull()?.entries?.value
            ?.filterIsInstance<TimelineEntry.Message>().orEmpty()
        val at = messages.indexOfFirst { it.id == message.id }
        if (at <= 0) return emptyList()
        return messages.subList((at - CONTEXT_MESSAGES).coerceAtLeast(0), at)
            .map { lineOf(it, summaryOf(it.body, context::getString)) }
    }

    private fun lineOf(message: TimelineEntry.Message, text: String) = SummaryLine(
        if (message.isOwn) context.getString(R.string.summary_you) else message.senderName,
        text,
        message.eventId
    )

    private companion object {
        const val CONTEXT_MESSAGES = 6
    }
}
