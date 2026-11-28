package com.lichader.hipdanext.async

import com.lichader.hipdanext.bean.DetailListBean
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.bean.PostBean
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.forum.post.PostForm
import com.lichader.hipdanext.forum.post.PostResult
import com.lichader.hipdanext.forum.post.PostTarget
import com.lichader.hipdanext.job.PostEvent
import com.lichader.hipdanext.job.ThreadUpdatedEvent
import com.lichader.hipdanext.ui.EditorBeans
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.ui.ThreadDetailBeans
import com.lichader.hipdanext.utils.Constants
import com.lichader.hipdanext.utils.HiUtils
import com.lichader.hipdanext.utils.Logger
import com.lichader.hipdanext.utils.Utils
import com.vdurmont.emoji.EmojiParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends posts for the Java screens (rule 7), as the old `PostJob` and `PostHelper` did: replies,
 * quotes, new threads, edits, quick deletes and poll votes. A post outlives the screen that sent
 * it, so it runs in the application scope; the screens hear about it through the sticky
 * [PostEvent], until EventBus goes (W12).
 */
@Singleton
class PostSender @Inject constructor(
    private val posts: PostRepository,
    private val threads: ThreadRepository,
    private val settings: HiSettingsHelper,
    private val errors: ForumErrorFormatter,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * Sends [post] in [mode] (`PostHelper.MODE_*`), from the editor [info], or loads the editor
     * first if it is null. Posts a sticky [PostEvent] for [sessionId] in progress, then another
     * with the result: [post] itself, with its status and message.
     */
    fun send(sessionId: String, mode: Int, info: PrePostInfoBean?, post: PostBean, fromQuickReply: Boolean) {
        val event = PostEvent().apply {
            mMode = mode
            mSessionId = sessionId
            this.fromQuickReply = fromQuickReply
            mStatus = Constants.STATUS_IN_PROGRESS
        }
        // Not immediate: the screen hears of it after the click that sent it, as before.
        scope.launch(Dispatchers.Main) {
            EventBus.getDefault().postSticky(event)
            run(event, mode, info, post)
        }
    }

    internal suspend fun run(event: PostEvent, mode: Int, info: PrePostInfoBean?, post: PostBean) {
        val start = System.currentTimeMillis()
        var sendMode = mode
        var editor = info
        if (mode == PostHelper.MODE_QUICK_DELETE) {
            // Delete the post if the forum allows, or else empty it and remove its attachments.
            editor = try {
                EditorBeans.of(posts.editor(EditorBeans.kind(PostHelper.MODE_EDIT_POST, post.fid, post.tid, post.pid)), PostHelper.MODE_EDIT_POST)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                event.mStatus = Constants.STATUS_FAIL
                event.mMessage = errors.format(e)
                EventBus.getDefault().postSticky(event)
                return
            }
            sendMode = PostHelper.MODE_EDIT_POST
            if (editor.isDeleteable) {
                post.isDelete = true
            } else {
                post.isDelete = false
                post.content = "....."
                editor.allImages.forEach(editor::addDeleteAttach)
                editor.attaches.forEach(editor::addDeleteAttach)
            }
        }

        Sending(sendMode, editor, post).send()
        event.mPostResult = post
        event.mStatus = post.status
        event.mMessage = post.message

        delay(MIN_SEND_TIME_MS - (System.currentTimeMillis() - start))
        EventBus.getDefault().postSticky(event)

        val data = post.detailListBean
        if (data != null && data.page == data.lastPage && HiUtils.isForumValid(data.fid)) {
            EventBus.getDefault().postSticky(ThreadUpdatedEvent().apply {
                mFid = data.fid
                mTid = data.tid
                mTitle = data.title
                mReplyCount = data.all[data.count - 1].floor - 1
            })
        }
    }

    /** One post, with what the old `PostHelper` kept while sending it. */
    private inner class Sending(private val mode: Int, private var info: PrePostInfoBean?, private val post: PostBean) {
        private var result: String? = null
        private var status = Constants.STATUS_FAIL
        private var detail: DetailListBean? = null
        private var tid: String? = post.tid
        private var title: String? = null

        suspend fun send() {
            val tid = post.tid
            val pid = post.pid
            val fid = post.fid
            var subject = post.subject

            var count = 0
            while (info == null && count < 3) {
                count++
                info = try {
                    EditorBeans.of(posts.editor(EditorBeans.kind(mode, fid, tid, pid)), mode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }

            var replyText = EmojiParser.parseToHtmlDecimal(replaceToTags(post.content))
            if (!subject.isNullOrEmpty()) subject = EmojiParser.parseToHtmlDecimal(subject)
            if (mode != PostHelper.MODE_EDIT_POST) {
                val tail = settings.tailStr
                if (!tail.isNullOrEmpty() && settings.isAddTail && !replyText.trim().endsWith(tail)) replyText += "  $tail"
            }

            when (mode) {
                PostHelper.MODE_REPLY_THREAD, PostHelper.MODE_QUICK_REPLY -> send(PostTarget.Reply(tid.orEmpty()), replyText)
                PostHelper.MODE_REPLY_POST, PostHelper.MODE_QUOTE_POST -> send(
                    PostTarget.Reply(tid.orEmpty()),
                    // The old code stopped here with no editor; this fails as below instead.
                    EmojiParser.parseToHtmlDecimal(info?.quoteText.orEmpty()) + "\n\n    " + replyText,
                )
                PostHelper.MODE_NEW_THREAD -> send(PostTarget.NewThread(fid, post.typeid), replyText, subject)
                PostHelper.MODE_EDIT_POST -> send(PostTarget.Edit(fid, tid.orEmpty(), pid.orEmpty(), post.page), replyText, subject, post.typeid, post.isDelete)
                PostHelper.MODE_VOTE_POLL -> send(PostTarget.Vote(fid, tid.orEmpty()), replyText)
            }

            post.subject = title
            post.tid = this.tid
            post.message = result
            post.status = status
            post.detailListBean = detail
        }

        private suspend fun send(target: PostTarget, message: String, subject: String? = null, typeId: String? = null, delete: Boolean = false) {
            val editor = info
            if (editor == null || editor.formhash.isNullOrEmpty()) {
                result = "发表失败，无法获取必要信息 ！"
                status = Constants.STATUS_FAIL
                return
            }
            val quoting = mode == PostHelper.MODE_REPLY_POST || mode == PostHelper.MODE_QUOTE_POST
            when (target) {
                is PostTarget.NewThread -> title = subject
                is PostTarget.Edit -> if (!subject.isNullOrEmpty()) title = subject
                else -> Unit
            }
            val form = PostForm(
                formhash = editor.formhash,
                message = message,
                subject = subject,
                typeId = typeId,
                delete = delete,
                newAttachments = editor.newAttaches.toList(),
                deletedAttachments = editor.deleteAttaches.toList(),
                pollAnswers = if (target is PostTarget.Vote) post.pollAnswers.orEmpty() else emptyList(),
                noticeAuthor = if (quoting) editor.noticeAuthor else null,
                noticeAuthorMsg = if (quoting) editor.noticeAuthorMsg else null,
                noticeTrimStr = if (quoting) editor.noticeTrimStr else null,
            )

            var text = try {
                when (val answer = posts.submit(target, form)) {
                    is PostResult.Posted -> {
                        tid = answer.tid
                        status = Constants.STATUS_SUCCESS
                        detail = read(answer.html, answer.tid)
                        "发表成功!"
                    }
                    PostResult.ThreadDeleted -> {
                        status = Constants.STATUS_SUCCESS
                        "删除成功!"
                    }
                    is PostResult.Refused -> {
                        status = Constants.STATUS_FAIL
                        "发表失败! " + (answer.message?.let { "\n" + it } ?: "")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e)
                status = Constants.STATUS_FAIL
                "发表失败 : " + errors.format(e)
            }
            if (delete) text = text.replace("发表", "删除")
            result = text

            if (status == Constants.STATUS_SUCCESS && (mode != PostHelper.MODE_EDIT_POST || delete)) PostHelper.markPosted()
        }

        /** The thread page the forum went on to, if it has posts. */
        private suspend fun read(html: String, tid: String): DetailListBean? = try {
            ThreadDetailBeans.of(threads.read(html, tid), settings).takeIf { it.count > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e)
            null
        }
    }

    private companion object {
        const val MIN_SEND_TIME_MS = 500L

        /** Wraps web addresses outside BBCode tags in [url] tags. */
        fun replaceToTags(replyText: String?): String {
            var text = replyText
            val sb = StringBuilder()
            try {
                while (!text.isNullOrEmpty()) {
                    val tagStart = text.indexOf("[")
                    if (tagStart == -1) {
                        sb.append(Utils.replaceUrlWithTag(text))
                        break
                    }
                    val tagEnd = text.indexOf("]", tagStart)
                    if (tagEnd == -1) {
                        sb.append(Utils.replaceUrlWithTag(text))
                        break
                    }
                    var tag = text.substring(tagStart + 1, tagEnd)
                    if ("=" in tag) tag = tag.substring(0, tag.indexOf("="))
                    val tagE = "[/$tag]"
                    var tagEIndex = text.indexOf(tagE)
                    if (tagEIndex == -1) {
                        sb.append(Utils.replaceUrlWithTag(text))
                        break
                    }
                    tagEIndex += tagE.length
                    sb.append(Utils.replaceUrlWithTag(text.substring(0, tagStart)))
                    sb.append(text.substring(tagStart, tagEIndex))
                    text = text.substring(tagEIndex)
                }
            } catch (e: Exception) {
                Logger.e(e)
                return replyText.orEmpty()
            }
            return sb.toString()
        }
    }
}
