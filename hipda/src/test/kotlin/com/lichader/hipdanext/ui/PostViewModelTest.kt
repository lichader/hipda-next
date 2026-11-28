package com.lichader.hipdanext.ui

import com.lichader.hipdanext.async.PostHelper
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.forum.post.PostForm
import com.lichader.hipdanext.forum.post.PostResult
import com.lichader.hipdanext.forum.post.PostTarget
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import org.junit.runner.RunWith
import com.lichader.hipdanext.async.UploadImgHelper
import com.lichader.hipdanext.ui.PostViewModel.EditorEvent
import com.lichader.hipdanext.ui.PostViewModel.UploadEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PostViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePostRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theEditorOrWhyNot() = runTest(dispatcher) {
        val viewModel = PostViewModel(repository, ForumErrorFormatter { true }) { _, _, _, _, _ -> error("not used") }
        val events = mutableListOf<EditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.editorEvents.toList(events) }

        viewModel.loadEditor(PostHelper.MODE_QUOTE_POST, 2, "3000001", "4000001")
        advanceUntilIdle()
        repository.error = ForumException.RealNameRequired()
        viewModel.loadEditor(PostHelper.MODE_REPLY_THREAD, 2, "3000001", null)
        advanceUntilIdle()

        assertEquals(listOf(EditorKind.Quote("3000001", "4000001"), EditorKind.Reply("3000001")), repository.kinds)
        assertEquals("[quote]q[/quote]", (events[0] as EditorEvent.Loaded).info.quoteText)
        assertEquals("需要通过网页完成实名验证才可以发帖", (events[1] as EditorEvent.Failed).message)
    }

    @Test
    fun imagesAreUploadedOneByOne() = runTest(dispatcher) {
        val uris = listOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
        val viewModel = PostViewModel(repository, ForumErrorFormatter { true }) { listener, uid, hash, files, original ->
            assertEquals(listOf("100001", "h", files.toList(), true), listOf(uid, hash, uris, original))
            val entry = SingletonEntryPoint.get()
            object : UploadImgHelper(ApplicationProvider.getApplicationContext(), entry.settings(), null, null, listener, uid, hash, files, original) {
                override fun upload() {
                    listener.updateProgress(2, 0, -1)
                    listener.itemComplete(files[0], 2, 0, "", "", "9000001", null)
                    listener.updateProgress(2, 1, -1)
                    listener.itemComplete(files[1], 2, 1, "无效上传图片ID", "原图限制", null, null)
                }
            }
        }

        viewModel.uploadImages("100001", "h", uris, true)
        val events = withContext(Dispatchers.Default) { withTimeout(5000) { viewModel.uploadEvents.take(5).toList() } }

        assertEquals(listOf(0, 1), events.filterIsInstance<UploadEvent.Progress>().map { it.current })
        val done = events.filterIsInstance<UploadEvent.ImageDone>()
        assertEquals(listOf("9000001", null), done.map { it.image.imgId })
        assertEquals(uris, done.map { it.image.uri })
        assertEquals(listOf("", "无效上传图片ID"), done.map { it.message })
        assertEquals(UploadEvent.AllDone, events.last())
    }

    private class FakePostRepository : PostRepository {
        val kinds = mutableListOf<EditorKind>()
        var error: Exception? = null
        override suspend fun editor(kind: EditorKind): EditorPage {
            kinds += kind
            error?.let { throw it }
            return EditorPage("f", "[quote]q[/quote]", null, null, null, false, null, null, null, null, emptyList(), emptyList(), emptyMap(), null)
        }
        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray) = ImageUpload.Uploaded("1")
        override suspend fun submit(target: PostTarget, form: PostForm): PostResult = throw UnsupportedOperationException()
    }
}
