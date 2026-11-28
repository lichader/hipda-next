package com.lichader.hipdanext.forum.parser

import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagesTest {

    @Test
    fun formhashIsReadFromTheFirstFormhashInput() {
        assertEquals("a1b2c3d4", Pages.formhash(Jsoup.parse(fixture("blacklist/list.html"))))
    }

    @Test
    fun formhashIsReadFromTheRealBlacklistPage() {
        val doc = Jsoup.parse(fixture("blacklist/viewblack-real.html"))
        assertEquals("a1b2c3d4", Pages.formhash(doc))
        assertNull(Pages.errorMessage(doc))
    }

    @Test
    fun formhashFallsBackToTheLogoutLink() {
        assertEquals("a1b2c3d4", Pages.formhash(Jsoup.parse(fixture("user/profile-online-real.html"))))
    }

    @Test
    fun formhashIsNullWithoutAForm() {
        assertNull(Pages.formhash(Jsoup.parse(fixture("blacklist/not-logged-in.html"))))
    }

    @Test
    fun errorMessageDropsLinkText() {
        assertEquals(
            "对不起，您还未登录，无法进行此操作。",
            Pages.errorMessage(Jsoup.parse(fixture("blacklist/not-logged-in.html"))),
        )
    }

    @Test
    fun errorMessageIsNullWithoutAnErrorBox() {
        assertNull(Pages.errorMessage(Jsoup.parse(fixture("blacklist/submit-ok.html"))))
    }

    @Test
    fun errorMessageIsNullForAnEmptyErrorBox() {
        assertNull(Pages.errorMessage(Jsoup.parse("<div class='alert_error'><a href='x'>back</a></div>")))
    }

    /** Same cases as the app's Utils.getMiddleString, which [between] replaces. */
    @Test
    fun betweenMatchesGetMiddleString() {
        assertEquals("alice", "space.php?username=alice&from=x".between("username=", "&"))
        assertEquals("alice", "space.php?username=alice".between("username=", "&"))
        assertEquals("alice&x", "space.php?username=alice&x".between("username=", ""))
        assertEquals("", "space.php?uid=1".between("username=", "&"))
        assertEquals("", "space.php?username=&x".between("username=", "&"))
    }
}
