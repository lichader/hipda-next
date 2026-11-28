package com.lichader.hipdanext.forum.session

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.parser.Pages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Logs in again with the stored credentials. Supplied by the app. */
fun interface SessionRenewer {

    /** @return true if the login succeeded */
    suspend fun renew(): Boolean
}

/**
 * Fetches pages that need a session, and recovers when the session has expired: if a page comes
 * back logged out ([Pages.isLoggedOut]), it logs in again once through [SessionRenewer] and fetches
 * the page again. Requests that find the session expired at the same time share one login.
 */
class SessionGuard(
    private val transport: ForumTransport,
    private val renewer: SessionRenewer,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val mutex = Mutex()

    /** How many times the session was renewed; guarded by [mutex]. */
    private var renewals = 0

    /**
     * The page at [url], parsed.
     *
     * @throws ForumException.NotLoggedIn if the page is logged out and logging in again fails, or
     * the page is still logged out afterwards
     */
    suspend fun page(url: String): Document {
        val seen = mutex.withLock { renewals }
        val doc = load(url)
        if (!Pages.isLoggedOut(doc)) return doc
        if (!renew(seen)) throw ForumException.NotLoggedIn()
        val again = load(url)
        if (Pages.isLoggedOut(again)) throw ForumException.NotLoggedIn()
        return again
    }

    private suspend fun load(url: String): Document {
        val html = transport.get(url)
        return withContext(parseDispatcher) { Jsoup.parse(html) }
    }

    /** Logs in again, unless another request already did since it read [seen]. One login at a time. */
    private suspend fun renew(seen: Int): Boolean = mutex.withLock {
        if (renewals != seen) return@withLock true
        renewer.renew().also { if (it) renewals++ }
    }
}
