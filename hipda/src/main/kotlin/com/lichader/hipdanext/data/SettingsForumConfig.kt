package com.lichader.hipdanext.data

import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.utils.HiUtils
import java.net.CookieStore
import java.nio.charset.Charset

/** Supplies the site adapter with the user's settings and the current login cookie. */
class SettingsForumConfig(
    private val settings: HiSettingsHelper,
    private val cookieStore: CookieStore,
) : ForumConfig {

    override val forumServer: String
        get() = settings.forumServer

    override val charset: Charset
        get() = Charset.forName(settings.encode)

    override val userAgent: String
        get() = HiUtils.getUserAgent()

    /** The forum's auth cookie, which it replaces on every login. */
    override val sessionKey: String?
        get() = cookieStore.cookies.firstOrNull { it.name == AUTH_COOKIE }?.value

    private companion object {
        const val AUTH_COOKIE = "cdb_auth"
    }
}
