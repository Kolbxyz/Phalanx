package org.phalanx.android.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.Reminder
import org.phalanx.android.data.repository.ReminderRepository

/** The reminders still to come, and cancelling one. */
@HiltViewModel
class RemindersViewModel @Inject constructor(private val repository: ReminderRepository) : ViewModel() {
    /** Soonest first. */
    val reminders: StateFlow<List<Reminder>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun cancel(id: String) {
        viewModelScope.launch { repository.cancel(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
