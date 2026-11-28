package com.lichader.hipdanext.debug

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset
import java.util.Date

class ResponseRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var dir: File
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        dir = File(temp.root, ResponseRecorder.DIR_NAME)
        client = OkHttpClient.Builder()
            .addInterceptor(ResponseRecorder(dir) { Date(0) })
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun get(path: String): String =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body!!.string() }

    private fun recordedBodies() = dir.listFiles()!!.filter { it.name != ResponseRecorder.INDEX }

    @Test
    fun recordsNothingWhileTheDirectoryIsMissing() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<p>hi</p>"))

        assertEquals("<p>hi</p>", get("/forum/index.php"))

        assertFalse(dir.exists())
    }

    @Test
    fun recordsTheRawBodyAndAnIndexLineWithoutChangingTheResponse() {
        dir.mkdirs()
        val gbk = Buffer().writeString("<p>黑名单</p>", Charset.forName("GBK")).readByteArray()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=gbk").setBody(Buffer().write(gbk)))

        val text = client.newCall(Request.Builder().url(server.url("/forum/pm.php?action=viewblack")).build())
            .execute().use { it.body!!.string() }

        assertEquals("<p>黑名单</p>", text)
        val files = recordedBodies()
        assertEquals(1, files.size)
        assertTrue(files[0].name, files[0].name.endsWith("-0001-GET-forum_pm.php_action=viewblack.html"))
        assertArrayEquals(gbk, files[0].readBytes())
        val index = File(dir, ResponseRecorder.INDEX).readText().trim().split("\t")
        assertEquals(listOf(files[0].name, "GET", "200", "text/html; charset=gbk"), index.take(4))
        assertTrue(index[4].endsWith("/forum/pm.php?action=viewblack"))
    }

    @Test
    fun recordsResponsesToPostsButNeverTheRequestBody() {
        dir.mkdirs()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/xml").setBody("<root>ok</root>"))

        client.newCall(
            Request.Builder().url(server.url("/forum/logging.php?action=login&inajax=1"))
                .post(FormBody.Builder().add("password", "secret-password").build()).build()
        ).execute().close()

        val recorded = dir.walk().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(recorded.contains("<root>ok</root>"))
        assertFalse(recorded.contains("secret-password"))
        assertTrue(recordedBodies().single().name.endsWith("-POST-forum_logging.php_action=login_inajax=1.xml"))
    }

    @Test
    fun skipsImagesAndOtherBinaryResponses() {
        dir.mkdirs()
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("PNG"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody("BIN"))

        get("/forum/images/a.png")
        get("/forum/attachment.php?aid=1")

        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun extensionsFollowTheContentType() {
        fun ext(type: String) = ResponseRecorder.extensionFor(okhttp3.MediaType.Companion.run { type.toMediaType() })
        assertEquals("html", ext("text/html; charset=gbk"))
        assertEquals("xml", ext("application/xml"))
        assertEquals("json", ext("application/json"))
        assertEquals("txt", ext("text/plain"))
        assertEquals(null, ext("image/jpeg"))
    }
}
