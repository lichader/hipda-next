package com.lichader.hipdanext.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.ui.ForumErrorFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The blacklist screen: loads the forum's blacklist and removes users from it. */
@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val repository: BlacklistRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    data class UiState(
        /** The blacklisted usernames, in the forum's order. */
        val usernames: List<String> = emptyList(),
        /** Users whose removal has been sent but not yet confirmed. */
        val removing: Set<String> = emptySet(),
        val refreshing: Boolean = false,
        /** True once a fetch has finished, successfully or not. */
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /**
     * Messages to show the user once, such as errors. Messages sent while the screen is not
     * collecting are kept and delivered when it collects again.
     */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _state.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            try {
                val usernames = repository.sync()
                _state.update { it.copy(usernames = usernames, refreshing = false, loaded = true) }
                delay(SYNCED_MESSAGE_DELAY_MS)
                _messages.send(SYNCED_MESSAGE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, loaded = true) }
                val text = errors.format(e)
                _messages.send(if (e is ForumException.Server) text else "获取黑名单发生错误 : $text")
            }
        }
    }

    fun remove(username: String) {
        if (username in _state.value.removing) return
        _state.update { it.copy(removing = it.removing + username) }
        viewModelScope.launch {
            try {
                repository.remove(username)
                val wasListed = username in _state.value.usernames
                _state.update { it.copy(usernames = it.usernames - username, removing = it.removing - username) }
                // The list on screen was out of date, so show the forum's current one.
                if (!wasListed) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(removing = it.removing - username) }
                _messages.send(errors.format(e))
            }
        }
    }

    private companion object {
        const val SYNCED_MESSAGE = "黑名单数据已同步"

        /** The old screen showed the message this long after the list appeared. */
        const val SYNCED_MESSAGE_DELAY_MS = 200L
    }
}
