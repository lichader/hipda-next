package com.lichader.hipdanext.forum.user

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileParserTest {

    private fun parse(name: String) = UserProfileParser.parse(Jsoup.parse(fixture(name)))

    @Test
    fun onlineUser() {
        val profile = parse("user/profile-online-real.html")

        assertEquals("100002", profile.uid)
        assertEquals("user_1", profile.username)
        assertTrue(profile.online)
        assertEquals(
            "https://img02.4d4y.com/forum/uc_server/data/avatar/000/10/00/02_avatar_middle.jpg",
            profile.avatarUrl,
        )
    }

    @Test
    fun userWhoHidesTheirStatusIsOffline() {
        val profile = parse("user/profile-offline-real.html")

        assertEquals("100003", profile.uid)
        assertEquals("user_2", profile.username)
        assertFalse(profile.online)
    }

    @Test
    fun sectionsAreTheUserGroupWithStatisticsThenTheCredits() {
        val sections = parse("user/profile-offline-real.html").sections

        assertEquals(listOf("用户组: 高级会员", "积分: 0"), sections.map { it.title })
        assertEquals(
            listOf(
                "注册日期: 2000-1-1", "上次访问: 隐身", "最后发表: 2026-1-1 00:00",
                "发帖数级别: 太健谈了", "阅读权限: 10", "帖子: 100 篇(占全部帖子的 %)",
                "平均每日发帖: 0.1 篇", "精华: 0 篇", "页面访问量: 1000",
            ),
            sections[0].lines,
        )
        assertEquals(emptyList<String>(), sections[1].lines)
    }

    @Test
    fun forumErrorBecomesServerException() {
        val e = assertThrows(ForumException.Server::class.java) {
            parse("blacklist/not-logged-in.html")
        }
        assertEquals("对不起，您还未登录，无法进行此操作。", e.message)
    }

    @Test
    fun pageWithoutAProfileIsParseError() {
        assertThrows(ForumException.Parse::class.java) { parse("blacklist/viewblack-real.html") }
    }
}
