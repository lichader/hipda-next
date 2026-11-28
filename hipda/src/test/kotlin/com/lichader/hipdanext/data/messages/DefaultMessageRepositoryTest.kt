package com.lichader.hipdanext.data.messages

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.messages.Conversation
import com.lichader.hipdanext.forum.messages.MessagesApi
import com.lichader.hipdanext.forum.messages.Recipient
import com.lichader.hipdanext.forum.messages.SendResult
import com.lichader.hipdanext.forum.parser.PageHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultMessageRepositoryTest {

    private val notiHelper = SingletonEntryPoint.get().notiHelper()
    private val account = FakeAccount()
    private val api = FakeMessagesApi()
    private val repository = DefaultMessageRepository(ApplicationProvider.getApplicationContext(), api, account, notiHelper)

    @Before
    fun setUp() = notiHelper.holdFetchNotify()

    @Test
    fun onlyASentMessageStartsTheWait() = runBlocking {
        api.result = SendResult.Refused("太快了")
        repository.send(Recipient.Uid("200001"), "消息")
        assertEquals(0, repository.waitSecondsToSend())

        api.result = SendResult.Sent
        repository.send(Recipient.Uid("200001"), "消息")
        assertTrue(repository.waitSecondsToSend() in 14..15)
    }

    @Test
    fun aConversationFetchedAcrossAnAccountChangeIsDropped() {
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.conversation("200001") } }
    }

    @Test
    fun aConversationsMessages() = runBlocking {
        assertEquals(listOf(ListItem(uid = "200001", info = "消息 1")), repository.conversation("200001"))
    }

    private class FakeMessagesApi : MessagesApi {
        var result: SendResult = SendResult.Sent
        var duringRequest: () -> Unit = {}
        override suspend fun conversation(uid: String): Conversation {
            duringRequest()
            return Conversation(listOf(ListItem(uid = uid, info = "消息 1")), PageHeader("100001", "test_user", 0))
        }
        override suspend fun send(to: Recipient, content: String) = result
        override suspend fun clear(uid: String) {}
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
