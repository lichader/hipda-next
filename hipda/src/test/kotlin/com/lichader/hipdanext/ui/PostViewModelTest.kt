package com.lichader.hipdanext.ui

import com.lichader.hipdanext.async.PostHelper
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.ui.PostViewModel.EditorEvent
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
class PostViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePostRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theEditorOrWhyNot() = runTest(dispatcher) {
        val viewModel = PostViewModel(repository, ForumErrorFormatter { true })
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

    private class FakePostRepository : PostRepository {
        val kinds = mutableListOf<EditorKind>()
        var error: Exception? = null
        override suspend fun editor(kind: EditorKind): EditorPage {
            kinds += kind
            error?.let { throw it }
            return EditorPage("f", "[quote]q[/quote]", null, null, null, false, null, null, null, null, emptyList(), emptyList(), emptyMap(), null)
        }
        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray) = ImageUpload.Uploaded("1")
    }
}
