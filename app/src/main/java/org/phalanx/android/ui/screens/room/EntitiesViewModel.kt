package org.phalanx.android.ui.screens.room

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.core.format.TextEntities

/** Dates, numbers and addresses in the message whose menu is open. */
@HiltViewModel
internal class EntitiesViewModel @Inject constructor(private val entities: TextEntities) : ViewModel() {
    suspend fun find(text: String): List<FoundEntity> = entities.find(text)
}
