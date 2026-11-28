package com.lichader.hipdanext.async

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.bean.PostBean
import com.lichader.hipdanext.bean.PrePostInfoBean
import com.lichader.hipdanext.data.post.PostRepository
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.forum.post.PostForm
import com.lichader.hipdanext.forum.post.PostResult
import com.lichader.hipdanext.forum.post.PostTarget
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.job.PostEvent
import com.lichader.hipdanext.job.ThreadUpdatedEvent
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.net.UnknownHostException
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class PostSenderTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val posts = FakePostRepository()
    private val sender = PostSender(posts, FakeThreadRepository(), settings, ForumErrorFormatter { true }, CoroutineScope(SupervisorJob()))

    @Before
    fun setUp() {
        settings.isAddTail = false
        PostHelper::class.java.getDeclaredField("LAST_POST_TIME").apply { isAccessible = true }.setLong(null, 0)
        EventBus.getDefault().removeAllStickyEvents()
    }

    private fun editor() = PrePostInfoBean().apply {
        formhash = "a1b2c3d4"
        quoteText = "[quote]引用[/quote]"
        noticeAuthor = "r|200001|user_1"
        noticeAuthorMsg = "内容 1"
        noticeTrimStr = "[b]回复 [/b]"
        addNewAttach("9000001")
        addDeleteAttach("9000002")
    }

    private fun post(content: String? = "你好 😀") = PostBean().apply {
        this.content = content
        tid = "3000001"
        pid = "4000001"
        fid = 2
        floor = 5
        page = 3
        subject = "主题 😀"
        typeid = "9"
        pollAnswers = mutableListOf("3", "11")
    }

    private fun send(mode: Int, post: PostBean = post(), info: PrePostInfoBean? = editor()): PostEvent {
        val event = PostEvent()
        runBlocking { sender.run(event, mode, info, post) }
        return event
    }

    private val waiting get() = PostHelper.getWaitTimeToPost() > 0

    @Test
    fun aReplyToAPost() {
        settings.isAddTail = true
        settings.tailText = "尾巴"
        settings.tailUrl = ""

        val event = send(PostHelper.MODE_QUOTE_POST)

        val (target, form) = posts.sent.single()
        assertEquals(PostTarget.Reply("3000001"), target)
        assertEquals("[quote]引用[/quote]\n\n    你好 &#128512;  [size=1]尾巴[/size]", form.message)
        assertEquals(listOf("r|200001|user_1", "内容 1", "[b]回复 [/b]"), listOf(form.noticeAuthor, form.noticeAuthorMsg, form.noticeTrimStr))
        assertEquals(listOf(listOf("9000001"), listOf("9000002")), listOf(form.newAttachments, form.deletedAttachments))
        assertEquals(listOf(Constants.STATUS_SUCCESS, "发表成功!", "3000002", null, 5), listOf(event.mStatus, event.mMessage, event.mPostResult.tid, event.mPostResult.subject, event.mPostResult.floor))
        assertEquals(1, event.mPostResult.detailListBean.count)
        assertTrue(waiting)
    }

    @Test
    fun aReplyToTheThreadSendsNoNotice() {
        send(PostHelper.MODE_REPLY_THREAD)

        val form = posts.sent.single().second
        assertEquals("你好 &#128512;", form.message)
        assertNull(form.noticeAuthor)
    }

    @Test
    fun aNewThreadAnEditAndAVote() {
        assertEquals("主题 &#128512;", send(PostHelper.MODE_NEW_THREAD).mPostResult.subject)
        assertEquals("主题 &#128512;", send(PostHelper.MODE_EDIT_POST).mPostResult.subject)
        send(PostHelper.MODE_VOTE_POLL)

        assertEquals(listOf(PostTarget.NewThread(2, "9"), PostTarget.Edit(2, "3000001", "4000001", 3), PostTarget.Vote(2, "3000001")), posts.sent.map { it.first })
        assertEquals(listOf("主题 &#128512;", "9"), posts.sent[1].second.let { listOf(it.subject, it.typeId) })
        assertEquals(listOf("3", "11"), posts.sent[2].second.pollAnswers)
    }

    @Test
    fun anEditStartsNoWaitUnlessItDeletes() {
        send(PostHelper.MODE_EDIT_POST)
        assertFalse(waiting)

        posts.answer = PostResult.ThreadDeleted
        val event = send(PostHelper.MODE_EDIT_POST, post().apply { isDelete = true })
        assertEquals(listOf(Constants.STATUS_SUCCESS, "删除成功!"), listOf(event.mStatus, event.mMessage))
        assertTrue(posts.sent.last().second.delete)
        assertTrue(waiting)
    }

    @Test
    fun failuresAsBefore() {
        posts.answer = PostResult.Refused("您两次发表间隔少于 30 秒")
        assertEquals("发表失败! \n您两次发表间隔少于 30 秒", send(PostHelper.MODE_REPLY_THREAD).mMessage)
        posts.answer = PostResult.Refused(null)
        assertEquals("删除失败! ", send(PostHelper.MODE_EDIT_POST, post().apply { isDelete = true }).mMessage)
        posts.error = ForumException.Network(UnknownHostException())
        val event = send(PostHelper.MODE_REPLY_THREAD)
        assertEquals(listOf(Constants.STATUS_FAIL, "发表失败 : 请检查网络连接."), listOf(event.mStatus, event.mMessage))
        assertFalse(waiting)
    }

    @Test
    fun withoutAnEditorItIsLoadedUpToThreeTimes() {
        posts.editorError = ForumException.Server("没有权限")

        val event = send(PostHelper.MODE_QUOTE_POST, info = null)

        assertEquals(3, posts.editors.size)
        assertEquals(listOf(Constants.STATUS_FAIL, "发表失败，无法获取必要信息 ！"), listOf(event.mStatus, event.mMessage))
        assertTrue(posts.sent.isEmpty())
    }

    @Test
    fun aQuickDelete() {
        posts.deletable = true
        send(PostHelper.MODE_QUICK_DELETE, info = null)
        posts.deletable = false
        val event = send(PostHelper.MODE_QUICK_DELETE, info = null)

        assertEquals(listOf(EditorKind.Edit(2, "3000001", "4000001")), posts.editors.distinct())
        assertEquals(listOf(true, false), posts.sent.map { it.second.delete })
        assertEquals(listOf("....."), listOf(event.mPostResult.content))
        assertEquals(listOf("9000011", "8000001"), posts.sent[1].second.deletedAttachments)
    }

    @Test
    fun aQuickDeleteWithoutItsEditor() {
        posts.editorError = ForumException.Server("没有权限")

        val event = send(PostHelper.MODE_QUICK_DELETE, info = null)

        assertEquals(listOf(Constants.STATUS_FAIL, "没有权限"), listOf(event.mStatus, event.mMessage))
        assertTrue(posts.sent.isEmpty())
        assertEquals(event, EventBus.getDefault().getStickyEvent(PostEvent::class.java))
    }

    @Test
    fun theThreadListHearsOfALastPage() {
        send(PostHelper.MODE_REPLY_THREAD)

        val update = EventBus.getDefault().getStickyEvent(ThreadUpdatedEvent::class.java)
        assertEquals(listOf(2, "3000002", "主题", 0), listOf(update.mFid, update.mTid, update.mTitle, update.mReplyCount))
    }

    @Test
    fun theScreenHearsOfThePostInProgressThenItsResult() {
        val sender = PostSender(posts, FakeThreadRepository(), settings, ForumErrorFormatter { true }, CoroutineScope(SupervisorJob()))

        val recorder = Recorder()
        EventBus.getDefault().register(recorder)

        sender.send("session-1", PostHelper.MODE_REPLY_THREAD, editor(), post(), true)
        assertEquals(emptyList<String>(), recorder.heard)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("session-1 0 true ${Constants.STATUS_IN_PROGRESS}"), recorder.heard)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals("session-1 0 true ${Constants.STATUS_SUCCESS}", recorder.heard.last())
        assertEquals(2, recorder.heard.size)
        EventBus.getDefault().unregister(recorder)
    }

    class Recorder {
        val heard = mutableListOf<String>()

        @Subscribe
        fun onEvent(event: PostEvent) {
            heard += "${event.mSessionId} ${event.mMode} ${event.fromQuickReply} ${event.mStatus}"
        }
    }

    private class FakePostRepository : PostRepository {
        val sent = mutableListOf<Pair<PostTarget, PostForm>>()
        val editors = mutableListOf<EditorKind>()
        var answer: PostResult = PostResult.Posted("3000002", "<html></html>")
        var error: Exception? = null
        var editorError: Exception? = null
        var deletable = true

        override suspend fun editor(kind: EditorKind): EditorPage {
            editors += kind
            editorError?.let { throw it }
            return EditorPage("a1b2c3d4", "内容 1", "100001", "h", null, deletable, null, null, null, null, listOf("9000011"), listOf("8000001"), emptyMap(), null)
        }

        override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray) = ImageUpload.Uploaded("1")

        override suspend fun submit(target: PostTarget, form: PostForm): PostResult {
            sent += target to form
            error?.let { throw it }
            return answer
        }
    }

    private class FakeThreadRepository : ThreadRepository {
        private fun page(): ThreadPage {
            val post = Post("1001", 1, "发表于 2026-11-28 10:00", "200001", "user_1", false, null, null, null, emptyList())
            return ThreadPage("3000002", 2, "主题", 1, 1, listOf(post), null, PageHeader(null, null, 0))
        }
        override suspend fun page(tid: String, page: Int, authorId: String?) = page()
        override suspend fun lastPage(tid: String) = page()
        override suspend fun pageOfPost(pid: String, tid: String?) = page()
        override suspend fun authorId(tid: String) = "200001"
        override suspend fun read(html: String, tid: String?) = page()
    }
}
