package com.lichader.hipdanext.forum.lists

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.parser.Pages
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

    suspend fun notices(): ListPage

    /**
     * New posts of the last day. The first page starts a search; later pages pass the
     * [ListPage.searchId] it returned.
     */
    suspend fun newPosts(searchId: String?, page: Int): ListPage

    suspend fun savedThreads(list: SavedThreads, page: Int): ListPage
}

class HiPdaListsApi(
    private val endpoints: HiPdaEndpoints,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ListsApi {

    override suspend fun myReplies(page: Int) = fetch(endpoints.myReplies(page), ListParsers::myReplies)

    override suspend fun myThreads(page: Int) = fetch(endpoints.myThreads(page), ListParsers::myThreads)

    override suspend fun messages() = fetch(endpoints.messages(), ListParsers::messages)

    override suspend fun notices() = fetch(endpoints.notices(), ListParsers::notices)

    override suspend fun newPosts(searchId: String?, page: Int) = fetch(endpoints.newPosts(searchId, page), ListParsers::search)

    override suspend fun savedThreads(list: SavedThreads, page: Int) =
        fetch(endpoints.savedThreads(list.item, page), ListParsers::savedThreads)

    private suspend fun fetch(url: String, parse: (Document) -> ListParsers.Parsed): ListPage {
        val doc = session.page(url)
        return withContext(parseDispatcher) {
            val header = pageHeader(doc)
            val formhash = Pages.formhash(doc)
            val parsed = parse(doc)
            ListPage(parsed.items, parsed.maxPage, parsed.searchId, formhash, header)
        }
    }
}
