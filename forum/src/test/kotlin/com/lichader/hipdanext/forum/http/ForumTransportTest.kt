package com.lichader.hipdanext.forum.http

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.TestForumConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class ForumTransportTest {

    private val gbk = Charset.forName("GBK")
    private val server = MockWebServer()
    private val config = TestForumConfig(charset = gbk)
    private var client = OkHttpClient()
    private val transport get() = ForumTransport(client, config)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun url() = server.url("/forum/page.php").toString()

    private fun body(text: String, charset: Charset, contentType: String) =
        MockResponse().setHeader("Content-Type", contentType).setBody(Buffer().writeString(text, charset))

    private inline fun <reified T : ForumException> assertFails(noinline block: suspend () -> Unit): T {
        try {
            runBlocking { block() }
        } catch (e: ForumException) {
            assertTrue("Expected ${T::class.simpleName} but got $e", e is T)
            return e as T
        }
        fail("Expected ${T::class.simpleName}")
        throw AssertionError()
    }

    @Test
    fun decodesWithTheConfiguredCharsetWhenTheResponseNamesNone() = runBlocking {
        server.enqueue(body("黑名单", gbk, "text/html"))
        assertEquals("黑名单", transport.get(url()))
    }

    @Test
    fun charsetInContentTypeOverridesTheConfiguredOne() = runBlocking {
        server.enqueue(body("黑名单", Charsets.UTF_8, "text/html; charset=utf-8"))
        assertEquals("黑名单", transport.get(url()))
    }

    @Test
    fun sendsUserAgentAndBypassesTheCache() = runBlocking {
        server.enqueue(MockResponse())
        transport.get(url())
        val request = server.takeRequest()
        assertEquals("hipda-next test", request.getHeader("User-Agent"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
    }

    @Test
    fun postEncodesValuesInTheForumCharset() = runBlocking {
        server.enqueue(MockResponse())
        transport.post(url(), linkedMapOf("formhash" to "a1b2c3d4", "user" to "测试"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("formhash=a1b2c3d4&user=%B2%E2%CA%D4", request.body.readUtf8())
    }

    @Test
    fun httpErrorStatusBecomesHttpException() {
        server.enqueue(MockResponse().setResponseCode(503))
        val e = assertFails<ForumException.Http> { transport.get(url()) }
        assertEquals(503, e.code)
    }

    @Test
    fun connectionFailureBecomesNetworkException() {
        val url = url()
        server.shutdown()
        assertFails<ForumException.Network> { transport.get(url) }
    }

    @Test
    fun readTimeoutBecomesTimeoutException() {
        client = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertFails<ForumException.Timeout> { transport.get(url()) }
    }

    @Test
    fun cancellingTheCallerCancelsTheHttpCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async(Dispatchers.Default, CoroutineStart.UNDISPATCHED) { transport.get(url()) }
        assertNotNull("request never reached the server", server.takeRequest(5, TimeUnit.SECONDS))

        request.cancel()

        val deadline = System.currentTimeMillis() + 5_000
        while (client.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(0, client.dispatcher.runningCallsCount())
    }
}
