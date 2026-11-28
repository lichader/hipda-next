package com.lichader.hipdanext.data.threads

import android.content.Context
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.threads.ThreadListApi
import com.lichader.hipdanext.forum.threads.ThreadSummary
import com.lichader.hipdanext.service.NotiHelper
import com.lichader.hipdanext.utils.HiUtils
import com.vdurmont.emoji.EmojiParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ThreadListResult(
    /** The page's threads after the user's filters, with emoji shortcodes in titles converted. */
    val threads: List<ThreadSummary>,
    /** True if the page told the app the account's uid for the first time. */
    val learnedUid: Boolean,
)

/**
 * Forums' thread lists. Main-safe; throws [ForumException] if the request fails, or
 * [AccountChangedException] if the account changed meanwhile.
 */
interface ThreadListRepository {

    suspend fun page(forumId: Int, page: Int): ThreadListResult
}

/** Applies what the old `HiParserThreadList` and `ThreadListJob` did around parsing. */
@Singleton
class DefaultThreadListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ThreadListApi,
    private val settings: HiSettingsHelper,
    private val account: AccountRepository,
    private val notiHelper: NotiHelper,
) : ThreadListRepository {

    override suspend fun page(forumId: Int, page: Int): ThreadListResult {
        val generation = account.generation.value
        // Like TextUtils.isDigitsOnly, an empty type ID ("all types") counts as digits.
        val typeId = settings.getBSTypeId().orEmpty().takeIf { forumId == HiUtils.FID_BS && it.all(Char::isDigit) }
        val result = api.page(forumId, page, typeId, settings.isSortByPostTime(forumId))
        if (account.generation.value != generation) throw AccountChangedException()

        notiHelper.fetchNotificationAsync(context, result.header.noticeCount)
        HiSettingsHelper.updateMobileNetworkStatus(context)
        val learnedUid = learnUid(result.header)
        val threads = result.threads
            .filterNot { (it.sticky && !settings.isShowStickThreads) || settings.isInBlacklist(it.author) }
            .map { it.copy(title = EmojiParser.parseToUnicode(it.title)) }
        return ThreadListResult(threads, learnedUid)
    }

    /** While the uid is unknown, take it from the page header, and the username's spelling with it. */
    private fun learnUid(header: PageHeader): Boolean {
        val uid = header.accountUid ?: return false
        if (!settings.uid.isNullOrEmpty()) return false
        val name = header.accountName.orEmpty()
        val stored = settings.username.orEmpty()
        if (stored != name && stored.equals(name, ignoreCase = true)) settings.setUsername(name)
        settings.setUid(uid)
        settings.saveCurrentProfile()
        return true
    }
}
