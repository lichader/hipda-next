package com.lichader.hipdanext.forum.user

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
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class HiPdaUserApiTest {

    private val server = MockWebServer()
    private val config = TestForumConfig()
    private val client = OkHttpClient()
    private val formhash = FormhashCache(config)
    private lateinit var api: HiPdaUserApi

    @Before
    fun setUp() {
        server.start()
        config.forumServer = server.url("/").toString().removeSuffix("/")
        val session = SessionGuard(ForumTransport(client, config), SessionRenewer { error("the session is valid") })
        api = HiPdaUserApi(HiPdaEndpoints(config), formhash, session)
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun fetchesAndParsesTheGbkProfilePage() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
                .setBody(Buffer().writeString(fixture("user/profile-online-real.html"), Charset.forName("GBK")))
        )

        val profile = api.profile("100002")

        assertEquals("/forum/space.php?uid=100002", server.takeRequest().path)
        assertEquals("user_1", profile.username)
    }

    @Test
    fun keepsTheSessionsFormhashFromTheLogoutLink() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody(fixture("user/profile-offline-real.html")))

        api.profile("100003")

        assertEquals("a1b2c3d4", formhash.get())
    }
}
