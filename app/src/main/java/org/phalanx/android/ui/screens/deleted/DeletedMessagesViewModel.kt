package org.phalanx.android.ui.screens.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.DeletedMessage
import org.phalanx.android.data.repository.DeletedMessagesRepository

@HiltViewModel
class DeletedMessagesViewModel @Inject constructor(
    private val repository: DeletedMessagesRepository
) : ViewModel() {
    /** Most recently deleted first. */
    val deleted: StateFlow<List<DeletedMessage>> = repository.deleted

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
