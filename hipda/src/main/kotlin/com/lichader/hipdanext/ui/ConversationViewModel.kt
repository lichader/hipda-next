package com.lichader.hipdanext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.data.messages.MessageRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A private message conversation: loading it and clearing it. Sending goes through `SmsSender`. */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    sealed interface Event {
        /** The conversation loaded; [empty] if it had no messages. */
        data class Loaded(val empty: Boolean) : Event

        data class LoadFailed(val message: String) : Event

        /** The session expired and logging in again failed. */
        object NotLoggedIn : Event

        /** Clearing finished, with [message] to show either way, as before. */
        data class Cleared(val message: String, val failed: Boolean) : Event
    }

    private val _messages = MutableStateFlow<List<ListItem>>(emptyList())
    val messages: StateFlow<List<ListItem>> = _messages.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var uid = ""

    fun setUser(uid: String) {
        this.uid = uid
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val messages = repository.conversation(uid)
                if (messages.isNotEmpty()) _messages.value = messages
                _events.send(Event.Loaded(messages.isEmpty()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForumException.NotLoggedIn) {
                _messages.value = emptyList()
                _events.send(Event.NotLoggedIn)
            } catch (e: Exception) {
                _events.send(Event.LoadFailed(errors.format(e)))
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            val event = try {
                repository.clear(uid)
                Event.Cleared("操作完成", failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Event.Cleared("操作时发生错误 : " + errors.format(e), failed = true)
            }
            _events.send(event)
        }
    }
}
