package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.MAX_TRANSLATED_CHARS
import org.phalanx.android.core.ai.ModelMissingException
import org.phalanx.android.core.ai.SummariesUnsupportedException

/** What the model is asked to do with one message. */
internal enum class AssistKind(
    @StringRes val title: Int,
    @StringRes val working: Int,
    @StringRes val disclaimer: Int
) {
    Translate(R.string.assist_translation_title, R.string.assist_translating, R.string.assist_translation_disclaimer),
    Summarize(R.string.assist_summary_title, R.string.assist_summarizing, R.string.summary_disclaimer)
}

internal sealed interface AssistState {
    data object Idle : AssistState

    /** [text] is the reply so far; [done] once it is complete. [partial]: only part of the message was read. */
    data class Shown(val kind: AssistKind, val text: String, val done: Boolean, val partial: Boolean) : AssistState

    data class Failed(val kind: AssistKind, @StringRes val message: Int) : AssistState
}

/** Translates one message into the phone's language, or sums a long one up, with the local model. */
@HiltViewModel
internal class AssistViewModel @Inject constructor(
    private val model: LocalSummarizer,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutableState = MutableStateFlow<AssistState>(AssistState.Idle)
    val state: StateFlow<AssistState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun translate(text: String) {
        // The model is asked for a language by its English name, which it knows best.
        val language = context.resources.configuration.locales[0].getDisplayLanguage(Locale.ENGLISH)
        run(AssistKind.Translate, partial = text.length > MAX_TRANSLATED_CHARS) { onText ->
            model.translate(text, language, onText)
        }
    }

    fun summarize(text: String) = run(AssistKind.Summarize, partial = false) { onText ->
        model.summarizeMessage(text, onText)
    }

    fun dismiss() {
        job?.cancel()
        mutableState.value = AssistState.Idle
    }

    private fun run(kind: AssistKind, partial: Boolean, ask: suspend ((String) -> Unit) -> Result<String>) {
        job?.cancel()
        mutableState.value = AssistState.Shown(kind, "", done = false, partial = partial)
        job = viewModelScope.launch {
            ask { mutableState.value = AssistState.Shown(kind, it, done = false, partial = partial) }.fold(
                onSuccess = { mutableState.value = AssistState.Shown(kind, it, done = true, partial = partial) },
                onFailure = { mutableState.value = AssistState.Failed(kind, aiFailureMessage(it)) }
            )
        }
    }

    companion object {
        /** Shorter messages are quick to read; summing them up is not offered. */
        const val LONG_MESSAGE_CHARS = 600
    }
}

/** What to tell the user when the local model could not answer. */
@StringRes
internal fun aiFailureMessage(error: Throwable): Int = when (error) {
    is ModelMissingException -> R.string.summary_no_model
    is SummariesUnsupportedException -> R.string.settings_ai_unsupported
    else -> R.string.summary_failed
}
