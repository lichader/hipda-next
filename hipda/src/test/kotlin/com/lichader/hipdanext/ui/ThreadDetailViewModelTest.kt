package com.lichader.hipdanext.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.job.ThreadUpdatedEvent
import com.lichader.hipdanext.ui.ThreadDetailFragment.FETCH_NEXT
import com.lichader.hipdanext.ui.ThreadDetailFragment.FETCH_REFRESH
import com.lichader.hipdanext.ui.ThreadDetailFragment.LAST_PAGE
import com.lichader.hipdanext.ui.ThreadDetailFragment.POSITION_FOOTER
import com.lichader.hipdanext.ui.ThreadDetailFragment.POSITION_NORMAL
import com.lichader.hipdanext.ui.ThreadDetailViewModel.Companion.FIND_AUTHOR_ID
import com.lichader.hipdanext.ui.ThreadDetailViewModel.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ThreadDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeThreadRepository()
    private val events = mutableListOf<Event>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        EventBus.getDefault().removeStickyEvent(ThreadUpdatedEvent::class.java)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(): ThreadDetailViewModel {
        val viewModel = ThreadDetailViewModel(repository, SingletonEntryPoint.get().settings(), ForumErrorFormatter { true })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return viewModel
    }

    @Test
    fun aPostsPageTheLastPageOrAPageByNumber() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.load("1", null, "1002", 3, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()
        viewModel.load("1", null, null, LAST_PAGE, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()
        viewModel.load("1", "200001", null, 3, FETCH_NEXT, POSITION_FOOTER)
        advanceUntilIdle()

        assertEquals(listOf("pageOfPost 1002 1", "lastPage 1", "page 1 3 200001"), repository.requests)
        assertEquals(listOf(FETCH_REFRESH, FETCH_REFRESH, FETCH_NEXT), events.map { it.fetchType })
        assertEquals(listOf(POSITION_NORMAL, POSITION_NORMAL, POSITION_FOOTER), events.map { it.loadingPosition })
    }

    @Test
    fun aLoadedPageIsKept() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.load("1", null, null, 2, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()

        val loaded = events.single() as Event.Loaded
        assertEquals(listOf("1", 2, 1), listOf(loaded.data.tid, loaded.data.page, loaded.data.count))
        assertSame(loaded.data, viewModel.cache.get(2))
    }

    @Test
    fun theThreadAuthorIsFoundFirstWhenAsked() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.load("1", FIND_AUTHOR_ID, null, 1, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()
        repository.authorError = ForumException.Network(UnknownHostException())
        viewModel.load("1", FIND_AUTHOR_ID, null, 1, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()

        assertEquals(listOf("authorId 1", "page 1 1 200001", "authorId 1", "page 1 1 "), repository.requests)
        assertEquals(listOf("200001", ""), events.map { (it as Event.Loaded).authorId })
    }

    @Test
    fun failuresAsBefore() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val failures = listOf(null, ForumException.Parse("no posts"), ForumException.Network(UnknownHostException()), ForumException.NotLoggedIn())

        repository.posts = 0
        for (failure in failures) {
            repository.error = failure
            viewModel.load("1", null, null, 1, FETCH_REFRESH, POSITION_NORMAL)
            advanceUntilIdle()
        }

        assertEquals(listOf("页面加载失败", "页面加载失败", "请检查网络连接."), events.take(3).map { (it as Event.Failed).message })
        assertEquals(Event.NotLoggedIn::class.java, events[3].javaClass)
        assertNull(viewModel.cache.get(1))
    }

    @Test
    fun theLastPageTellsTheThreadListItsReplies() = runTest(dispatcher) {
        val viewModel = createViewModel()
        repository.posts = 3

        viewModel.load("1", "200001", null, 2, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()
        assertNull(EventBus.getDefault().getStickyEvent(ThreadUpdatedEvent::class.java))

        viewModel.load("1", null, null, 2, FETCH_REFRESH, POSITION_NORMAL)
        advanceUntilIdle()
        val update = EventBus.getDefault().removeStickyEvent(ThreadUpdatedEvent::class.java)
        assertEquals(listOf(2, "1", "主题", 2), listOf(update.mFid, update.mTid, update.mTitle, update.mReplyCount))
    }

    private class FakeThreadRepository : ThreadRepository {
        val requests = mutableListOf<String>()
        var posts = 1
        var error: Exception? = null
        var authorError: Exception? = null

        private fun page(request: String, page: Int): ThreadPage {
            requests += request
            error?.let { throw it }
            val posts = List(posts) { Post("${1001 + it}", it + 1, "发表于 2026-11-28 10:00", "200001", "user_1", false, null, null, null, emptyList()) }
            return ThreadPage("1", 2, "主题", page, 2, posts, null, PageHeader(null, null, 0))
        }

        override suspend fun page(tid: String, page: Int, authorId: String?) = page("page $tid $page ${authorId.orEmpty()}", page)
        override suspend fun lastPage(tid: String) = page("lastPage $tid", 2)
        override suspend fun pageOfPost(pid: String, tid: String?) = page("pageOfPost $pid $tid", 1)
        override suspend fun authorId(tid: String): String {
            requests += "authorId $tid"
            authorError?.let { throw it }
            return "200001"
        }
        override suspend fun read(html: String, tid: String?) = throw UnsupportedOperationException()
    }
}
