package com.lichader.hipdanext.async

import androidx.annotation.WorkerThread
import com.lichader.hipdanext.bean.DetailListBean
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.ui.ThreadDetailBeans
import com.lichader.hipdanext.utils.Logger
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the thread page the forum shows after a post, for `PostHelper`, which runs on a worker
 * thread (rule 7), until posting gets a view model (W9). Calls block.
 */
@Singleton
class ThreadDetailLoader @Inject constructor(
    private val repository: ThreadRepository,
    private val settings: HiSettingsHelper,
) {

    /** The thread page the forum shows after a post; null if it can't be read. */
    @WorkerThread
    fun read(html: String, tid: String?): DetailListBean? = try {
        ThreadDetailBeans.of(runBlocking { repository.read(html, tid) }, settings)
    } catch (e: Exception) {
        Logger.e(e)
        null
    }
}
