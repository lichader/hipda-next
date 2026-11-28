package com.lichader.hipdanext.forum.post

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorParserTest {

    private fun parse(name: String, quoting: Boolean = false) = EditorParser.parse(Jsoup.parse(fixture("post/$name")), quoting)

    @Test
    fun aReplyAfterAnImageWasUploaded() {
        val page = parse("editor-reply.html")

        assertEquals(listOf("a1b2c3d4", "", "100001", "0123456789abcdef0123456789abcdef", ""), listOf(page.formhash, page.text, page.uid, page.hash, page.subject))
        assertFalse(page.deletable)
        assertEquals(8 * 1024 * 1024, page.maxUploadSize)
        assertEquals(listOf(null, null, null), listOf(page.noticeAuthor, page.noticeAuthorMsg, page.noticeTrimStr))
        assertEquals(listOf("9000001"), page.images)
        assertEquals(emptyList<String>(), page.attachments)
        assertEquals(emptyMap<String, String>(), page.types)
        assertNull(page.typeId)
    }

    @Test
    fun aReplyToAPostHasItsQuoteAndNotices() {
        val page = parse("editor-reply-to-post.html", quoting = true)

        assertTrue(page.text!!.startsWith("[b]回复 [url=https://www.4d4y.com/forum/redirect.php?goto=findpost&pid=4000001&ptid=3000001]1#[/url] [i]user_1[/i] [/b]"))
        assertEquals("r|200001|[i]user_1[/i]", page.noticeAuthor)
        assertEquals("内容 1", page.noticeAuthorMsg)
        assertTrue(page.noticeTrimStr!!.startsWith("[b]回复 [url="))
    }

    @Test
    fun aQuote() {
        val page = parse("editor-quote.html", quoting = true)

        assertTrue(page.text!!.startsWith("[quote]内容 1\n[size=2][color=#999999]user_1 发表于 2026-11-28 17:20[/color]"))
        assertEquals("q|200001|user_1", page.noticeAuthor)
    }

    @Test
    fun aNewThreadHasTheForumsTypes() {
        val page = parse("editor-new-thread.html")

        assertEquals(listOf("0" to "分类", "9" to "聚会", "33" to "汽车"), page.types.entries.take(3).map { it.key to it.value })
        assertEquals("0", page.typeId)
    }

    @Test
    fun anEditedPostCanBeDeleted() {
        val page = parse("editor-edit.html")

        assertEquals("内容 1", page.text)
        assertTrue(page.deletable)
    }

    @Test
    fun theOtherBranches() {
        val page = parse("editor-synthetic.html", quoting = true)

        assertEquals(listOf("第一行\n  第二行", "100001", "主题 2"), listOf(page.text, page.uid, page.subject))
        assertEquals(500 * 1024, page.maxUploadSize)
        assertEquals(listOf("r|200001|[i]user_1[/i]", "内容 2", "[b]回复 [/b]"), listOf(page.noticeAuthor, page.noticeAuthorMsg, page.noticeTrimStr))
        assertEquals(listOf("9000011", "9000012", "9000014", "9000013"), page.images)
        assertEquals(listOf("8000001"), page.attachments)
        assertEquals(listOf("0", "9", "33", "38"), page.types.keys.toList())
        assertEquals("33", page.typeId)
    }

    @Test
    fun readingStopsWhereThePageEnds() {
        val noTextBox = EditorParser.parse(Jsoup.parse("""<input name="formhash" value="a1b2c3d4"><input name="hash" value="h"><div id="typeid"><option value="1">一</option></div>"""), false)
        val noToken = EditorParser.parse(Jsoup.parse("""<script>discuz_uid = 100001,</script><input name="formhash" value="a1b2c3d4"><textarea>t</textarea><input id="delete">"""), false)

        assertEquals(listOf(null, null, null), listOf(noTextBox.text, noTextBox.uid, noTextBox.hash))
        assertEquals(emptyMap<String, String>(), noTextBox.types)
        assertEquals(listOf("t", "100001", null), listOf(noToken.text, noToken.uid, noToken.hash))
        assertFalse(noToken.deletable)
    }

    @Test
    fun noMaxUploadSizeUnlessThePageSaysOne() {
        fun size(info: String) = EditorParser.parse(Jsoup.parse("""<script>discuz_uid = 1,</script><input name="formhash" value="f"><textarea></textarea><input name="hash" value="h"><div class="uploadinfo">$info</div>"""), false).maxUploadSize

        assertEquals(listOf(null, null, null, null, 2 * 1024 * 1024), listOf(size("小于 8MB"), size("文件尺寸: 小于 1KB"), size("文件尺寸: 小于 xMB"), size("文件尺寸: 不限"), size("文件尺寸: 小于 2mb")))
    }

    @Test
    fun aRefusalOrAPageWithoutAForm() {
        val refused = assertThrows(ForumException.Server::class.java) {
            EditorParser.parse(Jsoup.parse("""<div class="alert_error"><p>对不起，您没有权限 <a href="#">返回</a></p></div>"""), false)
        }
        assertEquals("对不起，您没有权限", refused.message)
        assertThrows(ForumException.Parse::class.java) { EditorParser.parse(Jsoup.parse("<p>empty</p>"), false) }
    }
}
