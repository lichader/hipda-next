package com.lichader.hipdanext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.data.threads.ThreadListRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.threads.ThreadSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A forum's thread list: its pages, paging and loading state. */
@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val repository: ThreadListRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    data class UiState(val threads: List<ThreadSummary> = emptyList(), val loading: Boolean = false)

    sealed interface Event {
        /** Page [page] loaded; page 1 replaced the list. */
        data class Loaded(val page: Int) : Event

        data class Failed(val message: String) : Event

        /** The session expired and logging in again failed. */
        object NotLoggedIn : Event

        /** The account's uid became known; the account header can show it. */
        object UidLearned : Event

        /** The first page showed no threads; following pages load automatically. */
        object FirstPageEmpty : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var forumId = 0
    private var page = 1
    private var loadJob: Job? = null

    val isLoading: Boolean get() = _state.value.loading

    fun setForum(forumId: Int) {
        this.forumId = forumId
    }

    /** Loads the first page unless the list has threads or is loading. */
    fun start() {
        if (_state.value.threads.isEmpty() && !isLoading) load(1)
    }

    fun refresh() {
        loadJob?.cancel()
        load(1)
    }

    fun loadMore() {
        if (!isLoading) load(page + 1)
    }

    /** Shows a thread's new title and reply count, after it was read or replied to. */
    fun updateThread(tid: String, title: String, replyCount: Int) {
        _state.update { state ->
            state.copy(threads = state.threads.map { if (it.tid == tid) it.copy(title = title, replyCount = replyCount.toString()) else it })
        }
    }

    fun clear() {
        loadJob?.cancel()
        _state.value = UiState()
    }

    private fun load(page: Int) {
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch { fetch(page) }
    }

    private suspend fun fetch(page: Int) {
        try {
            val result = repository.page(forumId, page)
            this.page = page
            val old = _state.value.threads
            val threads = if (page == 1) result.threads else old + result.threads.filter { new -> old.none { it.tid == new.tid } }
            _state.value = UiState(threads, loading = page <= AUTO_LOAD_PAGES && threads.size < MIN_THREADS)
            if (result.learnedUid) _events.send(Event.UidLearned)
            _events.send(Event.Loaded(page))
            // Fill a short list, as when many sticky or blacklisted threads were left out.
            if (page <= AUTO_LOAD_PAGES && threads.size < MIN_THREADS) {
                if (page == 1 && threads.isEmpty()) _events.send(Event.FirstPageEmpty)
                fetch(page + 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForumException.NotLoggedIn) {
            _state.value = UiState()
            _events.send(Event.NotLoggedIn)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false) }
            _events.send(Event.Failed(if (e is ForumException.Parse) "页面加载失败" else errors.format(e)))
        }
    }

    private companion object {
        const val MIN_THREADS = 10
        const val AUTO_LOAD_PAGES = 5
    }
}
