package com.lichader.hipdanext.forum.session

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.TestForumConfig
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.http.ForumTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SessionGuardTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val loggedIn = AtomicBoolean(true)
    private val renewals = AtomicInteger()
    private var renewalSucceeds = true
    private lateinit var guard: SessionGuard

    @Before
    fun setUp() {
        // The server answers as the forum would for the current session state.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(fixture(if (loggedIn.get()) "user/profile-online-real.html" else "auth/login-form-real.html"))
        }
        server.start()
        val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
        guard = SessionGuard(ForumTransport(client, config), SessionRenewer {
            renewals.incrementAndGet()
            if (renewalSucceeds) loggedIn.set(true)
            renewalSucceeds
        })
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun url() = server.url("/forum/space.php?uid=1").toString()

    @Test
    fun aLoggedInPageIsReturnedAsIs() = runBlocking {
        guard.page(url())

        assertEquals(0, renewals.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aLoggedOutPageRenewsTheSessionAndIsFetchedAgain() = runBlocking {
        loggedIn.set(false)

        val doc = guard.page(url())

        assertEquals("user_1", doc.selectFirst("div#profilecontent h1")!!.text().trim())
        assertEquals(1, renewals.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailedRenewalIsNotLoggedIn() {
        loggedIn.set(false)
        renewalSucceeds = false

        assertThrows(ForumException.NotLoggedIn::class.java) { runBlocking { guard.page(url()) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun requestsThatFindTheSessionExpiredTogetherShareOneLogin() = runBlocking(Dispatchers.Default) {
        loggedIn.set(false)

        List(5) { async { guard.page(url()) } }.awaitAll()

        assertEquals(1, renewals.get())
    }
}
