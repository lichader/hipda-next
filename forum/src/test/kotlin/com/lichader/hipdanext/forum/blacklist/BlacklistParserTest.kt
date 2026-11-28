package com.lichader.hipdanext.forum.blacklist

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BlacklistParserTest {

    private fun parse(name: String) = BlacklistParser.parse(Jsoup.parse(fixture("blacklist/$name")))

    /** The forum's real page (captured 2026-11-28, names, uid and formhash replaced). */
    @Test
    fun realPageListsTheBlacklistedUser() {
        assertEquals(listOf("blocked_user_1"), parse("viewblack-real.html"))
    }

    /** The forum's real page when the list is empty: it says 暂无数据. */
    @Test
    fun realEmptyPageIsAnEmptyList() {
        assertEquals(emptyList<String>(), parse("viewblack-empty-real.html"))
    }

    @Test
    fun listsUsernamesInPageOrderWithoutDuplicatesOrEmptyNames() {
        assertEquals(listOf("alice", "测试用户", "bob_99"), parse("list.html"))
    }

    @Test
    fun emptyListIsNotAnError() {
        assertEquals(emptyList<String>(), parse("empty.html"))
    }

    @Test
    fun forumErrorBecomesServerException() {
        val e = assertThrows(ForumException.Server::class.java) { parse("not-logged-in.html") }
        assertEquals("对不起，您还未登录，无法进行此操作。", e.message)
    }

    @Test
    fun pageWithoutBlacklistIsParseError() {
        assertThrows(ForumException.Parse::class.java) { parse("unexpected.html") }
    }

    @Test
    fun listWithoutUsersOrEmptyMarkerIsParseError() {
        assertThrows(ForumException.Parse::class.java) { parse("unmarked-empty.html") }
    }
}
