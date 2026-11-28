package com.lichader.hipdanext.data.favorites

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.forum.lists.SavedThreads
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesCacheTest {

    private fun newCache() = FavoritesCache(ApplicationProvider.getApplicationContext())

    @Test
    fun theListsAreKeptApartAndSurviveARestart() {
        val cache = newCache()
        cache.add(SavedThreads.FAVORITES, "11")
        cache.addAll(SavedThreads.ATTENTION, listOf("12", "13"))

        val restarted = newCache()
        assertTrue(restarted.contains(SavedThreads.FAVORITES, "11"))
        assertFalse(restarted.contains(SavedThreads.ATTENTION, "11"))
        assertTrue(restarted.contains(SavedThreads.ATTENTION, "13"))

        restarted.remove(SavedThreads.ATTENTION, "13")
        assertFalse(newCache().contains(SavedThreads.ATTENTION, "13"))
    }

    @Test
    fun onlyValidThreadIdsAreAddedOneByOne() {
        val cache = newCache()
        cache.add(SavedThreads.FAVORITES, "abc")

        assertFalse(cache.contains(SavedThreads.FAVORITES, "abc"))
    }

    @Test
    fun clearForgetsTheCopyInMemoryToo() {
        val cache = newCache()
        cache.add(SavedThreads.FAVORITES, "11")

        cache.clear()

        assertFalse(cache.contains(SavedThreads.FAVORITES, "11"))
        assertFalse(newCache().contains(SavedThreads.FAVORITES, "11"))
    }
}
