package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Logger
import com.lichader.hipdanext.utils.UIUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A bridge to [BlacklistRepository] for Java code, which cannot call `suspend` functions.
 * Each call runs in the background and reports its outcome in a toast, as before. Used by
 * `UserinfoFragment` and `TaskHelper`. Delete it once they have ViewModels.
 */
@Singleton
class BlacklistHelper @Inject constructor(
    private val repository: BlacklistRepository,
    private val errors: ForumErrorFormatter,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun addBlacklist(username: String) {
        launchWithToast("已经将用户 <$username> 添加至黑名单") { repository.add(username) }
    }

    fun delBlacklist(username: String) {
        launchWithToast("已经将用户 <$username> 从黑名单移除") { repository.remove(username) }
    }

    /** Refreshes the device's copy of the blacklist; failures are only logged. */
    fun syncBlacklists() {
        scope.launch {
            try {
                repository.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e)
            }
        }
    }

    private fun launchWithToast(success: String, action: suspend () -> Unit) {
        scope.launch {
            val message = try {
                action()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.format(e)
            }
            UIUtils.toast(message)
        }
    }
}
