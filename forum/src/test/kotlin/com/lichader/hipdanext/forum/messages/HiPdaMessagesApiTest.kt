package com.lichader.hipdanext.forum.messages

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.session.FormhashCache
import com.lichader.hipdanext.forum.session.SessionGuard
import com.lichader.hipdanext.forum.session.SessionRenewer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class HiPdaMessagesApiTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val gbk = Charset.forName("GBK")
    private lateinit var formhash: FormhashCache
    private lateinit var api: HiPdaMessagesApi

    @Before
    fun setUp() {
        server.start()
        val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"), sessionKey = "auth")
        val transport = ForumTransport(client, config)
        formhash = FormhashCache(config)
        api = HiPdaMessagesApi(transport, HiPdaEndpoints(config), formhash, SessionGuard(transport, SessionRenewer { error("the session is valid") }))
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun enqueue(body: String, type: String = "text/html") =
        server.enqueue(MockResponse().setHeader("Content-Type", "$type; charset=GBK").setBody(Buffer().writeString(body, gbk)))

    @Test
    fun conversationUrl() = runBlocking {
        enqueue(fixture("messages/conversation-real.html"))

        val conversation = api.conversation("200001")

        assertEquals("/forum/pm.php?daterange=5&uid=200001", server.takeRequest().path)
        assertEquals(1, conversation.messages.size)
        assertEquals("test_user", conversation.header.accountName)
    }

    @Test
    fun aMessageToAUidFetchesTheTokenFromTheFormFirst() = runBlocking {
        enqueue(fixture("messages/conversation-real.html"))
        enqueue(fixture("messages/send-sent-synthetic.xml"), "application/xml")

        assertEquals(SendResult.Sent, api.send(Recipient.Uid("200001"), "消息"))

        assertEquals("/forum/pm.php?daterange=1&uid=200001", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("/forum/pm.php?action=send&pmsubmit=yes&infloat=yes&inajax=1&uid=200001", post.path)
        val form = post.body.readString(gbk)
        assertEquals(true, form.startsWith("formhash=a1b2c3d4&lastdaterange="))
        assertEquals(true, form.endsWith("&handlekey=pmreply&message=%CF%FB%CF%A2"))
    }

    @Test
    fun aMessageToAUsernameUsesTheCachedTokenAndNamesTheRecipient() = runBlocking {
        formhash.put("cafe1234")
        enqueue(fixture("messages/send-refused-real.xml"), "application/xml")

        assertEquals(SendResult.Refused("您不能给自己发消息，请修改。"), api.send(Recipient.Username("test_user"), "消息"))

        val post = server.takeRequest()
        assertEquals("/forum/pm.php?action=send&pmsubmit=yes&infloat=yes&inajax=1", post.path)
        val form = post.body.readString(gbk)
        assertEquals(true, form.startsWith("formhash=cafe1234&"))
        assertEquals(true, form.endsWith("&msgto=test_user"))
    }

    @Test
    fun withoutATokenNothingIsSent() {
        enqueue("<html><body>no form</body></html>")

        assertThrows(ForumException.Parse::class.java) { runBlocking { api.send(Recipient.Uid("200001"), "消息") } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun clearDeletesTheConversation() = runBlocking {
        enqueue("<html><body>ok</body></html>")

        api.clear("200001")

        assertEquals("/forum/pm.php?action=del&uid=200001&filter=privatepm", server.takeRequest().path)
    }
}
