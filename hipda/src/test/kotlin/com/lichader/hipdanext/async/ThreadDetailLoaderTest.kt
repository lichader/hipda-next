package com.lichader.hipdanext.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.ThreadPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadDetailLoaderTest {

    private val repository = FakeThreadRepository()
    private val loader = ThreadDetailLoader(repository, SingletonEntryPoint.get().settings())

    @Test
    fun thePageAfterAPostIsRead() {
        val data = loader.read("<html></html>", "1")!!

        assertEquals(listOf("1", 1), listOf(data.tid, data.count))
    }

    @Test
    fun anUnreadablePageGivesNothing() {
        repository.error = ForumException.Parse("no posts")

        assertNull(loader.read("<html></html>", "1"))
    }

    private class FakeThreadRepository : ThreadRepository {
        var error: Exception? = null
        override suspend fun page(tid: String, page: Int, authorId: String?) = throw UnsupportedOperationException()
        override suspend fun lastPage(tid: String) = throw UnsupportedOperationException()
        override suspend fun pageOfPost(pid: String, tid: String?) = throw UnsupportedOperationException()
        override suspend fun authorId(tid: String) = throw UnsupportedOperationException()
        override suspend fun read(html: String, tid: String?): ThreadPage {
            error?.let { throw it }
            val post = Post("1001", 1, "发表于 2026-11-28 10:00", "200001", "user_1", false, null, null, null, emptyList())
            return ThreadPage("1", 2, "主题", 1, 1, listOf(post), null, PageHeader(null, null, 0))
        }
    }
}
