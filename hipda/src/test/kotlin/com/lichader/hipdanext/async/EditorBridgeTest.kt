package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.ui.ForumErrorFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EditorBridgeTest {

    private val repository = FakePostRepository()
    private val bridge = EditorBridge(repository, ForumErrorFormatter { true })

    @Test
    fun theEditorOrWhyNot() {
        val loaded = bridge.editor(PostHelper.MODE_EDIT_POST, 2, "3000001", "4000001")
        assertEquals(listOf("f", null), listOf(loaded.info?.formhash, loaded.message))
        assertEquals(EditorKind.Edit(2, "3000001", "4000001"), repository.kind)

        repository.error = ForumException.Server("对不起，您没有权限")
        val refused = bridge.editor(PostHelper.MODE_EDIT_POST, 2, "3000001", "4000001")
        assertNull(refused.info)
        assertEquals("对不起，您没有权限", refused.message)
    }

    @Test
    fun uploadFailuresAreThrown() {
        assertEquals(ImageUpload.Uploaded("9000001"), bridge.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)))
        repository.error = ForumException.Http(413, "Too Large")
        assertThrows(ForumException.Http::class.java) { bridge.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)) }
    }

    private class FakePostRepository : PostRepository {
        var kind: EditorKind? = null
        var error: Exception? = null
        override suspend fun editor(kind: EditorKind): EditorPage {
            this.kind = kind
            error?.let { throw it }
            return EditorPage("f", null, null, null, null, false, null, null, null, null, emptyList(), emptyList(), emptyMap(), null)
        }
        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload {
            error?.let { throw it }
            return ImageUpload.Uploaded("9000001")
        }
    }
}
