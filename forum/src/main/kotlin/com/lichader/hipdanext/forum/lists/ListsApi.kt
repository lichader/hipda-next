package com.lichader.hipdanext.forum.lists

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.parser.pageHeader
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/** The account's simple lists. Main-safe; throws [ForumException] on failure. */
interface ListsApi {

    suspend fun myReplies(page: Int): ListPage

    suspend fun myThreads(page: Int): ListPage

    /** The private message conversations. */
    suspend fun messages(): ListPage

    /** Conversations with unread messages; empty when the page has no list, as when there are none. */
    suspend fun newMessages(): ListPage

    suspend fun notices(): ListPage

    /**
     * New posts of the last day. The first page starts a search; later pages pass the
     * [ListPage.searchId] it returned.
     */
    suspend fun newPosts(searchId: String?, page: Int): ListPage

    suspend fun savedThreads(list: SavedThreads, page: Int): ListPage

    /** Page [page] of a search; each page repeats the search, as before. */
    suspend fun search(query: SearchQuery, page: Int): ListPage

    /**
     * A user's threads. The first page starts a search; later pages pass the
     * [ListPage.searchId] it returned.
     */
    suspend fun userThreads(uid: String, searchId: String?, page: Int): ListPage
}

class HiPdaListsApi(
    private val endpoints: HiPdaEndpoints,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ListsApi {

    override suspend fun myReplies(page: Int) = fetch(endpoints.myReplies(page), ListParsers::myReplies)

    override suspend fun myThreads(page: Int) = fetch(endpoints.myThreads(page), ListParsers::myThreads)

    override suspend fun messages() = fetch(endpoints.messages(), ListParsers::messages)

    override suspend fun newMessages() = fetch(endpoints.newMessages(), ListParsers::messages)

    override suspend fun notices() = fetch(endpoints.notices(), ListParsers::notices)

    override suspend fun newPosts(searchId: String?, page: Int) = fetch(endpoints.newPosts(searchId, page), ListParsers::search)

    override suspend fun savedThreads(list: SavedThreads, page: Int) =
        fetch(endpoints.savedThreads(list.item, page), ListParsers::savedThreads)

    override suspend fun search(query: SearchQuery, page: Int) = fetch(
        endpoints.search(query.text, query.author, query.forumId, query.fullText, page),
        if (query.fullText) ListParsers::fullTextSearch else ListParsers::search,
    )

    override suspend fun userThreads(uid: String, searchId: String?, page: Int) =
        fetch(endpoints.userThreads(uid, searchId, page), ListParsers::search)

    private suspend fun fetch(url: String, parse: (Document) -> ListParsers.Parsed): ListPage {
        val doc = session.page(url)
        return withContext(parseDispatcher) {
            val parsed = parse(doc)
            ListPage(parsed.items, parsed.maxPage, parsed.searchId, pageHeader(doc))
        }
    }
}
