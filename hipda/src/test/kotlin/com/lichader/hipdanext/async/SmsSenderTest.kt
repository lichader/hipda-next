package com.lichader.hipdanext.async

import androidx.appcompat.app.AlertDialog
import com.lichader.hipdanext.data.messages.MessageRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.messages.Recipient
import com.lichader.hipdanext.forum.messages.SendResult
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class SmsSenderTest {

    private val repository = FakeMessageRepository()
    private val sender = SmsSender(repository, ForumErrorFormatter { true }, CoroutineScope(Dispatchers.Unconfined))
    private val listener = RecordingListener()

    private fun send(uid: String?, username: String?, outcome: () -> SendResult): List<String> {
        repository.outcome = outcome
        sender.send(uid, username, "消息", listener, null)
        return listener.calls
    }

    @Test
    fun eachResultHasTheOldWording() {
        assertEquals(listOf("pre", "${Constants.STATUS_SUCCESS} 短消息发送成功."), send("200001", null) { SendResult.Sent })
        listener.calls.clear()
        assertEquals(listOf("pre", "${Constants.STATUS_FAIL} 您不能给自己发消息，请修改。"), send("", "test_user") { SendResult.Refused("您不能给自己发消息，请修改。") })
        listener.calls.clear()
        assertEquals(listOf("pre", "${Constants.STATUS_FAIL} 短消息发送失败."), send("200001", null) { SendResult.Refused(null) })
        listener.calls.clear()
        assertEquals(listOf("pre", "${Constants.STATUS_FAIL} 短消息发送失败 :  无返回结果"), send("200001", null) { SendResult.NoReply })
    }

    @Test
    fun failuresSayWhy() {
        assertEquals("${Constants.STATUS_FAIL} 无法获取发送凭据", send("200001", null) { throw ForumException.Parse("no token") }.last())
        assertEquals("${Constants.STATUS_FAIL} 短消息发送失败 :  请检查网络连接.",
            send("200001", null) { throw ForumException.Network(UnknownHostException()) }.last())
    }

    @Test
    fun aUidWinsOverAUsername() {
        send("200001", "user_1") { SendResult.Sent }
        send(null, "user_1") { SendResult.Sent }

        assertEquals(listOf(Recipient.Uid("200001"), Recipient.Username("user_1")), repository.sent)
    }

    private class RecordingListener : SmsPostListener {
        val calls = mutableListOf<String>()
        override fun onSmsPrePost() {
            calls += "pre"
        }

        override fun onSmsPostDone(status: Int, message: String, dialog: AlertDialog?) {
            calls += "$status $message"
        }
    }

    private class FakeMessageRepository : MessageRepository {
        var outcome: () -> SendResult = { SendResult.Sent }
        val sent = mutableListOf<Recipient>()
        override suspend fun conversation(uid: String) = emptyList<ListItem>()
        override suspend fun send(to: Recipient, content: String): SendResult {
            sent += to
            return outcome()
        }
        override suspend fun clear(uid: String) {}
        override fun waitSecondsToSend() = 0
    }
}
