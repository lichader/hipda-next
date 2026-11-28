package com.lichader.hipdanext.data.threads

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.threads.ThreadListApi
import com.lichader.hipdanext.forum.threads.ThreadListPage
import com.lichader.hipdanext.forum.threads.ThreadSummary
import com.lichader.hipdanext.utils.HiUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultThreadListRepositoryTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val notiHelper = SingletonEntryPoint.get().notiHelper()
    private val account = FakeAccount()
    private val api = FakeThreadListApi()
    private val repository = DefaultThreadListRepository(ApplicationProvider.getApplicationContext(), api, settings, account, notiHelper)

    @Before
    fun setUp() {
        notiHelper.holdFetchNotify()
        settings.setUid("100001")
        settings.setUsername("test_user")
        settings.setBlacklists(emptyList())
        settings.setShowStickThreads(true)
    }

    @Test
    fun blacklistedAuthorsAndHiddenStickyThreadsAreLeftOut() = runBlocking {
        api.threads = listOf(summary("1", "user_1", sticky = true), summary("2", "user_2"), summary("3", "user_3"))
        settings.setBlacklists(listOf("user_2"))
        settings.setShowStickThreads(false)

        assertEquals(listOf("3"), repository.page(2, 1).threads.map { it.tid })
    }

    @Test
    fun emojiShortcodesInTitlesAreConverted() = runBlocking {
        api.threads = listOf(summary("1", "user_1", title = "hi :smile:"))

        assertEquals("hi 😄", repository.page(2, 1).threads.single().title)
    }

    @Test
    fun theUidIsLearnedOnlyWhileUnknown() = runBlocking {
        api.header = PageHeader("100001", "Test_User", 0)
        assertFalse(repository.page(2, 1).learnedUid)
        assertEquals("test_user", settings.username)

        settings.setUid("")
        assertTrue(repository.page(2, 1).learnedUid)
        assertEquals("100001", settings.uid)
        assertEquals("Test_User", settings.username)
    }

    @Test
    fun aDifferentlyNamedAccountKeepsTheStoredUsername() = runBlocking {
        settings.setUid("")
        api.header = PageHeader("100002", "other_user", 0)

        assertTrue(repository.page(2, 1).learnedUid)
        assertEquals("test_user", settings.username)
    }

    @Test
    fun aPageFetchedAcrossAnAccountChangeIsDropped() {
        settings.setUid("")
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.page(2, 1) } }
        assertEquals("", settings.uid)
    }

    @Test
    fun onlyTheBuyAndSellForumUsesTheTypeFilter() = runBlocking {
        settings.setBSTypeId("3")
        repository.page(HiUtils.FID_BS, 1)
        assertEquals("3", api.lastTypeId)

        repository.page(2, 1)
        assertEquals(null, api.lastTypeId)
    }

    private fun summary(tid: String, author: String, sticky: Boolean = false, title: String = "T") = ThreadSummary(
        tid = tid, title = title, titleColor = null, type = null, isNew = false, sticky = sticky, poll = false,
        author = author, authorId = "200001", timeCreate = "2026-1-1", timeUpdate = null, replyCount = "0",
        viewCount = "0", lastPost = author, withPictures = false, maxPage = 1,
    )

    private class FakeThreadListApi : ThreadListApi {
        var threads = emptyList<ThreadSummary>()
        var header = PageHeader(null, null, 0)
        var duringRequest: () -> Unit = {}
        var lastTypeId: String? = null
        override suspend fun page(forumId: Int, page: Int, typeId: String?, orderByPostTime: Boolean): ThreadListPage {
            lastTypeId = typeId
            duringRequest()
            return ThreadListPage(threads, header)
        }
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
