package com.lichader.hipdanext.forum.favorites

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.lists.SavedThreads
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
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.Charset

class HiPdaFavoritesApiTest {

    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient()
    private val gbk = Charset.forName("GBK")
    private val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
    private val transport = ForumTransport(client, config)
    private val formhash = FormhashCache(config)
    private val api = HiPdaFavoritesApi(transport, HiPdaEndpoints(config), formhash, SessionGuard(transport, SessionRenewer { error("the session is valid") }))

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun enqueue(body: String, type: String) =
        server.enqueue(MockResponse().setHeader("Content-Type", "$type; charset=GBK").setBody(Buffer().writeString(body, gbk)))

    @Test
    fun addAndRemoveReturnTheForumsMessage() = runBlocking {
        enqueue(fixture("favorites/add-real.xml"), "application/xml")
        enqueue(fixture("favorites/remove-real.xml"), "application/xml")

        assertEquals("此主题已成功添加到收藏夹中", api.add(SavedThreads.FAVORITES, "3478000"))
        assertEquals("此主题已成功从您的收藏夹中移除。", api.remove(SavedThreads.FAVORITES, "3478000"))
        assertEquals("/forum/my.php?item=favorites&action=add&inajax=1&ajaxtarget=favorite_msg&tid=3478000", server.takeRequest().path)
        assertEquals("/forum/my.php?item=favorites&action=remove&inajax=1&ajaxtarget=favorite_msg&tid=3478000", server.takeRequest().path)
    }

    @Test
    fun deleteFetchesTheTokenFromTheListPageWhenItHasNone() = runBlocking {
        enqueue(fixture("lists/attention-real.html"), "text/html")
        enqueue("<html></html>", "text/html")

        api.delete(SavedThreads.ATTENTION, "3478001")

        assertEquals("/forum/my.php?item=attention&type=thread", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("attentionsubmit=true&delete[]=3478001&formhash=a1b2c3d4", URLDecoder.decode(post.body.readString(gbk), "GBK"))
    }

    @Test
    fun withoutATokenNothingIsDeleted() {
        enqueue("<html><body>no form</body></html>", "text/html")

        assertThrows(ForumException.Parse::class.java) { runBlocking { api.delete(SavedThreads.FAVORITES, "3478000") } }
        assertEquals(1, server.requestCount)
    }
}
