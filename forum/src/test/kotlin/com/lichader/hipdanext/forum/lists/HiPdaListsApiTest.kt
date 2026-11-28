package com.lichader.hipdanext.forum.lists

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
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class HiPdaListsApiTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private lateinit var api: HiPdaListsApi

    @Before
    fun setUp() {
        server.start()
        val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
        api = HiPdaListsApi(HiPdaEndpoints(config), SessionGuard(ForumTransport(client, config), SessionRenewer { error("the session is valid") }))
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun requestedPath(name: String, call: suspend () -> ListPage): Pair<String?, ListPage> {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
            .setBody(Buffer().writeString(fixture("lists/$name"), Charset.forName("GBK"))))
        val page = runBlocking { call() }
        return server.takeRequest().path to page
    }

    @Test
    fun eachListHasItsUrl() {
        assertEquals("/forum/my.php?item=posts&page=2", requestedPath("my-replies-real.html") { api.myReplies(2) }.first)
        assertEquals("/forum/my.php?item=threads&page=1", requestedPath("my-threads-real.html") { api.myThreads(1) }.first)
        assertEquals("/forum/pm.php?filter=privatepm", requestedPath("messages-real.html") { api.messages() }.first)
        assertEquals("/forum/notice.php", requestedPath("notices-empty-real.html") { api.notices() }.first)
        assertEquals("/forum/my.php?item=favorites&type=thread", requestedPath("favorites-real.html") { api.savedThreads(SavedThreads.FAVORITES, 1) }.first)
        assertEquals("/forum/my.php?item=attention&type=thread&page=2", requestedPath("attention-real.html") { api.savedThreads(SavedThreads.ATTENTION, 2) }.first)
    }

    @Test
    fun newPostsStartASearchAndPageThroughIt() {
        val (first, page) = requestedPath("new-posts-p1-real.html") { api.newPosts(null, 1) }
        val (next, _) = requestedPath("new-posts-p2-real.html") { api.newPosts(page.searchId, 2) }

        assertEquals("/forum/search.php?srchfrom=86400&searchsubmit=yes", first)
        assertEquals("/forum/search.php?searchid=257&orderby=lastpost&ascdesc=desc&searchsubmit=yes&page=2", next)
    }

    @Test
    fun aPageCarriesItsHeaderAndFormToken() {
        val (_, page) = requestedPath("favorites-real.html") { api.savedThreads(SavedThreads.FAVORITES, 1) }

        assertEquals(71, page.items.size)
        assertEquals("a1b2c3d4", page.formhash)
        assertEquals("test_user", page.header.accountName)
    }
}
