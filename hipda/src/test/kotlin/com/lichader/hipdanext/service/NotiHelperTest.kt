package com.lichader.hipdanext.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.lists.ListPage
import com.lichader.hipdanext.forum.lists.ListsApi
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.forum.lists.SearchQuery
import com.lichader.hipdanext.forum.parser.PageHeader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The background check of new messages and notices (W11). */
@RunWith(AndroidJUnit4::class)
class NotiHelperTest {

    private val api = FakeListsApi()
    private val noti = NotiHelper(ApplicationProvider.getApplicationContext(), SingletonEntryPoint.get().settings(), NewMessagesCheck(api))

    @Test
    fun aSingleNewMessageIsNamed() {
        api.items = listOf(ListItem(uid = "200001", author = "user_1", title = "消息 1"))
        api.noticeCount = 2

        noti.fetchNotification(null)

        val bean = noti.currentNotification
        assertEquals(1, bean.smsCount)
        assertEquals(2, bean.threadCount)
        assertEquals(listOf("user_1", "200001", "消息 1"), listOf(bean.username, bean.uid, bean.content))
    }

    @Test
    fun severalNewMessagesAreOnlyCounted() {
        api.items = listOf(ListItem(uid = "200001"), ListItem(uid = "200002"))

        noti.fetchNotification(null)

        assertEquals(2, noti.currentNotification.smsCount)
        assertEquals("", noti.currentNotification.username)
    }

    @Test
    fun aPageWithoutNewMessagesClearsTheCount() {
        noti.currentNotification.smsCount = 3
        api.items = emptyList()

        noti.fetchNotification(null)

        assertEquals(0, noti.currentNotification.smsCount)
    }

    @Test
    fun aFailedCheckKeepsTheCounts() {
        noti.currentNotification.smsCount = 3
        noti.currentNotification.threadCount = 4
        api.error = RuntimeException("network")

        noti.fetchNotification(null)

        assertEquals(3, noti.currentNotification.smsCount)
        assertEquals(4, noti.currentNotification.threadCount)
    }

    private class FakeListsApi : ListsApi {
        var items = emptyList<ListItem>()
        var noticeCount = 0
        var error: Exception? = null
        override suspend fun newMessages(): ListPage {
            error?.let { throw it }
            return ListPage(items, 1, null, PageHeader("100001", "test_user", noticeCount))
        }
        override suspend fun myReplies(page: Int) = error("unused")
        override suspend fun myThreads(page: Int) = error("unused")
        override suspend fun messages() = error("unused")
        override suspend fun notices() = error("unused")
        override suspend fun newPosts(searchId: String?, page: Int) = error("unused")
        override suspend fun savedThreads(list: SavedThreads, page: Int) = error("unused")
        override suspend fun search(query: SearchQuery, page: Int) = error("unused")
        override suspend fun userThreads(uid: String, searchId: String?, page: Int) = error("unused")
    }
}
