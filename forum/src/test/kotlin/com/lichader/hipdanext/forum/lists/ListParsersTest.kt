package com.lichader.hipdanext.forum.lists

import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ListParsersTest {

    private fun page(name: String) = Jsoup.parse(fixture("lists/$name"))

    @Test
    fun myRepliesPairTheThreadRowWithTheReplyRow() {
        val parsed = ListParsers.myReplies(page("my-replies-real.html"))

        assertEquals(75, parsed.items.size)
        assertEquals(4, parsed.maxPage)
        assertEquals(
            ListItem(tid = "2980736", pid = "62837940", title = "主题 1", time = "2021-12-5 18:51", forum = "Discovery", info = "回复 1"),
            parsed.items.first(),
        )
    }

    @Test
    fun myThreads() {
        val parsed = ListParsers.myThreads(page("my-threads-real.html"))

        assertEquals(5, parsed.items.size)
        assertEquals(1, parsed.maxPage)
        assertEquals(ListItem(tid = "1967521", title = "主题 1", time = "user_1 2016-10-20 06:30", forum = "历史遗迹"), parsed.items.first())
    }

    @Test
    fun favoritesAndAttention() {
        val favorites = ListParsers.savedThreads(page("favorites-real.html"))
        val attention = ListParsers.savedThreads(page("attention-real.html"))

        assertEquals(71, favorites.items.size)
        assertEquals(ListItem(tid = "3101657", title = "主题 1", time = "user_1 2026-6-30 09:51", forum = "Discovery"), favorites.items.first())
        assertEquals(5, attention.items.size)
        // The attention list's last poster is plain text, run together with the time, as before.
        assertEquals("user_12022-12-28 21:00", attention.items.first().time)
    }

    @Test
    fun messages() {
        val real = ListParsers.messages(page("messages-real.html")).items
        val synthetic = ListParsers.messages(page("messages-synthetic.html")).items

        assertEquals(5, real.size)
        assertEquals("200001", real.first().uid)
        assertEquals("user_1", real.first().author)
        assertEquals(
            ListItem(uid = "200001", title = "消息 1", author = "user_1", time = "2026-11-28 10:00", isNew = true,
                avatar = Avatar.Url("https://img.invalid/avatar/000/20/00/01_avatar_small.jpg")),
            synthetic.first(),
        )
        // Rows without a linked author or a summary are left out.
        assertEquals(listOf("200001", "200002"), synthetic.map { it.uid })
    }

    @Test
    fun noticesOfEachKind() {
        val notices = ListParsers.notices(page("notices-synthetic.html")).items

        assertEquals(8, notices.size)
        assertEquals(
            ListItem(tid = "3000001", pid = "50001", title = "主题 1", time = "2026-11-28 10:00", info = "user_1 user_2 回复了您的帖子 ", isNew = true),
            notices[0],
        )
        assertEquals("user_3 回复了您关注的主题", notices[1].info)
        assertEquals(
            ListItem(tid = "3000003", pid = "50003", title = "主题 3", author = "user_4", time = "2026-11-27 20:00",
                info = "<u>您的帖子:</u>您的帖子 1<br><u>user_4 说:</u>回复 1", isNew = true, avatar = Avatar.OfUser("200004")),
            notices[2],
        )
        assertEquals("回复 2", notices[3].info)
        assertEquals(ListItem(tid = "3000005", title = "系统信息", info = "您的主题 主题 5 被移动 2026-11-26 08:00", isNew = true, avatar = Avatar.System), notices[5])
        assertEquals(
            ListItem(uid = "200006", title = "好友信息", author = "user_6", info = "user_6 加您为好友", avatar = Avatar.OfUser("200006")),
            notices[6],
        )
        assertEquals(ListItem(title = "好友信息", info = "没有链接"), notices[7])
    }

    @Test
    fun pagesWithoutTheirListHaveNoItems() {
        val empty = page("notices-empty-real.html")

        assertEquals(0, ListParsers.notices(empty).items.size)
        assertEquals(0, ListParsers.myReplies(empty).items.size)
        assertEquals(0, ListParsers.messages(empty).items.size)
    }

    @Test
    fun newPostsCarryTheirSearch() {
        val parsed = ListParsers.search(page("new-posts-p1-real.html"))

        assertEquals(75, parsed.items.size)
        assertEquals(5, parsed.maxPage)
        assertEquals("257", parsed.searchId)
        assertEquals(
            ListItem(tid = "3477974", title = "主题 1", author = "user_1", time = "2026-11-25", forum = "Buy & Sell 交易服务区",
                avatar = Avatar.OfUser("200001")),
            parsed.items.first(),
        )
    }
}
