package com.lichader.hipdanext.data.lists

import android.content.Context
import android.text.TextUtils
import com.lichader.hipdanext.bean.SearchBean
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.favorites.FavoritesCache
import com.lichader.hipdanext.db.HistoryDao
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.lists.ListsApi
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.forum.lists.SearchQuery
import com.lichader.hipdanext.ui.SimpleListTypes
import com.lichader.hipdanext.service.NotiHelper
import com.lichader.hipdanext.utils.HiUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SimpleListResult(
    val items: List<ListItem>,
    val maxPage: Int,
    /** New posts' search, for the following pages. */
    val searchId: String?,
)

/**
 * The simple lists: the account's posts, replies, messages, notices, new posts, saved threads,
 * and the reading history kept on the device. Main-safe; throws [ForumException] if a request
 * fails, or [AccountChangedException] if the account changed meanwhile.
 */
interface SimpleListRepository {

    /**
     * Page [page] of the list of [type], one of [SimpleListTypes].
     *
     * @param searchId the search of page 1's result, for new posts and a user's threads; null to start one
     * @param search the search's terms for `TYPE_SEARCH`, or the user's uid for `TYPE_SEARCH_USER_THREADS`
     */
    suspend fun page(type: Int, page: Int, searchId: String?, search: SearchBean? = null): SimpleListResult
}

/** Does what the old `SimpleListJob`, `HiParser` and `SimpleListFragment` did around parsing. */
@Singleton
class DefaultSimpleListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ListsApi,
    private val account: AccountRepository,
    private val notiHelper: NotiHelper,
    private val favoritesCache: FavoritesCache,
    private val historyDao: HistoryDao,
) : SimpleListRepository {

    override suspend fun page(type: Int, page: Int, searchId: String?, search: SearchBean?): SimpleListResult {
        if (type == SimpleListTypes.TYPE_HISTORIES) return histories()

        val generation = account.generation.value
        val result = when (type) {
            SimpleListTypes.TYPE_MYREPLY -> api.myReplies(page)
            SimpleListTypes.TYPE_MYPOST -> api.myThreads(page)
            SimpleListTypes.TYPE_SMS -> api.messages()
            SimpleListTypes.TYPE_THREAD_NOTIFY -> api.notices()
            SimpleListTypes.TYPE_NEW_POSTS -> api.newPosts(searchId, page)
            SimpleListTypes.TYPE_FAVORITES -> api.savedThreads(SavedThreads.FAVORITES, page)
            SimpleListTypes.TYPE_ATTENTION -> api.savedThreads(SavedThreads.ATTENTION, page)
            SimpleListTypes.TYPE_SEARCH -> requireNotNull(search) { "A search needs its terms" }.let {
                api.search(SearchQuery(it.query, it.author, it.forum, it.isFulltext), page)
            }
            SimpleListTypes.TYPE_SEARCH_USER_THREADS ->
                api.userThreads(requireNotNull(search) { "A user's threads need the uid" }.uid, searchId, page)
            else -> throw IllegalArgumentException("Not a simple list type: $type")
        }
        if (account.generation.value != generation) throw AccountChangedException()

        notiHelper.fetchNotificationAsync(context, result.header.noticeCount)
        if (result.items.isNotEmpty()) {
            when (type) {
                SimpleListTypes.TYPE_FAVORITES -> favoritesCache.addAll(SavedThreads.FAVORITES, tids(result.items))
                SimpleListTypes.TYPE_ATTENTION -> favoritesCache.addAll(SavedThreads.ATTENTION, tids(result.items))
                SimpleListTypes.TYPE_SMS -> notiHelper.currentNotification.clearSmsCount()
                SimpleListTypes.TYPE_THREAD_NOTIFY -> notiHelper.currentNotification.setThreadCount(0)
            }
        }
        return SimpleListResult(result.items, result.maxPage, result.searchId)
    }

    private fun tids(items: List<ListItem>): Set<String> = items.mapNotNullTo(HashSet()) { it.tid }

    private suspend fun histories(): SimpleListResult = withContext(Dispatchers.IO) {
        val items = historyDao.histories.map { history ->
            val fid = history.fid
            ListItem(
                tid = history.tid,
                uid = history.uid,
                title = history.title,
                author = history.username,
                time = history.postTime,
                forum = if (!TextUtils.isEmpty(fid) && TextUtils.isDigitsOnly(fid)) HiUtils.getForumNameByFid(fid.toInt()) else "",
                avatar = history.uid?.let { Avatar.OfUser(it) },
            )
        }
        SimpleListResult(items, maxPage = 1, searchId = null)
    }
}
