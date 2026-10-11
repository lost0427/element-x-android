/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.search

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.search.MessageSearch
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.search.MessageSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageTypeWithAttachment
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.impl.util.TaskHandleBag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.matrix.rustcomponents.sdk.SearchServiceInterface
import org.matrix.rustcomponents.sdk.SearchServicePaginationStateListener
import org.matrix.rustcomponents.sdk.SearchServiceResultsListener
import org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate
import uniffi.matrix_sdk_ui.SearchServicePaginationState

/**
 * Wraps the SDK's stateful [SearchServiceInterface] as a single search cursor.
 *
 * Lifecycle: the caller's [CoroutineScope] owns this instance. When that scope completes, both
 * subscription [org.matrix.rustcomponents.sdk.TaskHandle]s are cancelled and [onClose] releases the
 * underlying service — the handles must be retained until then, or the Rust side drops the
 * subscription.
 *
 * PII: search queries and message bodies are never logged.
 */
class RustMessageSearch(
    private val inner: SearchServiceInterface,
    private val onClose: () -> Unit,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val roomId: RoomId? = null,
) : MessageSearch {
    private val innerResults = MutableStateFlow<List<MessageSearchResult>>(emptyList())
    private val processor = MessageSearchResultsProcessor(
        results = innerResults,
        coroutineContext = dispatcher,
    )
    private val taskHandles = TaskHandleBag()
    private val subscriptionMutex = Mutex()
    private var subscribedToResults = false
    private val operationMutex = Mutex()
    private val chineseQuery = MutableStateFlow<ChineseSearchQuery?>(null)
    @Volatile
    private var scanningChineseQuery = false

    /**
     * The SDK listener is not a suspend function, so batches are handed to a single consumer
     * coroutine. An unlimited channel keeps them strictly in arrival order — applying diffs out of
     * order would corrupt the list just as surely as dropping them.
     */
    private val updates = Channel<List<SearchServiceResultsUpdate>>(Channel.UNLIMITED)

    override val results: StateFlow<ImmutableList<MessageSearchResult>> = combine(innerResults, chineseQuery) { results, query ->
        // Room scoping is applied HERE, at the exposure boundary, and nowhere else.
        // [innerResults] must stay index-parallel to the SDK's own list, because the positional
        // diffs (Insert/Set/Remove/Truncate) address the SDK's indices. Filtering inside
        // MessageSearchResultsProcessor instead is precisely the element-x-ios desync bug.
        results.filter { result ->
            (roomId == null || result.roomId == roomId) && (query == null || query.matches(result.searchableBody()))
        }.toImmutableList()
    }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    private val mutablePaginationState = MutableStateFlow(inner.paginationState().map())
    override val paginationState: StateFlow<MessageSearchPaginationState> = mutablePaginationState.asStateFlow()

    init {
        updates.consumeAsFlow()
            .onEach { processor.postUpdates(it) }
            .launchIn(scope)

        taskHandles += inner.subscribeToPaginationStateUpdates(
            object : SearchServicePaginationStateListener {
                override fun onUpdate(paginationState: SearchServicePaginationState) {
                    if (!scanningChineseQuery) mutablePaginationState.value = paginationState.map()
                }
            }
        )

        scope.coroutineContext.job.invokeOnCompletion {
            taskHandles.dispose()
            updates.close()
            onClose()
        }
    }

    override suspend fun setQuery(query: String): Result<Unit> = withContext(dispatcher) {
        operationMutex.withLock {
            runCatchingExceptions {
                // Subscribe lazily, so a search screen that is opened but never used costs nothing.
                subscribeToResultsIfNeeded()
                // The SDK contract for setQuery is "clears the current results, restarts pagination from
                // scratch". Whatever endReached we are holding describes the PREVIOUS query — or, on the
                // very first search, a cursor that was never queried at all and reports endReached=true
                // because there is nothing to page through yet. Carrying it over makes every search look
                // finished before it starts, so callers never paginate and an empty result is reported as
                // a definitive "no results". Reset before handing the query over; the subscription
                // corrects this the moment the SDK reports its real state.
                mutablePaginationState.value = MessageSearchPaginationState.Idle(endReached = false)
                // The SDK parses this with tantivy's strict query parser, where `:` and friends are
                // syntax rather than text — a pasted URL fails the whole search. Escape so the query is
                // searched literally. The caller keeps the raw string; only the SDK sees the escaped one.
                val substringQuery = ChineseSearchQuery.from(query)
                chineseQuery.value = substringQuery
                if (substringQuery == null) {
                    inner.setQuery(query.escapeForTantivy())
                } else {
                    // The SDK's default tokenizer indexes continuous Chinese as a single word. Read
                    // all indexed events, including long bodies omitted by its token length filter,
                    // and match locally. This uses the existing encrypted index and creates no store.
                    // Keep the raw diff list intact; filtering only happens at the exposure boundary.
                    scanningChineseQuery = true
                    mutablePaginationState.value = MessageSearchPaginationState.Loading
                    try {
                        inner.setQuery("*")
                        while (inner.paginationState() != SearchServicePaginationState.Idle(endReached = true)) {
                            currentCoroutineContext().ensureActive()
                            inner.paginate()
                            yield()
                        }
                    } finally {
                        scanningChineseQuery = false
                        mutablePaginationState.value = inner.paginationState().map()
                    }
                }
            }
        }
    }

    override suspend fun paginate(): Result<Unit> = withContext(dispatcher) {
        operationMutex.withLock {
            runCatchingExceptions {
                inner.paginate()
            }
        }
    }

    private suspend fun subscribeToResultsIfNeeded() {
        subscriptionMutex.withLock {
            if (subscribedToResults) return@withLock
            taskHandles += inner.subscribeToResults(
                object : SearchServiceResultsListener {
                    override fun onUpdate(updates: List<SearchServiceResultsUpdate>) {
                        this@RustMessageSearch.updates.trySend(updates)
                    }
                }
            )
            subscribedToResults = true
        }
    }
}

private fun MessageSearchResult.searchableBody(): String = when (val content = content) {
    is MessageContent -> {
        val attachment = content.type as? MessageTypeWithAttachment
        listOfNotNull(content.body, attachment?.filename, attachment?.caption).joinToString(" ")
    }
    is StickerContent -> content.bestDescription
    else -> ""
}

internal fun SearchServicePaginationState.map(): MessageSearchPaginationState = when (this) {
    is SearchServicePaginationState.Idle -> MessageSearchPaginationState.Idle(endReached = endReached)
    SearchServicePaginationState.Loading -> MessageSearchPaginationState.Loading
}
