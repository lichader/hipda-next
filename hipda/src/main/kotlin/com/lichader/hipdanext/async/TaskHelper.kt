package com.lichader.hipdanext.async

import android.app.Activity
import androidx.preference.Preference
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.db.ContentDao
import com.lichader.hipdanext.db.HistoryDao
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.forum.site.SiteApi
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.ui.widget.HiProgressDialog
import com.lichader.hipdanext.utils.HiUtils
import com.lichader.hipdanext.utils.Logger
import com.lichader.hipdanext.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The app's background chores: the daily cleanup and blacklist sync, and refreshing the image host. */
@Singleton
class TaskHelper @Inject constructor(
    private val settings: HiSettingsHelper,
    private val loginHelper: LoginHelper,
    private val blacklist: BlacklistRepository,
    private val siteApi: SiteApi,
    private val errors: ForumErrorFormatter,
    private val contentDao: ContentDao,
    private val historyDao: HistoryDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** Once a day, or now with [force]: cleans the local caches and syncs the blacklist when logged in. */
    fun runDailyTask(force: Boolean) {
        val last = settings.getStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, "0").toLongOrNull()
        if (force || last == null || System.currentTimeMillis() > last + DAY_MILLIS) {
            scope.launch(Dispatchers.IO) {
                contentDao.cleanup()
                historyDao.cleanup()
                Utils.cleanPictures()
                Utils.cleanTempUploadFiles()
            }
            settings.setStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, System.currentTimeMillis().toString())
        }
        val synced = settings.blacklistSyncTime
        if (loginHelper.isLoggedIn() && (force || synced == null || System.currentTimeMillis() > synced.time + DAY_MILLIS)) {
            scope.launch {
                try {
                    blacklist.sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // As before, a failed background sync is only logged.
                    Logger.e(e)
                }
            }
        }
    }

    fun updateImageHost() = updateImageHost(null, null)

    /**
     * Resets the forum server and reads the image host from the site, at most once an hour unless
     * [activity] asks for it, which then shows the progress and result; [preference] shows the server.
     */
    fun updateImageHost(activity: Activity?, preference: Preference?) {
        val imageHost = settings.getStringValue(HiSettingsHelper.PERF_IMAGE_HOST, "")
        val updatedAt = settings.getLongValue(HiSettingsHelper.PERF_IMAGE_HOST_UPDATE_TIME, 0)
        if (activity == null && !imageHost.isNullOrEmpty() && imageHost.contains("://") &&
            System.currentTimeMillis() - updatedAt <= HOUR_MILLIS
        ) return

        val dialog = activity?.let { HiProgressDialog.show(it, "正在更新...") }
        scope.launch {
            try {
                settings.setForumServer(HiUtils.ForumServer)
                settings.setImageHost(siteApi.imageHost())
                HiUtils.updateBaseUrls(settings)
                settings.setLongValue(HiSettingsHelper.PERF_IMAGE_HOST_UPDATE_TIME, System.currentTimeMillis())
                dialog?.dismiss("服务器已更新 \n\n论坛:${settings.forumServer}\n图片:${settings.imageHost}", 2000)
                preference?.summary = settings.forumServer
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog?.dismissError("发生错误 : " + errors.format(e))
            }
        }
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
