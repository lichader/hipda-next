package com.lichader.hipdanext.async

import androidx.annotation.WorkerThread
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.ImageUpload
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** Image uploads for `UploadImgHelper`, which prepares images on a worker thread (rule 7). Calls block. */
@Singleton
class EditorBridge @Inject constructor(
    private val repository: PostRepository,
) {

    /** @throws ForumException if the upload fails */
    @WorkerThread
    fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload =
        runBlocking { repository.uploadImage(uid, hash, fileName, mimeType, bytes) }
}
