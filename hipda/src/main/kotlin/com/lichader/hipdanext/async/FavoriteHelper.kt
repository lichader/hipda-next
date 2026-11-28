package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.favorites.FavoritesRepository
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.UIUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favorites and attention for the Java screens (rule 7), with the old helper's methods and
 * messages; the results are toasts, as before.
 */
@Singleton
class FavoriteHelper @Inject constructor(
    private val repository: FavoritesRepository,
    private val errors: ForumErrorFormatter,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun isInFavorite(tid: String?): Boolean = repository.isSaved(SavedThreads.FAVORITES, tid)

    fun isInAttention(tid: String?): Boolean = repository.isSaved(SavedThreads.ATTENTION, tid)

    /** Adds [tid] to the list named [item] ([TYPE_FAVORITE] or [TYPE_ATTENTION]). */
    fun addFavorite(item: String?, tid: String?) = run(item, tid, "添加失败 : ") { list, id -> repository.add(list, id) }

    fun removeFavorite(item: String?, tid: String?) = run(item, tid, "移除失败 : ") { list, id -> repository.remove(list, id) }

    /** Removes [tid] through the list page's form, from the list screen. */
    fun deleteFavorite(item: String?, tid: String?) = run(item, tid, "移除失败 : ") { list, id ->
        repository.delete(list, id)
        "已取消" + if (list == SavedThreads.FAVORITES) "收藏" else "关注"
    }

    private fun run(item: String?, tid: String?, failure: String, action: suspend (SavedThreads, String) -> String) {
        val list = SavedThreads.values().firstOrNull { it.item == item }
        if (list == null || tid.isNullOrEmpty()) {
            UIUtils.toast("参数错误")
            return
        }
        scope.launch {
            val message = try {
                action(list, tid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure + errors.format(e)
            }
            UIUtils.toast(message)
        }
    }

    companion object {
        const val TYPE_FAVORITE = "favorites"
        const val TYPE_ATTENTION = "attention"
    }
}
