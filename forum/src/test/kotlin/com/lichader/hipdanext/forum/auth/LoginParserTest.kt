package com.lichader.hipdanext.forum.auth

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.fixture
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginParserTest {

    @Test
    fun tokenComesFromTheRealLoginForm() {
        assertEquals("a1b2c3d4", LoginParser.formToken(Jsoup.parse(fixture("auth/login-form-real.html"))))
    }

    @Test
    fun aNoticeInPlaceOfTheFormIsTheForumsMessage() {
        val e = assertThrows(ForumException.Server::class.java) {
            LoginParser.formToken(Jsoup.parse(fixture("auth/login-form-unavailable.html")))
        }
        assertEquals("您已经登录，请勿重复登录。", e.message)
    }

    @Test
    fun aPageWithoutTokenOrNoticeIsAParseError() {
        assertThrows(ForumException.Parse::class.java) {
            LoginParser.formToken(Jsoup.parse(fixture("user/profile-online-real.html")))
        }
    }

    @Test
    fun realWelcomeReplyIsSuccess() {
        assertEquals(LoginResult.Success, LoginParser.result(fixture("auth/login-success-real.xml")))
    }

    @Test
    fun refusalCarriesTheForumsTextUpToTheAttemptsLeft() {
        assertEquals(LoginResult.Rejected("登录失败，您还可以尝试 4 次"), LoginParser.result(fixture("auth/login-failed.xml")))
    }

    @Test
    fun refusalWithoutAttemptsHasNoMessage() {
        assertEquals(LoginResult.Rejected(null), LoginParser.result("<root><![CDATA[<p>登录失败</p>]]></root>"))
    }

    @Test
    fun otherRepliesAreUnrecognized() {
        assertEquals(LoginResult.Unrecognized, LoginParser.result(fixture("auth/login-locked.xml")))
    }

    @Test
    fun passwordIsSentAsTheMd5OfTheEscapedPlainText() {
        // md5("secret") and md5("a\\\\b\\\"c"), the digests the old LoginHelper computed.
        assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", forumPassword("secret"))
        assertEquals(md5Hex("a\\\\b\\\"c"), forumPassword("a\\b\"c"))
        assertEquals("", forumPassword(""))
        assertEquals("0123456789abcdef0123456789abcdef", forumPassword("0123456789abcdef0123456789abcdef"))
    }

    private fun md5Hex(s: String) = java.security.MessageDigest.getInstance("MD5")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { String.format("%02x", it.toInt() and 0xff) }
}
