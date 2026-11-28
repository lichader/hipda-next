package com.lichader.hipdanext.data.favorites

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.forum.favorites.FavoritesApi
import com.lichader.hipdanext.forum.lists.SavedThreads
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
class DefaultFavoritesRepositoryTest {

    private val cache = FavoritesCache(ApplicationProvider.getApplicationContext())
    private val account = FakeAccount()
    private val api = FakeFavoritesApi()
    private val repository = DefaultFavoritesRepository(api, cache, account)

    @Before
    fun setUp() = cache.clear()

    @Test
    fun addingRemembersTheThreadWhateverTheForumSaid() = runBlocking {
        api.message = "该主题已在收藏夹中"

        assertEquals("该主题已在收藏夹中", repository.add(SavedThreads.FAVORITES, "11"))
        assertTrue(repository.isSaved(SavedThreads.FAVORITES, "11"))
        assertFalse(repository.isSaved(SavedThreads.ATTENTION, "11"))
    }

    @Test
    fun removingAndDeletingForgetIt() = runBlocking {
        cache.add(SavedThreads.ATTENTION, "11")
        cache.add(SavedThreads.ATTENTION, "12")

        repository.remove(SavedThreads.ATTENTION, "11")
        repository.delete(SavedThreads.ATTENTION, "12")

        assertFalse(repository.isSaved(SavedThreads.ATTENTION, "11"))
        assertFalse(repository.isSaved(SavedThreads.ATTENTION, "12"))
        assertEquals(listOf("remove attention 11", "delete attention 12"), api.calls)
    }

    @Test
    fun aChangeAcrossAnAccountSwitchLeavesTheCopyAlone() {
        api.duringRequest = { account.generation.value++ }

        assertThrows(AccountChangedException::class.java) { runBlocking { repository.add(SavedThreads.FAVORITES, "11") } }
        assertFalse(repository.isSaved(SavedThreads.FAVORITES, "11"))
    }

    private class FakeFavoritesApi : FavoritesApi {
        var message = "此主题已成功添加到收藏夹中"
        var duringRequest: () -> Unit = {}
        val calls = mutableListOf<String>()
        override suspend fun add(list: SavedThreads, tid: String) = message.also { duringRequest(); calls += "add ${list.item} $tid" }
        override suspend fun remove(list: SavedThreads, tid: String) = message.also { duringRequest(); calls += "remove ${list.item} $tid" }
        override suspend fun delete(list: SavedThreads, tid: String) {
            duringRequest()
            calls += "delete ${list.item} $tid"
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
