package com.lichader.hipdanext.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.bean.ContentText
import com.lichader.hipdanext.cache.SignatureContainer
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.thread.Part
import com.lichader.hipdanext.forum.thread.Poll
import com.lichader.hipdanext.forum.thread.PollOption
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.RunStyle
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.utils.HiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadDetailBeansTest {

    private val settings = SingletonEntryPoint.get().settings()

    @Before
    fun setUp() {
        settings.setBlacklists(emptyList())
        settings.setErrorReportMode(false)
    }

    private fun post(author: String, vararg parts: Part, poll: Poll? = null) =
        Post("1001", 1, "发表于 2026-11-28 10:00", "200001", author, false, "已编辑", "签名", poll, parts.toList())

    private fun page(vararg posts: Post) = ThreadPage("3000001", 2, "主题 :smile:", 1, 1, posts.toList(), null, PageHeader(null, null, 0))

    private fun texts(bean: com.lichader.hipdanext.bean.DetailBean) =
        bean.contents.let { contents -> (0 until contents.size).map { contents.get(it) }.filterIsInstance<ContentText>().joinToString("|") { it.content } }

    @Test
    fun aPostBecomesTheOldHtml() {
        val list = ThreadDetailBeans.of(page(post("user_1",
            Part.Text("a<b", RunStyle(bold = true, colors = listOf("Red"))), Part.LineBreak,
            Part.Link("链接", "example.invalid/x", true), Part.Smiley("https://www.4d4y.com/forum/images/smilies/default/smile.gif"),
        )), settings)
        val bean = list.all.single()

        assertEquals("主题 😄", list.title)
        assertEquals(listOf("3000001", 2, "user_1", HiUtils.getAvatarUrlByUid("200001"), "已编辑"), listOf(list.tid, list.fid, bean.author, bean.avatarUrl, bean.postStatus))
        assertEquals(
            "<b><font color=#ff0000>a&lt;b</font></b><br> <a href=\"https://example.invalid/x\"><small>链接</small></a> " +
                "<img src=\"https://www.4d4y.com/forum/images/smilies/default/smile.gif\"/>",
            texts(bean),
        )
        assertEquals("签名", SignatureContainer.getSignature("200001"))
    }

    @Test
    fun blacklistedAuthorsShowNoContent() {
        settings.setBlacklists(listOf("user_2"))

        val bean = ThreadDetailBeans.of(page(post("user_2", Part.Text("说的话", RunStyle()))), settings).all.single()

        assertEquals("[[黑名单用户]]", bean.author)
        assertEquals(0, bean.contents.size)
        assertNull(bean.avatarUrl)
    }

    @Test
    fun unknownTagsShowOnlyInErrorReportMode() {
        val page = page(post("user_1", Part.Unparsed("marquee", "<marquee>x</marquee>")))

        assertEquals("", texts(ThreadDetailBeans.of(page, settings).all.single()))
        settings.setErrorReportMode(true)
        assertTrue(texts(ThreadDetailBeans.of(page, settings).all.single()).contains("[[ERROR:UNPARSED TAG:marquee:<marquee>x</marquee>]]"))
    }

    @Test
    fun theAppsSmallImagesAreInlineAndOtherImagesAreNumbered() {
        val small = HiUtils.BaseUrl + "attachments/day_140621/1406211752793e731a4fec8f7b.png"
        val list = ThreadDetailBeans.of(page(post("user_1",
            Part.ExternalImage(small), Part.ExternalImage("https://example.invalid/a.jpg"), Part.AttachedImage("https://forum.invalid/b.jpg", 10, "t.jpg"),
        )), settings)
        val bean = list.all.single()

        assertEquals("<img src=\"$small\"/>", texts(bean))
        assertEquals(listOf("https://example.invalid/a.jpg", "https://forum.invalid/b.jpg"), bean.images.map { it.content })
        assertEquals(listOf(0, 1), bean.images.map { it.indexInPage })
        assertEquals(2, list.imagesCount)
    }

    @Test
    fun thePollBecomesThePollBean() {
        val poll = Poll("说明<br>剩 1 天", "a1b2c3d4", 3, listOf(PollOption("11", "选项一", "6 (50%)"), PollOption(null, "选项二", null)), "页脚")

        val bean = ThreadDetailBeans.of(page(post("user_1", poll = poll)), settings).all.single().poll

        assertEquals(listOf("说明<br>剩 1 天", "a1b2c3d4", 3, "页脚"), listOf(bean.title, bean.formhash, bean.maxAnswer, bean.footer))
        assertEquals(listOf("11", ""), bean.pollOptions.map { it.optionId })
        assertEquals(listOf("6 (50%)", ""), bean.pollOptions.map { it.rates })
    }
}
