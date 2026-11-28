package com.lichader.hipdanext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.data.post.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The post editor screen: loads the forum's editor. Posting itself moves here in W9b. */
@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    sealed interface EditorEvent {
        class Loaded(val info: PrePostInfoBean) : EditorEvent

        class Failed(val message: String) : EditorEvent
    }

    private val _editorEvents = Channel<EditorEvent>(Channel.BUFFERED)
    val editorEvents: Flow<EditorEvent> = _editorEvents.receiveAsFlow()

    /** Loads the editor for a post in [mode] (`PostHelper.MODE_*`). */
    fun loadEditor(mode: Int, fid: Int, tid: String?, pid: String?) {
        viewModelScope.launch {
            val event = try {
                EditorEvent.Loaded(EditorBeans.of(repository.editor(EditorBeans.kind(mode, fid, tid, pid)), mode))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EditorEvent.Failed(errors.format(e))
            }
            _editorEvents.send(event)
        }
    }
}
