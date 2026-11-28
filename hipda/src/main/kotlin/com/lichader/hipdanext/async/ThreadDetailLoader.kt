package com.lichader.hipdanext.async

import androidx.annotation.WorkerThread
import com.lichader.hipdanext.bean.DetailListBean
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.ui.ThreadDetailBeans
import com.lichader.hipdanext.utils.Constants
import com.lichader.hipdanext.utils.Logger
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** A thread page load, in the old job's terms: `Constants.STATUS_*` and its message. */
class ThreadLoad(val data: DetailListBean?, val status: Int, val message: String)

/**
 * Loads thread pages for `ThreadDetailJob` and `PostHelper`, which run on worker threads (rule 7),
 * until thread detail (W10b) and posting (W9) get view models. Calls block.
 */
@Singleton
class ThreadDetailLoader @Inject constructor(
    private val repository: ThreadRepository,
    private val settings: HiSettingsHelper,
    private val errors: ForumErrorFormatter,
) {

    @WorkerThread
    fun page(tid: String, page: Int, authorId: String?): ThreadLoad = load { repository.page(tid, page, authorId) }

    @WorkerThread
    fun lastPage(tid: String): ThreadLoad = load { repository.lastPage(tid) }

    @WorkerThread
    fun pageOfPost(pid: String, tid: String?): ThreadLoad = load { repository.pageOfPost(pid, tid) }

    /** The uid of the thread's author, or empty if it can't be read, as before. */
    @WorkerThread
    fun authorId(tid: String): String = try {
        runBlocking { repository.authorId(tid) }
    } catch (e: Exception) {
        ""
    }

    /** The thread page the forum shows after a post; null if it can't be read. */
    @WorkerThread
    fun read(html: String, tid: String?): DetailListBean? = try {
        ThreadDetailBeans.of(runBlocking { repository.read(html, tid) }, settings)
    } catch (e: Exception) {
        Logger.e(e)
        null
    }

    private fun load(request: suspend () -> ThreadPage): ThreadLoad = try {
        val data = ThreadDetailBeans.of(runBlocking { request() }, settings)
        if (data.count == 0) ThreadLoad(data, Constants.STATUS_FAIL_ABORT, "页面加载失败") else ThreadLoad(data, Constants.STATUS_SUCCESS, "")
    } catch (e: ForumException.NotLoggedIn) {
        ThreadLoad(null, Constants.STATUS_FAIL_RELOGIN, "请重新登录")
    } catch (e: ForumException.Parse) {
        ThreadLoad(null, Constants.STATUS_FAIL_ABORT, "页面加载失败")
    } catch (e: Exception) {
        ThreadLoad(null, Constants.STATUS_FAIL, errors.format(e))
    }
}
