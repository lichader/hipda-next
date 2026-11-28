package com.lichader.hipdanext.forum.session

import com.lichader.hipdanext.forum.ForumConfig

/**
 * Holds the forum's `formhash`, the token every form submission must carry. The forum
 * issues one token per login session and repeats it on every page with a form, so a value
 * read from any page can be reused. The value is tied to [ForumConfig.sessionKey], so it
 * is dropped as soon as the user logs in again or switches accounts.
 */
class FormhashCache(private val config: ForumConfig) {

    private var sessionKey: String? = null
    private var formhash: String? = null

    /** The token for the current session, or null if none has been seen yet. */
    @Synchronized
    fun get(): String? = formhash?.takeIf { sessionKey == config.sessionKey }

    /** Records a token read from a page; null or empty values are ignored. */
    @Synchronized
    fun put(value: String?) {
        if (value.isNullOrEmpty()) return
        sessionKey = config.sessionKey
        formhash = value
    }
}
