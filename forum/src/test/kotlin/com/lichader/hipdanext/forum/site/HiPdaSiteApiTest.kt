package com.lichader.hipdanext.forum.site

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HiPdaSiteApiTest {

    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient()
    private val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
    private val api = HiPdaSiteApi(ForumTransport(client, config), HiPdaEndpoints(config))

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun theImageHostComesFromTheSitesConfig() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("site/config-real.json")))

        assertEquals("https://img02.4d4y.com", api.imageHost())
        assertEquals("/config.php", server.takeRequest().path)
    }

    @Test
    fun aConfigWithoutAnImageHostNamesNone() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"other\": \"x\"}"))

        assertNull(api.imageHost())
    }

    @Test
    fun aConfigThatIsNotJsonIsAParseError() {
        server.enqueue(MockResponse().setBody("<html>error</html>"))

        assertThrows(ForumException.Parse::class.java) { runBlocking { api.imageHost() } }
    }
}
