package com.lichader.hipdanext.ui

import com.lichader.hipdanext.data.messages.MessageRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.messages.Recipient
import com.lichader.hipdanext.forum.messages.SendResult
import com.lichader.hipdanext.ui.ConversationViewModel.Event
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
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMessageRepository()
    private val events = mutableListOf<Event>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(): ConversationViewModel {
        val viewModel = ConversationViewModel(repository, ForumErrorFormatter { true })
        viewModel.setUser("200001")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return viewModel
    }

    @Test
    fun loadsTheConversationWithTheUser() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf("200001"), repository.loaded)
        assertEquals(listOf("消息 1"), viewModel.messages.value.map { it.info })
        assertEquals(listOf(Event.Loaded(empty = false)), events)
    }

    @Test
    fun anEmptyConversationKeepsWhatWasShown() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.refresh()
        advanceUntilIdle()

        repository.messages = emptyList()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, viewModel.messages.value.size)
        assertEquals(Event.Loaded(empty = true), events.last())
    }

    @Test
    fun failuresAndAnExpiredSession() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.refresh()
        advanceUntilIdle()

        repository.error = ForumException.Network(UnknownHostException())
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(Event.LoadFailed("请检查网络连接."), events.last())
        assertEquals(1, viewModel.messages.value.size)

        repository.error = ForumException.NotLoggedIn()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(Event.NotLoggedIn, events.last())
        assertEquals(emptyList<ListItem>(), viewModel.messages.value)
    }

    @Test
    fun clearingReportsEitherWay() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.clear()
        advanceUntilIdle()
        repository.error = ForumException.Network(UnknownHostException())
        viewModel.clear()
        advanceUntilIdle()

        assertEquals(listOf("200001", "200001"), repository.cleared)
        assertEquals(listOf(Event.Cleared("操作完成", failed = false), Event.Cleared("操作时发生错误 : 请检查网络连接.", failed = true)), events)
    }

    private class FakeMessageRepository : MessageRepository {
        var messages = listOf(ListItem(uid = "200001", info = "消息 1"))
        var error: Exception? = null
        val loaded = mutableListOf<String>()
        val cleared = mutableListOf<String>()
        override suspend fun conversation(uid: String): List<ListItem> {
            loaded += uid
            error?.let { throw it }
            return messages
        }
        override suspend fun send(to: Recipient, content: String) = SendResult.Sent
        override suspend fun clear(uid: String) {
            cleared += uid
            error?.let { throw it }
        }
        override fun waitSecondsToSend() = 0
    }
}
