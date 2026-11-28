package com.lichader.hipdanext.data.lists

import android.content.Context
import android.text.TextUtils
import com.lichader.hipdanext.async.FavoriteHelper
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.db.HistoryDao
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.lists.ListsApi
import com.lichader.hipdanext.forum.lists.SavedThreads
import com.lichader.hipdanext.job.SimpleListJob
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
    /** The page's form token, for removing a favorite. */
    val formhash: String?,
)

/**
 * The simple lists: the account's posts, replies, messages, notices, new posts, saved threads,
 * and the reading history kept on the device. Main-safe; throws [ForumException] if a request
 * fails, or [AccountChangedException] if the account changed meanwhile.
 */
interface SimpleListRepository {

    /**
     * Page [page] of the list of [type], one of `SimpleListJob`'s types.
     *
     * @param searchId new posts' search from page 1's result, or null to start one
     */
    suspend fun page(type: Int, page: Int, searchId: String?): SimpleListResult
}

/** Does what the old `SimpleListJob`, `HiParser` and `SimpleListFragment` did around parsing. */
@Singleton
class DefaultSimpleListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ListsApi,
    private val account: AccountRepository,
    private val notiHelper: NotiHelper,
    private val favoriteHelper: FavoriteHelper,
    private val historyDao: HistoryDao,
) : SimpleListRepository {

    override suspend fun page(type: Int, page: Int, searchId: String?): SimpleListResult {
        if (type == SimpleListJob.TYPE_HISTORIES) return histories()

        val generation = account.generation.value
        val result = when (type) {
            SimpleListJob.TYPE_MYREPLY -> api.myReplies(page)
            SimpleListJob.TYPE_MYPOST -> api.myThreads(page)
            SimpleListJob.TYPE_SMS -> api.messages()
            SimpleListJob.TYPE_THREAD_NOTIFY -> api.notices()
            SimpleListJob.TYPE_NEW_POSTS -> api.newPosts(searchId, page)
            SimpleListJob.TYPE_FAVORITES -> api.savedThreads(SavedThreads.FAVORITES, page)
            SimpleListJob.TYPE_ATTENTION -> api.savedThreads(SavedThreads.ATTENTION, page)
            else -> throw IllegalArgumentException("Not a simple list type: $type")
        }
        if (account.generation.value != generation) throw AccountChangedException()

        notiHelper.fetchNotificationAsync(context, result.header.noticeCount)
        if (result.items.isNotEmpty()) {
            when (type) {
                SimpleListJob.TYPE_FAVORITES -> favoriteHelper.addToCahce(FavoriteHelper.TYPE_FAVORITE, tids(result.items))
                SimpleListJob.TYPE_ATTENTION -> favoriteHelper.addToCahce(FavoriteHelper.TYPE_ATTENTION, tids(result.items))
                SimpleListJob.TYPE_SMS -> notiHelper.currentNotification.clearSmsCount()
                SimpleListJob.TYPE_THREAD_NOTIFY -> notiHelper.currentNotification.setThreadCount(0)
            }
        }
        return SimpleListResult(result.items, result.maxPage, result.searchId, result.formhash)
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
        SimpleListResult(items, maxPage = 1, searchId = null, formhash = null)
    }
}
