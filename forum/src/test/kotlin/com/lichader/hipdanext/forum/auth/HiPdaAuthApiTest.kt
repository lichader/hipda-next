package com.lichader.hipdanext.forum.auth

import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
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

class HiPdaAuthApiTest {

    private val gbk = Charset.forName("GBK")
    private val server = MockWebServer()
    private val config = TestForumConfig()
    private val client = OkHttpClient()
    private lateinit var api: HiPdaAuthApi

    @Before
    fun setUp() {
        server.start()
        config.forumServer = server.url("/").toString().removeSuffix("/")
        api = HiPdaAuthApi(ForumTransport(client, config), HiPdaEndpoints(config))
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun gbkPage(name: String, type: String) = MockResponse().setHeader("Content-Type", "$type; charset=gbk")
        .setBody(Buffer().writeString(fixture(name), gbk))

    @Test
    fun tokenIsReadFromTheLoginForm() = runBlocking {
        server.enqueue(gbkPage("auth/login-form-real.html", "text/html"))

        assertEquals("a1b2c3d4", api.loginToken())
        assertEquals("/forum/logging.php?action=login", server.takeRequest().path)
    }

    @Test
    fun submitPostsTheFormLikeTheForumsLoginPage() = runBlocking {
        server.enqueue(gbkPage("auth/login-success-real.xml", "application/xml"))

        val result = api.submit("a1b2c3d4", Credentials("测试", "secret", "1", "答案"))

        assertEquals(LoginResult.Success, result)
        val request = server.takeRequest()
        assertEquals("/forum/logging.php?action=login&loginsubmit=yes&inajax=1", request.path)
        assertEquals(
            listOf(
                "m_formhash=a1b2c3d4",
                "referer=" + java.net.URLEncoder.encode(config.forumServer + "/forum/index.php", "GBK"),
                "loginfield=username",
                "username=%B2%E2%CA%D4",
                "password=5ebe2294ecd0e0f08eab7690d2a6ee69",
                "questionid=1",
                "answer=%B4%F0%B0%B8",
                "cookietime=2592000",
            ),
            request.body.readUtf8().split("&"),
        )
    }

    @Test
    fun refusalIsReturnedNotThrown() = runBlocking {
        server.enqueue(gbkPage("auth/login-failed.xml", "application/xml"))

        assertEquals(LoginResult.Rejected("登录失败，您还可以尝试 4 次"), api.submit("t", Credentials("u", "p", "0", "")))
    }
}
