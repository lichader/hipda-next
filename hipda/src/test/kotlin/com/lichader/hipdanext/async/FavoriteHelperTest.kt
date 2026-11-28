package com.lichader.hipdanext.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.favorites.FavoritesRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.ui.ForumErrorFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
class FavoriteHelperTest {

    private val repository = FakeFavoritesRepository()
    private val helper = FavoriteHelper(repository, ForumErrorFormatter { true }, CoroutineScope(Dispatchers.Unconfined))

    private fun toast(): String = ShadowToast.getTextOfLatestToast().toString()

    @Test
    fun theForumsMessageOrTheOldOnes() {
        helper.addFavorite(FavoriteHelper.TYPE_FAVORITE, "11")
        assertEquals("此主题已成功添加到收藏夹中", toast())

        helper.deleteFavorite(FavoriteHelper.TYPE_ATTENTION, "11")
        assertEquals("已取消关注", toast())

        helper.deleteFavorite(FavoriteHelper.TYPE_FAVORITE, "11")
        assertEquals("已取消收藏", toast())

        assertEquals(listOf("add favorites 11", "delete attention 11", "delete favorites 11"), repository.calls)
    }

    @Test
    fun failuresAndBadArguments() {
        repository.error = ForumException.Network(UnknownHostException())
        helper.addFavorite(FavoriteHelper.TYPE_FAVORITE, "11")
        assertEquals("添加失败 : 请检查网络连接.", toast())
        helper.removeFavorite(FavoriteHelper.TYPE_ATTENTION, "11")
        assertEquals("移除失败 : 请检查网络连接.", toast())

        helper.addFavorite("unknown", "11")
        assertEquals("参数错误", toast())
        helper.removeFavorite(FavoriteHelper.TYPE_FAVORITE, "")
        assertEquals("参数错误", toast())
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        var error: Exception? = null
        val calls = mutableListOf<String>()
        override fun isSaved(list: SavedThreads, tid: String?) = false
        override suspend fun add(list: SavedThreads, tid: String): String {
            error?.let { throw it }
            calls += "add ${list.item} $tid"
            return "此主题已成功添加到收藏夹中"
        }
        override suspend fun remove(list: SavedThreads, tid: String): String {
            error?.let { throw it }
            calls += "remove ${list.item} $tid"
            return "此主题已成功从您的收藏夹中移除。"
        }
        override suspend fun delete(list: SavedThreads, tid: String) {
            error?.let { throw it }
            calls += "delete ${list.item} $tid"
        }
    }
}
