package com.lichader.hipdanext.forum.favorites

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.session.FormhashCache
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Adding threads to and removing them from favorites and attention. Main-safe; throws [ForumException] on failure. */
interface FavoritesApi {

    /** Adds thread [tid] to [list]; returns the forum's message. */
    suspend fun add(list: SavedThreads, tid: String): String

    /** Removes thread [tid] from [list]; returns the forum's message. */
    suspend fun remove(list: SavedThreads, tid: String): String

    /**
     * Removes thread [tid] from [list] through the list page's form, as the list screen does.
     *
     * @throws ForumException.Parse if the form token can't be found
     */
    suspend fun delete(list: SavedThreads, tid: String)
}

class HiPdaFavoritesApi(
    private val transport: ForumTransport,
    private val endpoints: HiPdaEndpoints,
    private val formhash: FormhashCache,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FavoritesApi {

    override suspend fun add(list: SavedThreads, tid: String): String =
        message(transport.get(endpoints.addSavedThread(list.item, tid)))

    override suspend fun remove(list: SavedThreads, tid: String): String =
        message(transport.get(endpoints.removeSavedThread(list.item, tid)))

    override suspend fun delete(list: SavedThreads, tid: String) {
        val token = formhash.get() ?: fetchFormhash(list)
        val submit = if (list == SavedThreads.FAVORITES) "favsubmit" else "attentionsubmit"
        // The reply is the list page again; as before, it isn't read.
        transport.post(endpoints.savedThreads(list.item, 1), linkedMapOf(submit to "true", "delete[]" to tid, "formhash" to token))
    }

    private suspend fun message(reply: String): String = withContext(parseDispatcher) { Pages.ajaxMessage(reply) }

    private suspend fun fetchFormhash(list: SavedThreads): String {
        val doc = session.page(endpoints.savedThreads(list.item, 1))
        withContext(parseDispatcher) { formhash.put(Pages.formhash(doc)) }
        return formhash.get() ?: throw ForumException.Parse("No formhash on the ${list.item} page")
    }
}
