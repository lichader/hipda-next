package com.lichader.hipdanext.forum.blacklist

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.session.FormhashCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * The logged-in user's blacklist on the forum: the users whose threads and posts the app
 * hides. All functions are main-safe and throw [ForumException] on failure.
 */
interface BlacklistApi {

    /** The blacklisted usernames, in the forum's order. */
    suspend fun fetch(): List<String>

    suspend fun add(username: String)

    suspend fun remove(username: String)
}

class HiPdaBlacklistApi(
    private val transport: ForumTransport,
    private val endpoints: HiPdaEndpoints,
    private val formhash: FormhashCache,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BlacklistApi {

    override suspend fun fetch(): List<String> {
        val html = transport.get(endpoints.viewBlacklist())
        return withContext(parseDispatcher) {
            val doc = Jsoup.parse(html)
            formhash.put(Pages.formhash(doc))
            BlacklistParser.parse(doc)
        }
    }

    override suspend fun add(username: String) = submit(endpoints.addToBlacklist(), username)

    override suspend fun remove(username: String) = submit(endpoints.removeFromBlacklist(), username)

    private suspend fun submit(url: String, username: String) {
        val token = formhash.get() ?: fetchFormhash()
        val html = transport.post(url, linkedMapOf("formhash" to token, "user" to username))
        val error = withContext(parseDispatcher) { Pages.errorMessage(Jsoup.parse(html)) }
        if (error != null) throw ForumException.Server(error)
    }

    /** Reads a token from the blacklist page, which every logged-in user can open. */
    private suspend fun fetchFormhash(): String {
        fetch()
        return formhash.get() ?: throw ForumException.Parse("No formhash on the blacklist page")
    }
}
