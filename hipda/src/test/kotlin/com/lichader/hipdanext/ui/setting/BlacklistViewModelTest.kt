package com.lichader.hipdanext.ui.setting

import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.ui.setting.BlacklistViewModel.UiState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class BlacklistViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBlacklistRepository()
    private val messages = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(): BlacklistViewModel {
        val viewModel = BlacklistViewModel(repository, ForumErrorFormatter { true })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.toList(messages) }
        return viewModel
    }

    @Test
    fun loadsTheBlacklistWhenCreated() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.refreshing)

        advanceUntilIdle()

        assertEquals(UiState(usernames = listOf("alice", "bob"), loaded = true), viewModel.state.value)
        assertEquals(listOf("黑名单数据已同步"), messages)
    }

    @Test
    fun forumErrorsAreShownAsTheForumWordsThem() = runTest(dispatcher) {
        repository.syncError = ForumException.Server("对不起，您还未登录")
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(UiState(loaded = true), viewModel.state.value)
        assertEquals(listOf("对不起，您还未登录"), messages)
    }

    @Test
    fun requestFailuresSayThatLoadingFailed() = runTest(dispatcher) {
        repository.syncError = ForumException.Network(UnknownHostException())
        createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("获取黑名单发生错误 : 请检查网络连接."), messages)
    }

    @Test
    fun refreshWhileLoadingDoesNotLoadTwice() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, repository.syncCalls)
    }

    @Test
    fun removedUserLeavesTheList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.remove("alice")
        assertEquals(setOf("alice"), viewModel.state.value.removing)
        advanceUntilIdle()

        assertEquals(UiState(usernames = listOf("bob"), loaded = true), viewModel.state.value)
        assertEquals(listOf("alice"), repository.removed)
    }

    @Test
    fun secondRemoveOfTheSameUserIsIgnored() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.remove("alice")
        viewModel.remove("alice")
        advanceUntilIdle()

        assertEquals(listOf("alice"), repository.removed)
    }

    @Test
    fun failedRemoveKeepsTheUserAndShowsWhy() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.removeError = ForumException.Server("操作失败")

        viewModel.remove("alice")
        advanceUntilIdle()

        assertEquals(UiState(usernames = listOf("alice", "bob"), loaded = true), viewModel.state.value)
        assertEquals("操作失败", messages.last())
    }

    @Test
    fun removingAUserTheScreenDidNotListReloads() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.remove("carol")
        advanceUntilIdle()

        assertEquals(2, repository.syncCalls)
    }

    private class FakeBlacklistRepository : BlacklistRepository {
        var usernames = listOf("alice", "bob")
        var syncError: Exception? = null
        var removeError: Exception? = null
        var syncCalls = 0
        val removed = mutableListOf<String>()

        override suspend fun sync(): List<String> {
            syncCalls++
            syncError?.let { throw it }
            return usernames
        }

        override suspend fun add(username: String) {
            usernames = usernames + username
        }

        override suspend fun remove(username: String) {
            removeError?.let { throw it }
            removed += username
            usernames = usernames - username
        }
    }
}
