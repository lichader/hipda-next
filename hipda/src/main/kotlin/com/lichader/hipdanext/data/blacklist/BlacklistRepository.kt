package com.lichader.hipdanext.data.blacklist

import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.blacklist.BlacklistApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's blacklist: the forum's copy, plus the copy kept on the device that the thread
 * and post lists filter by. Every change made through here updates both copies.
 *
 * All functions are main-safe, and throw [ForumException] if the forum request fails.
 * They must be called on the main thread, because [HiSettingsHelper] is not thread-safe.
 */
interface BlacklistRepository {

    /** Fetches the forum's blacklist, stores it on the device and returns it. */
    suspend fun sync(): List<String>

    suspend fun add(username: String)

    suspend fun remove(username: String)
}

@Singleton
class DefaultBlacklistRepository @Inject constructor(
    private val api: BlacklistApi,
    private val settings: HiSettingsHelper,
) : BlacklistRepository {

    override suspend fun sync(): List<String> {
        val usernames = api.fetch()
        // A copy, because HiSettingsHelper keeps the list it is given and changes it later.
        settings.setBlacklists(ArrayList(usernames))
        settings.setBlacklistSyncTime()
        return usernames
    }

    override suspend fun add(username: String) {
        api.add(username)
        settings.addToBlacklist(username)
    }

    override suspend fun remove(username: String) {
        api.remove(username)
        settings.removeFromBlacklist(username)
    }
}
