package com.lichader.hipdanext.ui

import com.lichader.hipdanext.bean.ThreadBean
import com.lichader.hipdanext.forum.threads.ThreadSummary
import com.lichader.hipdanext.utils.HiUtils

/** The legacy adapter's [ThreadBean]s for the thread list, until the list is redesigned. */
object ThreadBeans {

    @JvmStatic
    fun of(threads: List<ThreadSummary>): List<ThreadBean> = threads.map { t ->
        ThreadBean().apply {
            setTid(t.tid)
            setTitle(t.title)
            t.titleColor?.let(::setTitleColor)
            t.type?.let(::setType)
            setNew(t.isNew)
            setSticky(t.sticky)
            setPoll(t.poll)
            setAuthor(t.author)
            setAuthorId(t.authorId)
            setAvatarUrl(HiUtils.getAvatarUrlByUid(t.authorId))
            setTimeCreate(t.timeCreate)
            t.timeUpdate?.let(::setTimeUpdate)
            setCountCmts(t.replyCount)
            setCountViews(t.viewCount)
            setLastPost(t.lastPost)
            setWithPic(t.withPictures)
            setMaxPage(t.maxPage)
        }
    }
}
