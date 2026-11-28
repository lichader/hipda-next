package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.auth.LoginResult
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A bridge to [AccountRepository] for the Java code: jobs, `MainFrameActivity`, `LoginDialog`,
 * `NotiWorker` and `TaskHelper`. It keeps the old API: blocking calls, `Constants.STATUS_*` codes,
 * and [LoginEvent]s on EventBus, which `MainFrameActivity` shows. Delete it once those callers use
 * the repository.
 */
@Singleton
class LoginHelper @Inject constructor(
    private val account: AccountRepository,
    private val errors: ForumErrorFormatter,
) {

    fun login(): Int = login(false)

    /**
     * Logs in with the stored credentials, and reports the attempt and its result as [LoginEvent]s,
     * except when an automatic login has no credentials to try. Blocks; call it off the main thread.
     *
     * @return `Constants.STATUS_SUCCESS`, `STATUS_FAIL_ABORT` (refused, or nothing to try) or
     * `STATUS_FAIL` (the request failed)
     */
    fun login(manual: Boolean): Int = runBlocking { loginAndAnnounce(manual) }

    /** [login] for coroutines, such as the site adapter's session renewal. Main-safe. */
    suspend fun loginAndAnnounce(manual: Boolean): Int {
        if (!manual && !account.hasStoredCredentials()) return Constants.STATUS_FAIL_ABORT

        post(manual, Constants.STATUS_IN_PROGRESS, "")
        val outcome = account.login(manual)
        val (status, message) = when (outcome) {
            LoginOutcome.Success -> Constants.STATUS_SUCCESS to ""
            LoginOutcome.NoCredentials -> Constants.STATUS_FAIL_ABORT to ""
            LoginOutcome.Incomplete -> Constants.STATUS_FAIL to "登录信息不完整"
            is LoginOutcome.Refused -> Constants.STATUS_FAIL_ABORT to when (val result = outcome.result) {
                is LoginResult.Rejected -> result.message ?: "登录失败,请检查账户信息"
                else -> "登录失败,未知错误"
            }
            is LoginOutcome.FormUnavailable -> Constants.STATUS_FAIL to when (val e = outcome.error) {
                is ForumException.Parse -> "无法获取登录凭据"
                else -> errors.format(e)
            }
            is LoginOutcome.SubmitFailed -> Constants.STATUS_FAIL to "登录失败 : " + errors.format(outcome.error)
        }
        post(manual, status, message)
        return status
    }

    fun isLoggedIn(): Boolean = account.isLoggedIn()

    fun logout() = account.logout()

    private fun post(manual: Boolean, status: Int, message: String) {
        val event = LoginEvent()
        event.mManual = manual
        event.mStatus = status
        event.mMessage = message
        EventBus.getDefault().postSticky(event)
    }

    companion object {
        /** Whether a forum page was served to a logged-in user: its header links to the user's space. */
        @JvmStatic
        fun checkLoggedin(doc: Document): Boolean =
            doc.selectFirst("div#umenu")?.html()?.contains("space.php") == true
    }
}
