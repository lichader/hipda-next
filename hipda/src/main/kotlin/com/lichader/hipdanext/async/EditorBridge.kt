package com.lichader.hipdanext.async

import androidx.annotation.WorkerThread
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.ui.EditorBeans
import com.lichader.hipdanext.ui.ForumErrorFormatter
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** An editor load: the editor, or why it could not be read. */
class EditorLoad(val info: PrePostInfoBean?, val message: String?)

/**
 * The post editor and image uploads for `PostHelper`, `PostJob` and `UploadImgHelper`, which run
 * on worker threads (rule 7), until posting moves to view models (W9b). Calls block.
 */
@Singleton
class EditorBridge @Inject constructor(
    private val repository: PostRepository,
    private val errors: ForumErrorFormatter,
) {

    /** The editor for a post in [mode] (`PostHelper.MODE_*`). */
    @WorkerThread
    fun editor(mode: Int, fid: Int, tid: String?, pid: String?): EditorLoad = try {
        EditorLoad(EditorBeans.of(runBlocking { repository.editor(EditorBeans.kind(mode, fid, tid, pid)) }, mode), null)
    } catch (e: Exception) {
        EditorLoad(null, errors.format(e))
    }

    /** @throws ForumException if the upload fails */
    @WorkerThread
    fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload =
        runBlocking { repository.uploadImage(uid, hash, fileName, mimeType, bytes) }
}
