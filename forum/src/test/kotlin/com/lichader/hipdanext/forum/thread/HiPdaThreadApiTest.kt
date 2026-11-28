package com.lichader.hipdanext.forum.thread

import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.session.SessionGuard
import com.lichader.hipdanext.forum.session.SessionRenewer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class HiPdaThreadApiTest {

    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient()
    private val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
    private val api = HiPdaThreadApi(HiPdaEndpoints(config), SessionGuard(ForumTransport(client, config), SessionRenewer { error("the session is valid") }))

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun <T> request(call: suspend () -> T): Pair<String?, T> {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
            .setBody(Buffer().writeString(fixture("thread/thread-synthetic.html"), Charset.forName("GBK"))))
        val result = runBlocking { call() }
        return server.takeRequest().path to result
    }

    @Test
    fun eachKindOfPageHasItsUrl() {
        assertEquals("/forum/viewthread.php?tid=3000001&page=2", request { api.page("3000001", 2, null) }.first)
        assertEquals("/forum/viewthread.php?tid=3000001&page=1&authorid=200001", request { api.page("3000001", 1, "200001") }.first)
        assertEquals("/forum/viewthread.php?tid=3000001&page=1", request { api.page("3000001", 1, "") }.first)
        assertEquals("/forum/redirect.php?goto=lastpost&from=fastpost&tid=3000001", request { api.lastPage("3000001") }.first)
        assertEquals("/forum/redirect.php?goto=findpost&pid=1002&ptid=3000001", request { api.pageOfPost("1002", "3000001") }.first)
    }

    @Test
    fun aPostsPageWithoutAThreadTakesTheThreadFromThePage() {
        val (path, page) = request { api.pageOfPost("1002", null) }

        assertEquals("/forum/gotopost.php?pid=1002", path)
        assertEquals("3000001", page.tid)
        assertEquals(6, page.posts.size)
    }

    @Test
    fun theThreadAuthor() {
        val (path, uid) = request { api.authorId("3000001") }

        assertEquals("/forum/viewthread.php?tid=3000001&page=1", path)
        assertEquals("200001", uid)
    }
}
