package com.lichader.hipdanext.forum.user

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.session.FormhashCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Users' public profiles. All functions are main-safe and throw [ForumException] on failure. */
interface UserApi {

    suspend fun profile(uid: String): UserProfile
}

class HiPdaUserApi(
    private val transport: ForumTransport,
    private val endpoints: HiPdaEndpoints,
    private val formhash: FormhashCache,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UserApi {

    override suspend fun profile(uid: String): UserProfile {
        val html = transport.get(endpoints.userProfile(uid))
        return withContext(parseDispatcher) {
            val doc = Jsoup.parse(html)
            // The page carries the session's form token, which later form submissions can reuse.
            formhash.put(Pages.formhash(doc))
            UserProfileParser.parse(doc)
        }
    }
}
