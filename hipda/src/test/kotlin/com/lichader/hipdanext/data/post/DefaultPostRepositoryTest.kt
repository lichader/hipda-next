package com.lichader.hipdanext.data.post

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.forum.post.PostApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultPostRepositoryTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val account = FakeAccount()
    private val api = FakePostApi()
    private val repository = DefaultPostRepository(api, settings, account)

    @Test
    fun theLargestUploadIsRememberedWhenTheEditorSays() = runBlocking {
        settings.maxUploadFileSize = 1000
        api.maxUploadSize = null
        repository.editor(EditorKind.Reply("1"))
        assertEquals(1000, settings.maxUploadFileSize)

        api.maxUploadSize = 8 * 1024 * 1024
        repository.editor(EditorKind.Reply("1"))
        assertEquals(8 * 1024 * 1024, settings.maxUploadFileSize)
    }

    @Test
    fun anEditorReadAcrossAnAccountChangeIsDropped() {
        settings.maxUploadFileSize = 1000
        api.maxUploadSize = 2048
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.editor(EditorKind.Reply("1")) } }
        assertEquals(1000, settings.maxUploadFileSize)
    }

    @Test
    fun uploadsGoToTheForum() = runBlocking {
        assertEquals(ImageUpload.Uploaded("9000001"), repository.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)))
        assertEquals("100001 h a.png image/png 1", api.upload)
    }

    private class FakePostApi : PostApi {
        var maxUploadSize: Int? = null
        var duringRequest: () -> Unit = {}
        var upload: String? = null
        override suspend fun editor(kind: EditorKind): EditorPage {
            duringRequest()
            return EditorPage("f", "", "100001", "h", null, false, maxUploadSize, null, null, null, emptyList(), emptyList(), emptyMap(), null)
        }
        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload {
            upload = "$uid $hash $fileName $mimeType ${bytes.size}"
            return ImageUpload.Uploaded("9000001")
        }
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
