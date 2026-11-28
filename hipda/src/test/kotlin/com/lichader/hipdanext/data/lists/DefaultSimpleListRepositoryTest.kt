package com.lichader.hipdanext.data.lists

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.bean.SearchBean
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.lists.ListPage
import com.lichader.hipdanext.forum.lists.ListsApi
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.forum.lists.SearchQuery
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.ui.SimpleListTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DefaultSimpleListRepositoryTest {

    private val entry = SingletonEntryPoint.get()
    private val notiHelper = entry.notiHelper()
    private val favorites = entry.favoritesCache()
    private val account = FakeAccount()
    private val api = FakeListsApi()
    private val repository = DefaultSimpleListRepository(
        ApplicationProvider.getApplicationContext(), api, account, notiHelper, favorites, entry.historyDao(),
    )

    @Before
    fun setUp() {
        notiHelper.holdFetchNotify()
        favorites.clear()
    }

    @Test
    fun savedThreadsFillTheFavoritesCache() = runBlocking {
        api.items = listOf(ListItem(tid = "11"), ListItem(tid = "12"))

        repository.page(SimpleListTypes.TYPE_FAVORITES, 1, null)
        assertTrue(favorites.contains(SavedThreads.FAVORITES, "11"))
        assertFalse(favorites.contains(SavedThreads.ATTENTION, "11"))

        repository.page(SimpleListTypes.TYPE_ATTENTION, 1, null)
        assertTrue(favorites.contains(SavedThreads.ATTENTION, "12"))
        assertEquals(listOf(SavedThreads.FAVORITES, SavedThreads.ATTENTION), api.savedLists)
    }

    @Test
    fun readingMessagesAndNoticesClearsTheirCounts() = runBlocking {
        api.items = listOf(ListItem(uid = "200001"))
        notiHelper.currentNotification.setSmsCount(2)
        notiHelper.currentNotification.setThreadCount(3)

        repository.page(SimpleListTypes.TYPE_SMS, 1, null)
        assertEquals(0, notiHelper.currentNotification.smsCount)
        assertEquals(3, notiHelper.currentNotification.threadCount)

        repository.page(SimpleListTypes.TYPE_THREAD_NOTIFY, 1, null)
        assertEquals(0, notiHelper.currentNotification.threadCount)
    }

    @Test
    fun newPostsPassTheirSearchOn() = runBlocking {
        api.items = listOf(ListItem(tid = "1"))
        api.searchId = "257"

        val result = repository.page(SimpleListTypes.TYPE_NEW_POSTS, 2, "257")

        assertEquals("257" to 2, api.lastNewPosts)
        assertEquals("257", result.searchId)
    }

    @Test
    fun aPageFetchedAcrossAnAccountChangeIsDropped() {
        api.items = listOf(ListItem(tid = "11"))
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.page(SimpleListTypes.TYPE_FAVORITES, 1, null) } }
        assertFalse(favorites.contains(SavedThreads.FAVORITES, "11"))
    }

    @Test
    fun historyComesFromTheDevice() = runBlocking {
        entry.historyDao().saveHistoryInBackground("31", "2", "主题 1", "200001", "user_1", "2026-11-28")
        shadowOf(Looper.getMainLooper()).idle()

        val item = repository.page(SimpleListTypes.TYPE_HISTORIES, 1, null).items.first { it.tid == "31" }

        assertEquals(
            ListItem(tid = "31", uid = "200001", title = "主题 1", author = "user_1", time = "2026-11-28", forum = "Discovery",
                avatar = Avatar.OfUser("200001")),
            item,
        )
        assertEquals(emptyList<SavedThreads>(), api.savedLists)
    }

    @Test
    fun searchesPassTheirTermsAndUserThreadsTheirUid() = runBlocking {
        val search = SearchBean().apply { query = "Android"; author = "user_1"; forum = "6"; isFulltext = true; uid = "200001" }

        repository.page(SimpleListTypes.TYPE_SEARCH, 2, null, search)
        repository.page(SimpleListTypes.TYPE_SEARCH_USER_THREADS, 2, "274", search)

        assertEquals(listOf(SearchQuery("Android", "user_1", "6", fullText = true)), api.searches)
        assertEquals(listOf(Triple("200001", "274", 2)), api.userThreads)
    }

    private class FakeListsApi : ListsApi {
        var items = emptyList<ListItem>()
        var searchId: String? = null
        var duringRequest: () -> Unit = {}
        val savedLists = mutableListOf<SavedThreads>()
        var lastNewPosts: Pair<String?, Int>? = null

        private fun page(): ListPage {
            duringRequest()
            return ListPage(items, 1, searchId, PageHeader("100001", "test_user", 0))
        }

        override suspend fun myReplies(page: Int) = page()
        override suspend fun myThreads(page: Int) = page()
        override suspend fun messages() = page()
        override suspend fun notices() = page()
        override suspend fun newPosts(searchId: String?, page: Int) = page().also { lastNewPosts = searchId to page }
        override suspend fun savedThreads(list: SavedThreads, page: Int) = page().also { savedLists += list }
        override suspend fun search(query: SearchQuery, page: Int) = page().also { searches += query }
        override suspend fun userThreads(uid: String, searchId: String?, page: Int) = page().also { userThreads += Triple(uid, searchId, page) }
        val searches = mutableListOf<SearchQuery>()
        val userThreads = mutableListOf<Triple<String, String?, Int>>()
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
