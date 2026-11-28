package com.lichader.hipdanext.forum

import java.nio.charset.Charset

/**
 * Settings the site adapter needs from the app. The app implements this over its own
 * settings and cookie store. Every property is read again on each use, so a change in
 * the settings applies to the next request.
 */
interface ForumConfig {

    /** Scheme and host of the forum server, without a trailing slash, such as `https://www.4d4y.com`. */
    val forumServer: String

    /** Charset for form values, and for responses whose `Content-Type` names no charset. */
    val charset: Charset

    val userAgent: String

    /**
     * Identifies the current login session. It changes whenever the user logs in, out, or
     * switches accounts. It is null when no session exists. Session-bound values, such as
     * the form token, are discarded when it changes.
     */
    val sessionKey: String?
}
