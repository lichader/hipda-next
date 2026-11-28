package com.lichader.hipdanext.forum.post

import com.lichader.hipdanext.forum.ForumException
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
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.Charset

class HiPdaPostApiSubmitTest {

    private val gbk = Charset.forName("GBK")
    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient()
    private val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
    private val transport = ForumTransport(client, config)
    private val api = HiPdaPostApi(HiPdaEndpoints(config), transport, SessionGuard(transport, SessionRenewer { error("not used") }))

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=GBK").setBody(Buffer().writeString(body, gbk))

    /** The forum's usual answer to a post: on to the thread. */
    private fun postedAnswers() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/forum/viewthread.php?tid=3000001&extra=page%3D1"))
        server.enqueue(html(fixture("thread/thread-synthetic.html")))
    }

    private fun send(target: PostTarget, form: PostForm): Pair<RecordedRequest, PostResult> {
        val result = runBlocking { api.submit(target, form) }
        return server.takeRequest() to result
    }

    private fun RecordedRequest.fields(): List<Pair<String, String>> = body.readUtf8().split("&").map {
        val (name, value) = it.split("=", limit = 2)
        URLDecoder.decode(name, "GBK") to URLDecoder.decode(value, "GBK")
    }

    @Test
    fun eachTargetHasItsUrl() {
        val form = PostForm("a1b2c3d4", "m")
        for ((target, path) in listOf(
            PostTarget.Reply("3000001") to "/forum/post.php?action=reply&tid=3000001&replysubmit=yes",
            PostTarget.NewThread(2, "9") to "/forum/post.php?action=newthread&fid=2&typeid=9&topicsubmit=yes",
            PostTarget.NewThread(2, null) to "/forum/post.php?action=newthread&fid=2&typeid=null&topicsubmit=yes",
            PostTarget.Edit(2, "3000001", "4000001", 3) to "/forum/post.php?action=edit&extra=&editsubmit=yes&mod=&editsubmit=yes&fid=2&tid=3000001&pid=4000001&page=3",
            PostTarget.Vote(2, "3000001") to "/forum/misc.php?action=votepoll&fid=2&tid=3000001&pollsubmit=yes&quickforward=yes&inajax=1",
        )) {
            postedAnswers()
            assertEquals(path, send(target, form).first.path)
            server.takeRequest()
        }
    }

    @Test
    fun aReplyToAPostInGbk() {
        postedAnswers()
        val form = PostForm(
            "a1b2c3d4", "回复 &#128512;", newAttachments = listOf("9000001", "9000002"), deletedAttachments = listOf("9000003"),
            noticeAuthor = "r|200001|[i]user_1[/i]", noticeAuthorMsg = null, noticeTrimStr = "[b]回复 [/b]",
        )

        val (request, result) = send(PostTarget.Reply("3000001"), form)

        assertEquals(PostResult.Posted("3000001", fixture("thread/thread-synthetic.html")), result)
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"))
        val fields = request.fields()
        assertTrue(fields.first { it.first == "posttime" }.second.toLong() > 0)
        assertEquals(
            listOf(
                "formhash" to "a1b2c3d4", "wysiwyg" to "0", "usesig" to "1", "message" to "回复 &#128512;",
                "attachnew[][description]" to "9000001", "attachnew[][description]" to "9000002", "attachdel[]" to "9000003",
                "noticeauthor" to "r|200001|[i]user_1[/i]", "noticeauthormsg" to "", "noticetrimstr" to "[b]回复 [/b]",
            ),
            fields.filter { it.first != "posttime" },
        )
    }

    @Test
    fun theOtherTargetsFields() {
        fun fields(target: PostTarget, form: PostForm): List<Pair<String, String>> {
            postedAnswers()
            val request = send(target, form).first
            server.takeRequest()
            return request.fields().filter { it.first !in setOf("formhash", "posttime", "wysiwyg", "usesig", "message") }
        }

        assertEquals(listOf("subject" to "主题 1", "attention_add" to "1"), fields(PostTarget.NewThread(2, "9"), PostForm("f", "m", subject = "主题 1")))
        assertEquals(listOf("delete" to "1", "subject" to "主题 1", "typeid" to "9"), fields(PostTarget.Edit(2, "1", "2", 1), PostForm("f", "m", subject = "主题 1", typeId = "9", delete = true)))
        assertEquals(emptyList<Pair<String, String>>(), fields(PostTarget.Edit(2, "1", "2", 1), PostForm("f", "m", subject = "", typeId = "9")))
        assertEquals(listOf("pollanswers[]" to "11", "pollanswers[]" to "3"), fields(PostTarget.Vote(2, "1"), PostForm("f", "m", pollAnswers = listOf("3", "11"))))
        assertEquals(emptyList<Pair<String, String>>(), fields(PostTarget.Reply("1"), PostForm("f", "m", noticeAuthor = "")))
    }

    @Test
    fun aDeletedFirstPostEndsOnTheForum() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/forum/forumdisplay.php?fid=2"))
        server.enqueue(html("<html>forum</html>"))

        assertEquals(PostResult.ThreadDeleted, send(PostTarget.Edit(2, "3000001", "4000001", 1), PostForm("f", "m", delete = true)).second)
    }

    @Test
    fun aRefusal() {
        server.enqueue(html("""<div class="alert_info"><p>您两次发表间隔少于 30 秒，请不要灌水！</p></div>"""))
        assertEquals(PostResult.Refused("您两次发表间隔少于 30 秒，请不要灌水！"), send(PostTarget.Reply("3000001"), PostForm("f", "m")).second)

        server.enqueue(html("<html>logged out</html>"))
        assertEquals(PostResult.Refused(null), send(PostTarget.Reply("3000001"), PostForm("f", "m")).second)

        // The forum went to the thread list without a deletion: not a post.
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/forum/forumdisplay.php?fid=2"))
        server.enqueue(html("<html>forum</html>"))
        assertEquals(PostResult.Refused(null), send(PostTarget.Reply("3000001"), PostForm("f", "m")).second)
    }

    @Test
    fun aFailedRequestIsNotRetried() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(ForumException.Http::class.java) { runBlocking { api.submit(PostTarget.Reply("3000001"), PostForm("f", "m")) } }
        assertEquals(1, server.requestCount)
    }
}
