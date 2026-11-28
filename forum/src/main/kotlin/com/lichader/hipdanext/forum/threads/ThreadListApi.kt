package com.lichader.hipdanext.forum.threads

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.parser.pageHeader
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A forum's thread list. Main-safe; throws [ForumException] on failure. */
interface ThreadListApi {

    /**
     * Page [page] of forum [forumId]'s threads.
     *
     * @param typeId the thread type to show, or null for all
     * @param orderByPostTime newest threads first instead of the latest replies first
     */
    suspend fun page(forumId: Int, page: Int, typeId: String?, orderByPostTime: Boolean): ThreadListPage
}

class HiPdaThreadListApi(
    private val endpoints: HiPdaEndpoints,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ThreadListApi {

    override suspend fun page(forumId: Int, page: Int, typeId: String?, orderByPostTime: Boolean): ThreadListPage {
        val doc = session.page(endpoints.threadList(forumId, page, typeId, orderByPostTime))
        return withContext(parseDispatcher) { ThreadListPage(ThreadListParser.parse(doc), pageHeader(doc)) }
    }
}
