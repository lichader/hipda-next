package com.lichader.hipdanext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.data.lists.SimpleListRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
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

/** One of the simple lists (`SimpleListJob`'s types): its pages and loading state. */
@HiltViewModel
class SimpleListViewModel @Inject constructor(
    private val repository: SimpleListRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    data class UiState(
        val items: List<ListItem> = emptyList(),
        val loading: Boolean = false,
        /** The last page's form token, for removing a favorite. */
        val formhash: String? = null,
    )

    sealed interface Event {
        /** A page loaded; [empty] if it had no items. */
        data class Loaded(val empty: Boolean) : Event

        data class Failed(val message: String) : Event

        /** The session expired and logging in again failed. */
        object NotLoggedIn : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** The list's type; set once by [init], changed by [switchType]. */
    var type = NO_TYPE
        private set
    private var page = 1
    private var maxPage = 0
    private var searchId: String? = null
    private var loadJob: Job? = null

    val isLoading: Boolean get() = _state.value.loading

    /** Sets the list's type, unless the view model already has one (after a configuration change). */
    fun init(type: Int) {
        if (this.type == NO_TYPE) this.type = type
    }

    /** Shows another list, such as attention instead of favorites. */
    fun switchType(type: Int) {
        loadJob?.cancel()
        this.type = type
        _state.value = UiState()
        refresh()
    }

    /** Loads the first page unless the list has items or is loading. */
    fun start() {
        if (_state.value.items.isEmpty() && !isLoading) load(1)
    }

    fun refresh() {
        loadJob?.cancel()
        maxPage = 0
        load(1)
    }

    /** Loads the next page; false if the list is on its last page. */
    fun loadMore(): Boolean {
        if (page >= maxPage) return false
        if (!isLoading) load(page + 1)
        return true
    }

    /** Removes the thread [tid] from the list, after it was removed from favorites; reloads if it isn't there. */
    fun remove(tid: String) {
        val items = _state.value.items
        if (items.none { it.tid == tid }) refresh() else _state.update { it.copy(items = items.filter { item -> item.tid != tid }) }
    }

    private fun load(page: Int) {
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch { fetch(page) }
    }

    private suspend fun fetch(page: Int) {
        try {
            val result = repository.page(type, page, searchId)
            this.page = page
            val old = _state.value.items
            if (result.items.isEmpty()) {
                _state.value = UiState(if (page == 1) emptyList() else old, formhash = result.formhash)
                _events.send(Event.Loaded(empty = true))
                return
            }
            // A search ID comes only with new posts; the other lists keep none.
            searchId = result.searchId
            maxPage = result.maxPage
            _state.value = UiState(if (page == 1) result.items else old + result.items, formhash = result.formhash)
            _events.send(Event.Loaded(empty = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForumException.NotLoggedIn) {
            _state.value = UiState()
            _events.send(Event.NotLoggedIn)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false) }
            _events.send(Event.Failed(errors.format(e)))
        }
    }

    private companion object {
        const val NO_TYPE = -1
    }
}
