package com.lichader.hipdanext.ui

import com.lichader.hipdanext.data.threads.ThreadListRepository
import com.lichader.hipdanext.data.threads.ThreadListResult
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.threads.ThreadSummary
import com.lichader.hipdanext.ui.ThreadListViewModel.Event
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeThreadListRepository()
    private val events = mutableListOf<Event>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(): ThreadListViewModel {
        val viewModel = ThreadListViewModel(repository, ForumErrorFormatter { true })
        viewModel.setForum(2)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return viewModel
    }

    private fun ThreadListViewModel.tids() = state.value.threads.map { it.tid }

    @Test
    fun laterPagesAppendWithoutDuplicatesAndRefreshStartsOver() = runTest(dispatcher) {
        repository.pages[1] = (1..10).map(Int::toString)
        repository.pages[2] = (9..20).map(Int::toString)
        val viewModel = createViewModel()

        viewModel.start()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals((1..20).map(Int::toString), viewModel.tids())

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals((1..10).map(Int::toString), viewModel.tids())
        assertEquals(listOf(Event.Loaded(1), Event.Loaded(2), Event.Loaded(1)), events)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun shortListsLoadFollowingPagesUpToPageSix() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4, 5, 6), repository.requested)
        assertEquals(Event.FirstPageEmpty, events[1])
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun aFailedPageKeepsTheListAndIsAskedForAgain() = runTest(dispatcher) {
        repository.pages[1] = (1..10).map(Int::toString)
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        repository.error = ForumException.Network(UnknownHostException())
        viewModel.loadMore()
        advanceUntilIdle()
        repository.error = null
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 2), repository.requested)
        assertEquals(Event.Failed("请检查网络连接."), events[1])
        assertEquals(10, viewModel.tids().size)
    }

    @Test
    fun anUnparsablePageSaysSo() = runTest(dispatcher) {
        repository.error = ForumException.Parse("no rows")
        createViewModel().start()
        advanceUntilIdle()

        assertEquals(listOf(Event.Failed("页面加载失败")), events)
    }

    @Test
    fun anExpiredSessionClearsTheList() = runTest(dispatcher) {
        repository.pages[1] = (1..10).map(Int::toString)
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        repository.error = ForumException.NotLoggedIn()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ThreadListViewModel.UiState(), viewModel.state.value)
        assertEquals(Event.NotLoggedIn, events.last())
    }

    @Test
    fun anUpdatedThreadShowsItsNewTitleAndReplies() = runTest(dispatcher) {
        repository.pages[1] = (1..10).map(Int::toString)
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateThread("3", "new title", 7)

        val thread = viewModel.state.value.threads[2]
        assertEquals("new title" to "7", thread.title to thread.replyCount)
    }

    private class FakeThreadListRepository : ThreadListRepository {
        val pages = mutableMapOf<Int, List<String>>()
        val requested = mutableListOf<Int>()
        var error: Exception? = null
        override suspend fun page(forumId: Int, page: Int): ThreadListResult {
            requested += page
            error?.let { throw it }
            return ThreadListResult(pages[page].orEmpty().map(::summary), learnedUid = false)
        }

        private fun summary(tid: String) = ThreadSummary(
            tid = tid, title = "T", titleColor = null, type = null, isNew = false, sticky = false, poll = false,
            author = "user_1", authorId = "200001", timeCreate = "2026-1-1", timeUpdate = null, replyCount = "0",
            viewCount = "0", lastPost = "user_1", withPictures = false, maxPage = 1,
        )
    }
}
