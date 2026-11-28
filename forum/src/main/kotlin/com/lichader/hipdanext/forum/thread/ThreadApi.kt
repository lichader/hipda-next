package com.lichader.hipdanext.forum.thread

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.parser.between
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Thread pages. Main-safe; throws [ForumException] on failure, [ForumException.Parse] for a page without posts. */
interface ThreadApi {

    /** Page [page] of thread [tid]; with [authorId], only that author's posts. */
    suspend fun page(tid: String, page: Int, authorId: String?): ThreadPage

    suspend fun lastPage(tid: String): ThreadPage

    /** The page holding post [pid], in thread [tid] if known. */
    suspend fun pageOfPost(pid: String, tid: String?): ThreadPage

    /** The uid of the thread's author, or empty if the first page doesn't say. */
    suspend fun authorId(tid: String): String

    /**
     * Reads a thread page the caller already has, as the old posting code does with the page the
     * forum shows after a post. Until posting moves to this module (W9).
     */
    suspend fun read(html: String, tid: String?): ThreadPage
}

class HiPdaThreadApi(
    private val endpoints: HiPdaEndpoints,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ThreadApi {

    override suspend fun page(tid: String, page: Int, authorId: String?) = parse(endpoints.threadPage(tid, page, authorId), tid)

    override suspend fun lastPage(tid: String) = parse(endpoints.threadLastPage(tid), tid)

    override suspend fun pageOfPost(pid: String, tid: String?) = parse(endpoints.threadPageOfPost(pid, tid), tid)

    override suspend fun authorId(tid: String): String {
        val doc = session.page(endpoints.threadPage(tid, 1, null))
        return withContext(parseDispatcher) { ThreadParser.authorId(doc) }
    }

    override suspend fun read(html: String, tid: String?): ThreadPage = withContext(parseDispatcher) {
        val doc = Jsoup.parse(html)
        ThreadParser.parse(doc, threadId(tid, doc), endpoints.baseUrl)
    }

    private suspend fun parse(url: String, tid: String?): ThreadPage {
        val doc = session.page(url)
        return withContext(parseDispatcher) { ThreadParser.parse(doc, threadId(tid, doc), endpoints.baseUrl) }
    }

    /** The requested thread, or the one the page names in its script, as for a post's page. */
    private fun threadId(tid: String?, doc: Document): String =
        if (tid != null && (tid.trim().toIntOrNull() ?: 0) > 0) tid else doc.html().between("tid = parseInt('", "')")
}
