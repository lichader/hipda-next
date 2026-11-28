package com.lichader.hipdanext.ui

import com.lichader.hipdanext.data.lists.SimpleListRepository
import com.lichader.hipdanext.data.lists.SimpleListResult
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.job.SimpleListJob
import com.lichader.hipdanext.ui.SimpleListViewModel.Event
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSimpleListRepository()
    private val events = mutableListOf<Event>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(type: Int = SimpleListJob.TYPE_MYREPLY): SimpleListViewModel {
        val viewModel = SimpleListViewModel(repository, ForumErrorFormatter { true })
        viewModel.init(type)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return viewModel
    }

    private fun SimpleListViewModel.tids() = state.value.items.map { it.tid }

    @Test
    fun pagesAppendUntilTheLastPage() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1", "2"), 2 to listOf("3"))
        repository.maxPage = 2
        val viewModel = createViewModel()

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.loadMore())
        advanceUntilIdle()

        assertEquals(listOf("1", "2", "3"), viewModel.tids())
        assertFalse(viewModel.loadMore())
        assertEquals(listOf(Event.Loaded(empty = false), Event.Loaded(empty = false)), events)
        assertEquals("a1b2c3d4", viewModel.state.value.formhash)
    }

    @Test
    fun anEmptyFirstPageEmptiesTheList() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1"))
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        repository.pages = emptyMap()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.tids())
        assertEquals(Event.Loaded(empty = true), events.last())
    }

    @Test
    fun aFailedPageKeepsTheListAndIsAskedForAgain() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1"), 2 to listOf("2"))
        repository.maxPage = 2
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        repository.error = ForumException.Network(UnknownHostException())
        viewModel.loadMore()
        advanceUntilIdle()
        repository.error = null
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 2), repository.requested.map { it.second })
        assertEquals(Event.Failed("请检查网络连接."), events[1])
        assertEquals(listOf("1", "2"), viewModel.tids())
    }

    @Test
    fun anExpiredSessionEmptiesTheList() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1"))
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        repository.error = ForumException.NotLoggedIn()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(SimpleListViewModel.UiState(), viewModel.state.value)
        assertEquals(Event.NotLoggedIn, events.last())
    }

    @Test
    fun newPostsUseTheSearchOfTheFirstPage() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1"), 2 to listOf("2"))
        repository.maxPage = 2
        repository.searchId = "257"
        val viewModel = createViewModel(SimpleListJob.TYPE_NEW_POSTS)

        viewModel.start()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(Triple(SimpleListJob.TYPE_NEW_POSTS, 1, null), Triple(SimpleListJob.TYPE_NEW_POSTS, 2, "257")),
            repository.requested.map { Triple(it.first, it.second, it.third) })
    }

    @Test
    fun switchingFromFavoritesToAttentionReloads() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1"))
        val viewModel = createViewModel(SimpleListJob.TYPE_FAVORITES)
        viewModel.start()
        advanceUntilIdle()

        viewModel.switchType(SimpleListJob.TYPE_ATTENTION)
        viewModel.init(SimpleListJob.TYPE_FAVORITES)
        advanceUntilIdle()

        assertEquals(SimpleListJob.TYPE_ATTENTION, viewModel.type)
        assertEquals(SimpleListJob.TYPE_ATTENTION, repository.requested.last().first)
    }

    @Test
    fun aRemovedFavoriteLeavesTheList() = runTest(dispatcher) {
        repository.pages = mapOf(1 to listOf("1", "2"))
        val viewModel = createViewModel(SimpleListJob.TYPE_FAVORITES)
        viewModel.start()
        advanceUntilIdle()

        viewModel.remove("1")
        assertEquals(listOf("2"), viewModel.tids())

        viewModel.remove("9")
        advanceUntilIdle()
        assertEquals(2, repository.requested.size)
    }

    private class FakeSimpleListRepository : SimpleListRepository {
        var pages = emptyMap<Int, List<String>>()
        var maxPage = 1
        var searchId: String? = null
        var error: Exception? = null
        val requested = mutableListOf<Triple<Int, Int, String?>>()

        override suspend fun page(type: Int, page: Int, searchId: String?): SimpleListResult {
            requested += Triple(type, page, searchId)
            error?.let { throw it }
            return SimpleListResult(pages[page].orEmpty().map { ListItem(tid = it) }, maxPage, this.searchId, "a1b2c3d4")
        }
    }
}
