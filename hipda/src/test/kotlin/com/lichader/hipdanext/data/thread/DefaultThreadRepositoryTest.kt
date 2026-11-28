package com.lichader.hipdanext.data.thread

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.thread.ThreadApi
import com.lichader.hipdanext.forum.thread.ThreadPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultThreadRepositoryTest {

    private val entry = SingletonEntryPoint.get()
    private val settings = entry.settings()
    private val account = FakeAccount()
    private val api = FakeThreadApi()
    private val repository = DefaultThreadRepository(ApplicationProvider.getApplicationContext(), api, settings, account, entry.notiHelper())

    @Before
    fun setUp() {
        entry.notiHelper().holdFetchNotify()
        settings.setMaxPostsInPage(50)
    }

    @Test
    fun thePostsPerPageAreRememberedWhenThePageTells() = runBlocking {
        api.postsPerPage = null
        repository.page("1", 1, null)
        assertEquals(50, settings.maxPostsInPage)

        api.postsPerPage = 30
        repository.lastPage("1")
        assertEquals(30, settings.maxPostsInPage)
    }

    @Test
    fun aPageFetchedAcrossAnAccountChangeIsDropped() {
        api.postsPerPage = 30
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.pageOfPost("2", null) } }
        assertEquals(50, settings.maxPostsInPage)
    }

    private class FakeThreadApi : ThreadApi {
        var postsPerPage: Int? = null
        var duringRequest: () -> Unit = {}
        private fun page(): ThreadPage {
            duringRequest()
            return ThreadPage("1", 2, "主题", 1, 1, emptyList(), postsPerPage, PageHeader("100001", "test_user", 0))
        }
        override suspend fun page(tid: String, page: Int, authorId: String?) = page()
        override suspend fun lastPage(tid: String) = page()
        override suspend fun pageOfPost(pid: String, tid: String?) = page()
        override suspend fun authorId(tid: String) = "200001"
        override suspend fun read(html: String, tid: String?) = page()
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
