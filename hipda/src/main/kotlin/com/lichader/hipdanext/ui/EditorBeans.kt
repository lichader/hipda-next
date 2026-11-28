package com.lichader.hipdanext.ui

import com.lichader.hipdanext.async.PostHelper
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage

/** Between the app's post modes (`PostHelper.MODE_*`) and the forum's editor. */
object EditorBeans {

    /** The editor a post in [mode] starts from; a reply's for modes without one, as before. */
    @JvmStatic
    fun kind(mode: Int, fid: Int, tid: String?, pid: String?): EditorKind = when (mode) {
        PostHelper.MODE_REPLY_POST -> EditorKind.ReplyToPost(tid.orEmpty(), pid.orEmpty())
        PostHelper.MODE_QUOTE_POST -> EditorKind.Quote(tid.orEmpty(), pid.orEmpty())
        PostHelper.MODE_NEW_THREAD -> EditorKind.NewThread(fid)
        PostHelper.MODE_EDIT_POST -> EditorKind.Edit(fid, tid.orEmpty(), pid.orEmpty())
        else -> EditorKind.Reply(tid.orEmpty())
    }

    /** The editor as the legacy bean the editor screen and `PostHelper` work on. */
    @JvmStatic
    fun of(page: EditorPage, mode: Int): PrePostInfoBean {
        val quoting = mode == PostHelper.MODE_REPLY_POST || mode == PostHelper.MODE_QUOTE_POST
        return PrePostInfoBean().apply {
            formhash = page.formhash
            page.text?.let { if (quoting) quoteText = it else text = it }
            uid = page.uid
            hash = page.hash
            subject = page.subject
            isDeleteable = page.deletable
            noticeAuthor = page.noticeAuthor
            noticeAuthorMsg = page.noticeAuthorMsg
            noticeTrimStr = page.noticeTrimStr
            page.images.forEach(::addImage)
            page.attachments.forEach(::addAttach)
            typeValues = LinkedHashMap(page.types)
            typeId = page.typeId
        }
    }
}
