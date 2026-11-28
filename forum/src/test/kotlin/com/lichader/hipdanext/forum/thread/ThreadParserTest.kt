package com.lichader.hipdanext.forum.thread

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.parser.PageHeader
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadParserTest {

    private val base = "https://forum.invalid/forum/"

    private fun parse(name: String, tid: String? = "3000001") = ThreadParser.parse(Jsoup.parse(fixture("thread/$name")), tid, base)

    @Test
    fun realPage() {
        val page = parse("thread-real.html", "3478387")

        assertEquals(listOf(1, 1, 6, "主题 1", "3478387", null), listOf(page.page, page.lastPage, page.fid, page.title, page.tid, page.postsPerPage))
        assertEquals(12, page.posts.size)
        assertEquals(PageHeader("100001", "test_user", 0), page.header)
        assertEquals(listOf("200001", "user_1", 1), page.posts.first().let { listOf(it.uid, it.author, it.floor) })
        assertEquals((1..12).toList(), page.posts.map { it.floor })
        assertEquals(3, page.posts.sumOf { post -> post.parts.count { it is Part.AttachedImage } })
    }

    @Test
    fun pageFieldsAndPostsPerPage() {
        val page = parse("thread-synthetic.html")

        assertEquals(listOf(2, 3, 2, "主题 1 :smile:"), listOf(page.page, page.lastPage, page.fid, page.title))
        // The first post on page 2 is floor 11: (11 - 1) / (2 - 1).
        assertEquals(10, page.postsPerPage)
        assertEquals(PageHeader("100001", "test_user", 1), page.header)
        assertEquals(listOf("1001", "1002", "1003", "1004", "1005", "1006"), page.posts.map { it.pid })
    }

    @Test
    fun styledTextRuns() {
        val parts = parse("thread-synthetic.html").posts[0].parts

        assertEquals(Part.Text("第一行 ", RunStyle()), parts[0])
        assertEquals(Part.Text("红色", RunStyle(colors = listOf("Red"))), parts[1])
        assertEquals(Part.Text("深蓝 ", RunStyle(colors = listOf("Red", "#123456"))), parts[2])
        assertEquals(Part.Text("小字", RunStyle(smallFont = true, colors = listOf("Red", "#123456"))), parts[3])
        assertEquals(Part.LineBreak, parts[4])
        assertTrue(Part.Text("粗体", RunStyle(bold = true)) in parts)
        assertTrue(Part.Text("删除线", RunStyle(strike = true)) in parts)
        // <em> doesn't change the style, as before.
        assertTrue(Part.Text("强调", RunStyle()) in parts)
    }

    @Test
    fun linksEmailsAndImages() {
        val parts = parse("thread-synthetic.html").posts[0].parts

        assertTrue(Part.Link("https://example.com/a?b=1&c=2", "https://example.com/a?b=1&c=2", false) in parts)
        assertTrue(Part.Email("user_1@example.com") in parts)
        assertTrue(Part.Text(" 以及 <尖括号>", RunStyle()) in parts)
        assertTrue(Part.Link("https://example.invalid/img-link", "https://example.invalid/img-link", false) in parts)
        // A small link in a signature points at the current domain.
        assertTrue(Part.Link("旧域名", "https://www.4d4y.com/forum/viewthread.php?tid=1", true) in parts)
        assertTrue(Part.Smiley("https://www.4d4y.com/forum/images/smilies/default/smile.gif") in parts)
        assertTrue(Part.ExternalImage("https://example.invalid/external.jpg") in parts)
        // A relative address is made absolute first, as before, so it counts as another site's.
        assertTrue(Part.ExternalImage(base + "relative/unknown.gif") in parts)
        assertTrue(Part.AttachedImage(base + "attachment.php?aid=2&noupdate=yes", Math.round(120.5 * 1024), "") in parts)
        assertTrue(Part.Attachment("attachment.php?aid=3", "文件.zip", "(1.2 MB, 下载次数: 3)") in parts)
        assertTrue(Part.Attachment("attachment.php?aid=1", "附件链接", null) in parts)
        // The forum's icons and base64 images are left out.
        assertTrue(parts.none { it is Part.ExternalImage && "back.gif" in it.src })
    }

    @Test
    fun tablesUnknownTagsAndVideos() {
        val parts = parse("thread-synthetic.html").posts[0].parts

        val table = parts.indexOf(Part.Text("单元格1", RunStyle()))
        assertEquals(listOf(Part.LineBreak, Part.Space), parts.subList(table - 2, table))
        assertTrue(Part.Unparsed("marquee", "<marquee>\n 未知标签\n</marquee>") in parts || parts.any { it is Part.Unparsed && it.tag == "marquee" })
        assertTrue(Part.Video("http://v.youku.com/v_show/id_XNzIy.html", youku = true) in parts)
        assertTrue(Part.Video("https://example.invalid/video.swf", youku = false) in parts)
        assertTrue(parts.none { it is Part.Text && it.text.contains("图片标题") })
    }

    @Test
    fun attachmentsListedUnderTheMessage() {
        val post = parse("thread-synthetic.html").posts[0]

        assertEquals(
            listOf(
                Part.AttachedImage(base + "attachments/day_261128/b.jpg", Math.round(100.1 * 1024), base + "attachments/day_261128/thumb.b.jpg"),
                Part.AttachedImage("", 0, ""),
            ),
            post.parts.takeLast(2),
        )
        assertEquals(listOf(true, "签名 1"), listOf(post.warned, post.signature))
    }

    @Test
    fun quotesFloorLinksAndStatus() {
        val post = parse("thread-synthetic.html").posts[1]
        val quote = post.parts.first() as Part.Quote

        assertEquals(listOf("user_1 发表于 2026-11-28 10:01", "3000001", "1001"), listOf(quote.authorAndTime, quote.tid, quote.pid))
        // Jsoup's line breaks stay in the cleaned HTML, as before.
        assertTrue(quote.html.contains("被引用的") && quote.html.contains("<b>文字</b>") && quote.html.contains("<font color=\"Blue\">蓝</font>"))
        assertTrue(!quote.html.contains("隐藏"))
        assertTrue(Part.FloorLink("回复 1# user_1", "3000001", "1001", 1, "user_1") in post.parts)
        assertEquals("本帖最后由 user_2 于 2026-11-28 10:05 编辑", post.status)
        assertEquals(listOf(Part.LineBreak, Part.Attachment("attachment.php?aid=5", "资料.pdf", "(200 KB, 下载次数: 1)")), post.parts.takeLast(2))
    }

    @Test
    fun lockedAndEmptyPosts() {
        val posts = parse("thread-synthetic.html").posts

        assertEquals(listOf(Part.Locked("提示: 作者被禁止或删除 内容自动屏蔽")), posts[2].parts)
        assertEquals(listOf(Part.Empty), posts[3].parts)
        assertNull(posts[2].status)
    }

    @Test
    fun pollOnTheFirstPost() {
        val page = parse("thread-poll-synthetic.html", "3000002")
        val poll = page.posts.first().poll!!

        assertEquals(listOf("投票主题", 1, 1, null), listOf(page.title, page.page, page.lastPage, page.postsPerPage))
        assertTrue(poll.titleHtml.contains("多选投票") && poll.titleHtml.contains("<br>距结束还有: 2 天"))
        assertEquals(listOf("a1b2c3d4", 3, "投票后结果可见"), listOf(poll.formhash, poll.maxAnswer, poll.footer))
        assertEquals(listOf(PollOption("11", "选项一", "6 (50%)"), PollOption("12", "选项二", "6 (50%)")), poll.options.map { it.copy(rates = it.rates?.trim()) })
        // The poll form is read first and removed, so the first post's summary is empty, as before.
        assertEquals(Part.PollSummary("", emptyList()), page.posts.first().parts.first())
    }

    @Test
    fun aPageWithoutAPostListIsAParseError() {
        assertThrows(ForumException.Parse::class.java) { ThreadParser.parse(Jsoup.parse("<html><body>error</body></html>"), "1", base) }
    }

    @Test
    fun theThreadAuthorIsTheFirstPostsAuthor() {
        assertEquals("200001", ThreadParser.authorId(Jsoup.parse(fixture("thread/thread-synthetic.html"))))
    }
}
