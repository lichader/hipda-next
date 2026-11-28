package com.lichader.hipdanext.forum.threads

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.parser.pageHeader
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThreadListParserTest {

    private val realPage = Jsoup.parse(fixture("threads/forumdisplay-real.html"))

    @Test
    fun realPageHasAllItsRows() {
        val threads = ThreadListParser.parse(realPage)

        assertEquals(75, threads.size)
        assertEquals(38, threads.count { it.withPictures })
        assertEquals(20, threads.count { it.maxPage > 1 })
    }

    @Test
    fun realRowFields() {
        val first = ThreadListParser.parse(realPage).first()

        assertEquals(
            ThreadSummary(
                tid = "3057651", title = "主题 1", titleColor = "#EE1B2E", type = null, isNew = false,
                sticky = false, poll = false, author = "user_1", authorId = "200001", timeCreate = "2022-6-13",
                timeUpdate = "2022-6-13 22:57", replyCount = "0", viewCount = "110740", lastPost = "user_1",
                withPictures = false, maxPage = 1,
            ),
            first,
        )
    }

    @Test
    fun realRowsWithPagesPicturesAndNewReplies() {
        val threads = ThreadListParser.parse(realPage).associateBy { it.tid }

        assertEquals(10, threads.getValue("2602293").maxPage)
        assertEquals("459", threads.getValue("2602293").replyCount)
        assertEquals(true, threads.getValue("3478334").withPictures)
        assertEquals(true, threads.getValue("3478334").isNew)
    }

    @Test
    fun realBuyAndSellPageHasPinnedThreadsAndTypes() {
        val threads = ThreadListParser.parse(Jsoup.parse(fixture("threads/forumdisplay-bs-real.html")))

        assertEquals(75, threads.size)
        assertEquals(
            listOf("3075777", "3463566", "2871974", "1734535", "1968274", "1197170", "969714", "1760586"),
            threads.filter { it.sticky }.map { it.tid },
        )
        assertEquals(listOf("站务", "其他好玩的"), threads.take(2).map { it.type })
        assertEquals(75, threads.count { it.type != null })
    }

    @Test
    fun realHeaderNamesTheAccountAndItsNotices() {
        assertEquals(PageHeader("100001", "test_user", 0), pageHeader(realPage))
    }

    private fun row(folder: String = "", icon: String = "", subject: String = "<span><a href=\"viewthread.php?tid=9&amp;extra=\">T</a></span>",
                    author: String = "<cite><a href=\"space.php?uid=7\">u</a></cite><em>2026-1-1</em>",
                    nums: String = "<strong>1</strong>/<em>2</em>", last: String = "<cite>v</cite>") =
        """<table><tbody id="normalthread_9"><tr><td class="folder">$folder</td><td class="icon">$icon</td>
           <th class="subject">$subject</th><td class="author">$author</td><td class="nums">$nums</td>
           <td class="lastpost">$last</td></tr></tbody></table>"""

    @Test
    fun stickyAndPollIconsAreRecognised() {
        val thread = ThreadListParser.parse(Jsoup.parse(row(
            folder = "<img src=\"images/default/pin_1.gif\" />",
            icon = "<img src=\"images/icons/poll.gif\" />",
        ))).single()

        assertEquals(true, thread.sticky)
        assertEquals(true, thread.poll)
    }

    @Test
    fun rowsMissingARequiredPartAreSkipped() {
        listOf(
            row(subject = "<em>no link</em>"),
            row(author = "<em>2026-1-1</em>"),
            row(author = "<cite><a href=\"space.php?uid=7\">u</a></cite>"),
            row(nums = "<em>2</em>"),
            row(last = ""),
        ).forEach { assertEquals(it, emptyList<ThreadSummary>(), ThreadListParser.parse(Jsoup.parse(it))) }
    }

    @Test
    fun aPageWithoutRowsIsAParseError() {
        assertThrows(ForumException.Parse::class.java) { ThreadListParser.parse(Jsoup.parse(fixture("user/profile-online-real.html"))) }
    }

    @Test
    fun noticesAddUpThreadsSystemAndFriendMessages() {
        val doc = Jsoup.parse("""<div class="promptcontent"><a id="prompt_threads">帖子消息 (2)</a>
            <a id="prompt_systempm">系统消息 (1)</a><a id="prompt_friend">好友消息 (0)</a><a id="prompt_pm">私人消息 (5)</a></div>""")

        assertEquals(PageHeader(null, null, 3), pageHeader(doc))
    }
}
