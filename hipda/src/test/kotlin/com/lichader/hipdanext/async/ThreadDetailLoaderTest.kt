package com.lichader.hipdanext.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
class ThreadDetailLoaderTest {

    private val repository = FakeThreadRepository()
    private val loader = ThreadDetailLoader(repository, SingletonEntryPoint.get().settings(), ForumErrorFormatter { true })

    private fun outcome(load: ThreadLoad) = Triple(load.status, load.message, load.data?.count)

    @Test
    fun theOldJobsStatusesAndMessages() {
        repository.posts = 1
        assertEquals(Triple(Constants.STATUS_SUCCESS, "", 1), outcome(loader.page("1", 1, null)))
        repository.posts = 0
        assertEquals(Triple(Constants.STATUS_FAIL_ABORT, "页面加载失败", 0), outcome(loader.lastPage("1")))
        repository.error = ForumException.Parse("no posts")
        assertEquals(Triple(Constants.STATUS_FAIL_ABORT, "页面加载失败", null), outcome(loader.pageOfPost("2", "1")))
        repository.error = ForumException.NotLoggedIn()
        assertEquals(Triple(Constants.STATUS_FAIL_RELOGIN, "请重新登录", null), outcome(loader.page("1", 1, null)))
        repository.error = ForumException.Network(UnknownHostException())
        assertEquals(Triple(Constants.STATUS_FAIL, "请检查网络连接.", null), outcome(loader.page("1", 1, null)))
    }

    @Test
    fun anUnreadableAuthorOrPageGivesNothing() {
        repository.error = ForumException.Network(UnknownHostException())

        assertEquals("", loader.authorId("1"))
        assertNull(loader.read("<html></html>", "1"))
    }

    private class FakeThreadRepository : ThreadRepository {
        var posts = 1
        var error: Exception? = null
        private fun page(): ThreadPage {
            error?.let { throw it }
            val post = Post("1001", 1, "发表于 2026-11-28 10:00", "200001", "user_1", false, null, null, null, emptyList())
            return ThreadPage("1", 2, "主题", 1, 1, List(posts) { post }, null, PageHeader(null, null, 0))
        }
        override suspend fun page(tid: String, page: Int, authorId: String?) = page()
        override suspend fun lastPage(tid: String) = page()
        override suspend fun pageOfPost(pid: String, tid: String?) = page()
        override suspend fun authorId(tid: String): String = error?.let { throw it } ?: "200001"
        override suspend fun read(html: String, tid: String?) = page()
    }
}
