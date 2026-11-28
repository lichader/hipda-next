package com.lichader.hipdanext.data.blacklist

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.blacklist.BlacklistApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** TD-22: a result fetched for one account must not be stored for another. */
@RunWith(AndroidJUnit4::class)
class DefaultBlacklistRepositoryTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val account = FakeAccount()
    private val api = FakeBlacklistApi()
    private val repository = DefaultBlacklistRepository(api, settings, account)

    @Before
    fun setUp() = settings.setBlacklists(arrayListOf("old_entry"))

    @Test
    fun syncStoresTheForumsList() = runBlocking {
        assertEquals(listOf("user_1", "user_2"), repository.sync())
        assertEquals(listOf("user_1", "user_2"), settings.blacklists)
    }

    @Test
    fun aListFetchedBeforeAnAccountChangeIsNotStored() {
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.sync() } }
        assertEquals(listOf("old_entry"), settings.blacklists)
    }

    @Test
    fun anAddThatSpansAnAccountChangeLeavesTheDevicesCopyAlone() {
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.add("user_3") } }
        assertFalse(settings.isInBlacklist("user_3"))
    }

    @Test
    fun addAndRemoveUpdateTheDevicesCopy() = runBlocking {
        repository.add("user_3")
        assertTrue(settings.isInBlacklist("user_3"))
        repository.remove("user_3")
        assertFalse(settings.isInBlacklist("user_3"))
    }

    private class FakeBlacklistApi : BlacklistApi {
        var duringRequest: () -> Unit = {}
        override suspend fun fetch(): List<String> = listOf("user_1", "user_2").also { duringRequest() }
        override suspend fun add(username: String) = duringRequest()
        override suspend fun remove(username: String) = duringRequest()
    }

    private class FakeAccount : AccountRepository {
        override val generation = MutableStateFlow(0)
        override fun isLoggedIn() = true
        override fun hasStoredCredentials() = true
        override suspend fun login(manual: Boolean) = LoginOutcome.Success
        override fun logout() {}
    }
}
