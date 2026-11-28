package com.lichader.hipdanext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.utils.HiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI

@RunWith(AndroidJUnit4::class)
class SettingsForumConfigTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val cookies = CookieManager().cookieStore
    private val config = SettingsForumConfig(settings, cookies)

    @Test
    fun endpointsUseTheSameBaseUrlAsHiUtils() {
        HiUtils.updateBaseUrls(settings)
        assertEquals(HiUtils.BaseUrl, HiPdaEndpoints(config).baseUrl)
    }

    @Test
    fun charsetFollowsTheEncodingSetting() {
        assertEquals(settings.encode, config.charset.name())
    }

    @Test
    fun sessionKeyIsTheAuthCookie() {
        assertNull(config.sessionKey)
        cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_sid", "sid"))
        cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_auth", "auth-1"))
        assertEquals("auth-1", config.sessionKey)
    }
}
