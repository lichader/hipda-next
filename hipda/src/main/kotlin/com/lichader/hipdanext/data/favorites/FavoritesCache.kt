package com.lichader.hipdanext.data.favorites

import android.content.Context
import androidx.core.content.edit
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.utils.HiUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's copy of which threads are in the account's favorites and attention, for the
 * thread screen's menu. Kept in the same preferences as before, so the cache survives the update.
 */
@Singleton
class FavoritesCache @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("FavCachePrefsFile", 0)
    private val sets = SavedThreads.values().associateWith { HashSet(prefs.getStringSet(it.item, emptySet()).orEmpty()) }

    @Synchronized
    fun contains(list: SavedThreads, tid: String?): Boolean = tid in sets.getValue(list)

    /** Remembers [tid], if it is a valid thread ID. */
    @Synchronized
    fun add(list: SavedThreads, tid: String) {
        if (HiUtils.isValidId(tid) && sets.getValue(list).add(tid)) save(list)
    }

    /** Remembers the threads a page of [list] showed. */
    @Synchronized
    fun addAll(list: SavedThreads, tids: Collection<String>) {
        sets.getValue(list).addAll(tids)
        save(list)
    }

    @Synchronized
    fun remove(list: SavedThreads, tid: String) {
        if (sets.getValue(list).remove(tid)) save(list)
    }

    /** Forgets everything, as at logout. */
    @Synchronized
    fun clear() {
        sets.values.forEach { it.clear() }
        prefs.edit { clear() }
    }

    private fun save(list: SavedThreads) {
        prefs.edit { putStringSet(list.item, HashSet(sets.getValue(list))) }
    }
}
