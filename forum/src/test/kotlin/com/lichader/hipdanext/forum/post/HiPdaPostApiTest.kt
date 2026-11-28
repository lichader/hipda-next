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
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class HiPdaPostApiTest {

    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient()
    private val config = TestForumConfig(forumServer = server.url("/").toString().removeSuffix("/"))
    private val transport = ForumTransport(client, config)
    private val api = HiPdaPostApi(HiPdaEndpoints(config), transport, SessionGuard(transport, SessionRenewer { error("the session is valid") }))

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun page(name: String) = MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
        .setBody(Buffer().writeString(fixture("post/$name"), Charset.forName("GBK")))

    private fun editor(kind: EditorKind): Pair<String?, EditorPage> {
        server.enqueue(page("editor-reply-to-post.html"))
        val editor = runBlocking { api.editor(kind) }
        return server.takeRequest().path to editor
    }

    @Test
    fun eachEditorHasItsUrl() {
        assertEquals("/forum/post.php?action=reply&tid=3000001", editor(EditorKind.Reply("3000001")).first)
        assertEquals("/forum/post.php?action=reply&tid=3000001&reppost=4000001", editor(EditorKind.ReplyToPost("3000001", "4000001")).first)
        assertEquals("/forum/post.php?action=reply&tid=3000001&repquote=4000001", editor(EditorKind.Quote("3000001", "4000001")).first)
        assertEquals("/forum/post.php?action=newthread&fid=2", editor(EditorKind.NewThread(2)).first)
        assertEquals("/forum/post.php?action=edit&fid=2&tid=3000001&pid=4000001&page=1", editor(EditorKind.Edit(2, "3000001", "4000001")).first)
    }

    @Test
    fun onlyRepliesToAPostReadTheNotices() {
        assertEquals("r|200001|[i]user_1[/i]", editor(EditorKind.ReplyToPost("3000001", "4000001")).second.noticeAuthor)
        assertEquals("r|200001|[i]user_1[/i]", editor(EditorKind.Quote("3000001", "4000001")).second.noticeAuthor)
        assertEquals(null, editor(EditorKind.Reply("3000001")).second.noticeAuthor)
    }

    @Test
    fun anAccountWithoutRealNameVerification() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/forum/memcp.php?action=bind"))
        server.enqueue(page("editor-reply.html"))

        assertThrows(ForumException.RealNameRequired::class.java) { runBlocking { api.editor(EditorKind.Reply("3000001")) } }
    }

    @Test
    fun anImageUpload() {
        server.enqueue(MockResponse().setBody(fixture("post/upload.txt")))

        val upload = runBlocking { api.uploadImage("100001", "0123456789abcdef", "Hi-261128_202206.png", "image/png", byteArrayOf(1, 2, 3)) }

        assertEquals(ImageUpload.Uploaded("9000001"), upload)
        val request = server.takeRequest()
        assertEquals("/forum/misc.php?action=swfupload&operation=upload&simple=1&type=image", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        for (part in listOf("name=\"uid\"", "100001", "name=\"hash\"", "0123456789abcdef", "name=\"Filedata\"; filename=\"Hi-261128_202206.png\"", "Content-Type: image/png")) {
            assertTrue(part, part in body)
        }
    }

    @Test
    fun uploadAnswersWithoutAnImage() {
        assertEquals(ImageUpload.NoImageId("DISCUZUPLOAD|0|0|1"), api.imageUpload("DISCUZUPLOAD|0|0|1"))
        assertEquals(ImageUpload.NoImageId("DISCUZUPLOAD|0|"), api.imageUpload("DISCUZUPLOAD|0|"))
        assertEquals(ImageUpload.Unrecognized("<html>error</html>"), api.imageUpload("<html>error</html>"))
    }

    @Test
    fun aRefusedUpload() {
        server.enqueue(MockResponse().setResponseCode(413))

        val error = assertThrows(ForumException.Http::class.java) {
            runBlocking { api.uploadImage("100001", "h", "a.png", "image/png", byteArrayOf(1)) }
        }
        assertEquals(413, error.code)
    }
}
