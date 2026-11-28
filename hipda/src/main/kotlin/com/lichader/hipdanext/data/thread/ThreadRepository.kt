package com.lichader.hipdanext.data.thread

import android.content.Context
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.thread.ThreadApi
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.service.NotiHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread pages. Main-safe; throws [ForumException] if a request fails, [ForumException.Parse] for
 * a page without posts, or [AccountChangedException] if the account changed meanwhile.
 */
interface ThreadRepository {

    suspend fun page(tid: String, page: Int, authorId: String?): ThreadPage

    suspend fun lastPage(tid: String): ThreadPage

    suspend fun pageOfPost(pid: String, tid: String?): ThreadPage

    suspend fun authorId(tid: String): String

    /** A thread page the caller already has (the page the forum shows after a post). */
    suspend fun read(html: String, tid: String?): ThreadPage
}

/** Applies what the old `HiParserThreadDetail` did besides parsing. */
@Singleton
class DefaultThreadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ThreadApi,
    private val settings: HiSettingsHelper,
    private val account: AccountRepository,
    private val notiHelper: NotiHelper,
) : ThreadRepository {

    override suspend fun page(tid: String, page: Int, authorId: String?) = remember { api.page(tid, page, authorId) }

    override suspend fun lastPage(tid: String) = remember { api.lastPage(tid) }

    override suspend fun pageOfPost(pid: String, tid: String?) = remember { api.pageOfPost(pid, tid) }

    override suspend fun authorId(tid: String) = api.authorId(tid)

    override suspend fun read(html: String, tid: String?) = remember { api.read(html, tid) }

    private suspend fun remember(request: suspend () -> ThreadPage): ThreadPage {
        val generation = account.generation.value
        val page = request()
        if (account.generation.value != generation) throw AccountChangedException()
        notiHelper.fetchNotificationAsync(context, page.header.noticeCount)
        HiSettingsHelper.updateMobileNetworkStatus(context)
        page.postsPerPage?.let { settings.setMaxPostsInPage(it) }
        return page
    }
}
