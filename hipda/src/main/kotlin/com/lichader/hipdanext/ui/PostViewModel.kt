package com.lichader.hipdanext.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.async.UploadImgHelper
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.job.UploadImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The post editor screen: loads the forum's editor and uploads images. Posts are sent by
 * `PostSender`, because the screen closes as it sends.
 */
@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository,
    private val errors: ForumErrorFormatter,
    private val uploaders: UploadImgHelper.Factory,
) : ViewModel() {

    sealed interface EditorEvent {
        class Loaded(val info: PrePostInfoBean) : EditorEvent

        class Failed(val message: String) : EditorEvent
    }

    sealed interface UploadEvent {
        /** Image [current] (from 0) of [total] is being prepared and sent. */
        class Progress(val total: Int, val current: Int) : UploadEvent

        /** An image was sent; it has an ID if it was uploaded, else [message] and [detail] say why not. */
        class ImageDone(val image: UploadImage, val message: String?, val detail: String?) : UploadEvent

        object AllDone : UploadEvent
    }

    private val _editorEvents = Channel<EditorEvent>(Channel.BUFFERED)
    val editorEvents: Flow<EditorEvent> = _editorEvents.receiveAsFlow()

    private val _uploadEvents = Channel<UploadEvent>(Channel.UNLIMITED)
    val uploadEvents: Flow<UploadEvent> = _uploadEvents.receiveAsFlow()

    /**
     * Prepares and uploads [uris] one by one with the editor's [uid] and upload [hash], as the
     * old `ImageUploadJob` did; [original] sends images unchanged where the forum allows.
     */
    fun uploadImages(uid: String?, hash: String?, uris: List<Uri>, original: Boolean) {
        val listener = object : UploadImgHelper.UploadImgListener {
            override fun updateProgress(total: Int, current: Int, percentage: Int) {
                _uploadEvents.trySend(UploadEvent.Progress(total, current))
            }

            override fun itemComplete(uri: Uri, total: Int, current: Int, message: String?, detail: String?, imgId: String?, thumbtail: Bitmap?) {
                val image = UploadImage().apply {
                    setImgId(imgId)
                    setThumb(thumbtail)
                    setUri(uri)
                }
                _uploadEvents.trySend(UploadEvent.ImageDone(image, message, detail))
            }
        }
        // UploadImgHelper reads files, compresses and sends, blocking.
        viewModelScope.launch(Dispatchers.IO) {
            uploaders.create(listener, uid, hash, uris.toTypedArray(), original).upload()
            _uploadEvents.trySend(UploadEvent.AllDone)
        }
    }

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
