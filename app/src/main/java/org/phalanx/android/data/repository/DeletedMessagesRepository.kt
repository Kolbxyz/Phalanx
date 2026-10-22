package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.DeletedMessage

/** The deleted messages kept while the "Keep deleted messages" setting is on. */
interface DeletedMessagesRepository {
    /** Most recently deleted first. */
    val deleted: StateFlow<List<DeletedMessage>>

    /** Erases every kept copy, deleted or not. */
    suspend fun clear()
}
