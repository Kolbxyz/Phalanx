package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.SearchService
import org.matrix.rustcomponents.sdk.SearchServicePaginationStateListener
import org.matrix.rustcomponents.sdk.SearchServiceResult
import org.matrix.rustcomponents.sdk.SearchServiceResultsListener
import org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.phalanx.android.data.repository.MessageHit
import org.phalanx.android.data.repository.MessageSearch
import org.phalanx.android.data.repository.SearchRepository
import uniffi.matrix_sdk_ui.SearchServicePaginationState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustSearchRepository @Inject constructor(private val active: ActiveClient) : SearchRepository {
    override suspend fun open(): MessageSearch? = withContext(Dispatchers.IO) {
        active.client()?.let { RustMessageSearch(it.searchService()).apply { start() } }
    }
}

/** Mirrors the SDK's result list as [MessageHit]s; results it can't show (a state event) are kept as nulls. */
private class RustMessageSearch(private val service: SearchService) : MessageSearch {
    private val lock = Any()
    private val items = ArrayList<MessageHit?>()
    private val mutableHits = MutableStateFlow<List<MessageHit>>(emptyList())
    override val hits: StateFlow<List<MessageHit>> = mutableHits.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()
    private val mutableEnd = MutableStateFlow(false)
    override val endReached: StateFlow<Boolean> = mutableEnd.asStateFlow()

    private val handles = ArrayList<TaskHandle>()

    suspend fun start() {
        handles += service.subscribeToPaginationStateUpdates(
            object : SearchServicePaginationStateListener {
                override fun onUpdate(paginationState: SearchServicePaginationState) {
                    mutableLoading.value = paginationState is SearchServicePaginationState.Loading
                    mutableEnd.value = (paginationState as? SearchServicePaginationState.Idle)?.endReached == true
                }
            }
        )
        handles += service.subscribeToResults(
            object : SearchServiceResultsListener {
                override fun onUpdate(updates: List<SearchServiceResultsUpdate>) = apply(updates)
            }
        )
    }

    override suspend fun setQuery(query: String): Result<Unit> = sdk {
        if (query.isBlank()) {
            synchronized(lock) {
                items.clear()
                mutableHits.value = emptyList()
            }
        } else {
            service.setQuery(query.trim())
        }
    }

    override suspend fun loadMore(): Result<Unit> = sdk { service.paginate() }

    override fun close() {
        handles.forEach {
            it.cancel()
            it.destroy()
        }
        service.destroy()
    }

    private fun apply(updates: List<SearchServiceResultsUpdate>) = synchronized(lock) {
        updates.forEach { update ->
            when (update) {
                is SearchServiceResultsUpdate.Append -> update.values.forEach { items.add(it.toHit()) }
                SearchServiceResultsUpdate.Clear -> items.clear()
                is SearchServiceResultsUpdate.PushFront -> items.add(0, update.value.toHit())
                is SearchServiceResultsUpdate.PushBack -> items.add(update.value.toHit())
                SearchServiceResultsUpdate.PopFront -> items.removeFirstOrNull()
                SearchServiceResultsUpdate.PopBack -> items.removeLastOrNull()
                is SearchServiceResultsUpdate.Insert -> items.add(update.index.toInt(), update.value.toHit())
                is SearchServiceResultsUpdate.Set -> items[update.index.toInt()] = update.value.toHit()
                is SearchServiceResultsUpdate.Remove -> items.removeAt(update.index.toInt())
                is SearchServiceResultsUpdate.Truncate -> items.subList(update.length.toInt(), items.size).clear()
                is SearchServiceResultsUpdate.Reset -> {
                    items.clear()
                    update.values.forEach { items.add(it.toHit()) }
                }
            }
        }
        mutableHits.value = items.filterNotNull()
    }

    private suspend fun sdk(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            block()
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }
}

private fun SearchServiceResult.toHit(): MessageHit? = when (this) {
    is SearchServiceResult.Message -> {
        val found = result
        val msgLike = (found.content as? TimelineItemContent.MsgLike)?.content
        val hit = msgLike?.let {
            MessageHit(
                roomId = roomId,
                eventId = found.eventId,
                senderName = nameOf(found.sender, found.senderProfile),
                senderAvatar = mediaRefOf((found.senderProfile as? ProfileDetails.Ready)?.avatarUrl),
                body = it.kind.toBody(isReply = it.inReplyTo != null),
                timestampMillis = found.timestamp.toLong()
            )
        }
        found.content.destroy()
        hit
    }
}
