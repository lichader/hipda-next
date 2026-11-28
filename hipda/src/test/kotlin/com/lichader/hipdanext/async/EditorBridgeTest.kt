package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.forum.post.PostForm
import com.lichader.hipdanext.forum.post.PostResult
import com.lichader.hipdanext.forum.post.PostTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EditorBridgeTest {

    private val repository = FakePostRepository()
    private val bridge = EditorBridge(repository)

    @Test
    fun uploadFailuresAreThrown() {
        assertEquals(ImageUpload.Uploaded("9000001"), bridge.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)))
        repository.error = ForumException.Http(413, "Too Large")
        assertThrows(ForumException.Http::class.java) { bridge.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)) }
    }

    private class FakePostRepository : PostRepository {
        var error: Exception? = null
        override suspend fun editor(kind: EditorKind): EditorPage = throw UnsupportedOperationException()
        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload {
            error?.let { throw it }
            return ImageUpload.Uploaded("9000001")
        }
        override suspend fun submit(target: PostTarget, form: PostForm): PostResult = throw UnsupportedOperationException()
    }
}
