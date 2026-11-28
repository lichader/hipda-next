package com.lichader.hipdanext.data.favorites

import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.favorites.FavoritesApi
import com.lichader.hipdanext.forum.lists.SavedThreads
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adding threads to and removing them from favorites and attention, and the device's copy of
 * those lists. Main-safe; throws [ForumException] if a request fails, or [AccountChangedException]
 * if the account changed meanwhile, without touching the copy.
 */
interface FavoritesRepository {

    fun isSaved(list: SavedThreads, tid: String?): Boolean

    /** Returns the forum's message. As before, the thread is remembered whatever the forum said. */
    suspend fun add(list: SavedThreads, tid: String): String

    /** Returns the forum's message. */
    suspend fun remove(list: SavedThreads, tid: String): String

    /** Removes the thread through the list page's form, as the list screen does. */
    suspend fun delete(list: SavedThreads, tid: String)
}

@Singleton
class DefaultFavoritesRepository @Inject constructor(
    private val api: FavoritesApi,
    private val cache: FavoritesCache,
    private val account: AccountRepository,
) : FavoritesRepository {

    override fun isSaved(list: SavedThreads, tid: String?): Boolean = cache.contains(list, tid)

    override suspend fun add(list: SavedThreads, tid: String): String =
        forAccount { api.add(list, tid) }.also { cache.add(list, tid) }

    override suspend fun remove(list: SavedThreads, tid: String): String =
        forAccount { api.remove(list, tid) }.also { cache.remove(list, tid) }

    override suspend fun delete(list: SavedThreads, tid: String) {
        forAccount { api.delete(list, tid) }
        cache.remove(list, tid)
    }

    private suspend fun <T> forAccount(request: suspend () -> T): T {
        val generation = account.generation.value
        val result = request()
        if (account.generation.value != generation) throw AccountChangedException()
        return result
    }
}
