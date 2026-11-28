package com.lichader.hipdanext.forum.threads

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

class HiPdaThreadListApiTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private lateinit var api: HiPdaThreadListApi

    @Before
    fun setUp() {
        server.start()
        val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
        val session = SessionGuard(ForumTransport(client, config), SessionRenewer { error("the session is valid") })
        api = HiPdaThreadListApi(HiPdaEndpoints(config), session)
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun enqueuePage() = server.enqueue(
        MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
            .setBody(Buffer().writeString(fixture("threads/forumdisplay-real.html"), Charset.forName("GBK")))
    )

    @Test
    fun fetchesAndParsesAGbkPage() = runBlocking {
        enqueuePage()

        val page = api.page(2, 1, typeId = null, orderByPostTime = false)

        assertEquals("/forum/forumdisplay.php?fid=2&page=1", server.takeRequest().path)
        assertEquals(75, page.threads.size)
        assertEquals("test_user", page.header.accountName)
    }

    @Test
    fun typeFilterAndPostTimeOrderAreInTheUrl() = runBlocking {
        enqueuePage()

        api.page(6, 3, typeId = "1", orderByPostTime = true)

        assertEquals("/forum/forumdisplay.php?fid=6&page=3&filter=type&typeid=1&orderby=dateline", server.takeRequest().path)
    }
}
