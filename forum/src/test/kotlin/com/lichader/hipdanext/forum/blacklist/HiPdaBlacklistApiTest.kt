package com.lichader.hipdanext.forum.blacklist

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.session.FormhashCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class HiPdaBlacklistApiTest {

    private val server = MockWebServer()
    private val config = TestForumConfig()
    private val client = OkHttpClient()
    private lateinit var api: HiPdaBlacklistApi

    @Before
    fun setUp() {
        server.start()
        config.forumServer = server.url("/").toString().removeSuffix("/")
        api = HiPdaBlacklistApi(ForumTransport(client, config), HiPdaEndpoints(config), FormhashCache(config))
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun html(html: String) =
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(html))

    private fun page(name: String, formhash: String = "a1b2c3d4") =
        html(fixture("blacklist/$name").replace("a1b2c3d4", formhash))

    private fun RecordedRequest.isGet(path: String) {
        assertEquals("GET", method)
        assertEquals(path, this.path)
    }

    private fun RecordedRequest.isPost(path: String, body: String) {
        assertEquals("POST", method)
        assertEquals(path, this.path)
        assertEquals(body, this.body.readUtf8())
    }

    @Test
    fun fetchReturnsTheUsernamesOfTheBlacklistPage() = runBlocking {
        page("list.html")
        assertEquals(listOf("alice", "测试用户", "bob_99"), api.fetch())
        server.takeRequest().isGet("/forum/pm.php?action=viewblack")
    }

    /** The real page, sent GBK-encoded with no charset in the header, as the forum may do. */
    @Test
    fun realGbkPageIsFetchedAndItsFormhashUsed() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody(Buffer().writeString(fixture("blacklist/viewblack-real.html"), Charset.forName("GBK")))
        )
        page("submit-ok.html")

        assertEquals(listOf("blocked_user_1"), api.fetch())
        api.remove("blocked_user_1")

        server.takeRequest()
        server.takeRequest().isPost("/forum/pm.php?action=delblack", "formhash=a1b2c3d4&user=blocked_user_1")
    }

    @Test
    fun realEmptyPageGivesAnEmptyListAndAFormhash() = runBlocking {
        page("viewblack-empty-real.html")
        page("submit-ok.html")

        assertEquals(emptyList<String>(), api.fetch())
        api.add("blocked_user_1")

        assertEquals(2, server.requestCount)
        server.takeRequest()
        server.takeRequest().isPost("/forum/pm.php?action=addblack", "formhash=a1b2c3d4&user=blocked_user_1")
    }

    @Test
    fun fetchReportsTheForumsError() {
        page("not-logged-in.html")
        val e = assertThrows(ForumException.Server::class.java) { runBlocking { api.fetch() } }
        assertEquals("对不起，您还未登录，无法进行此操作。", e.message)
    }

    @Test
    fun removeReusesTheFormhashOfTheLastFetch() = runBlocking {
        page("list.html")
        page("submit-ok.html")

        api.fetch()
        api.remove("bob_99")

        assertEquals(2, server.requestCount)
        server.takeRequest()
        server.takeRequest().isPost("/forum/pm.php?action=delblack", "formhash=a1b2c3d4&user=bob_99")
    }

    @Test
    fun addFetchesAFormhashWhenNoneIsKnown() = runBlocking {
        page("list.html")
        page("submit-ok.html")

        api.add("carol")

        server.takeRequest().isGet("/forum/pm.php?action=viewblack")
        server.takeRequest().isPost("/forum/pm.php?action=addblack", "formhash=a1b2c3d4&user=carol")
    }

    @Test
    fun aNewSessionDoesNotReuseTheOldFormhash() = runBlocking {
        page("list.html", formhash = "old00000")
        page("list.html", formhash = "new11111")
        page("submit-ok.html")

        api.fetch()
        config.sessionKey = "session-2"
        api.remove("alice")

        server.takeRequest()
        server.takeRequest().isGet("/forum/pm.php?action=viewblack")
        server.takeRequest().isPost("/forum/pm.php?action=delblack", "formhash=new11111&user=alice")
    }

    @Test
    fun submitReportsTheForumsError() {
        page("list.html")
        page("submit-error.html")
        val e = assertThrows(ForumException.Server::class.java) { runBlocking { api.add("nobody") } }
        assertEquals("用户名不存在，请返回修改。", e.message)
    }

    @Test
    fun submitFailsWhenNoFormhashCanBeFound() {
        html(fixture("blacklist/empty.html").replace("""<input type="hidden" name="formhash" value="a1b2c3d4" />""", ""))
        assertThrows(ForumException.Parse::class.java) { runBlocking { api.remove("alice") } }
        assertEquals(1, server.requestCount)
    }
}
