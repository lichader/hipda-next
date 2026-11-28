package com.lichader.hipdanext.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.LoginOutcome
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.auth.LoginResult
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.SocketTimeoutException

/** The bridge keeps the old LoginHelper contract: status codes and LoginEvents for MainFrameActivity. */
@RunWith(AndroidJUnit4::class) // EventBus looks up the Android main looper
class LoginHelperTest {

    private val account = FakeAccount()
    private val helper = LoginHelper(account, ForumErrorFormatter { true })
    private val events = mutableListOf<String>()
    private val subscriber = object {
        @Subscribe
        fun onEvent(event: LoginEvent) {
            events += "${event.mStatus} ${event.mManual} ${event.mMessage}"
        }
    }

    @Before
    fun setUp() = EventBus.getDefault().register(subscriber)

    @After
    fun tearDown() {
        EventBus.getDefault().unregister(subscriber)
        EventBus.getDefault().removeAllStickyEvents()
    }

    private fun loginWith(outcome: LoginOutcome, manual: Boolean = true): Int {
        account.outcome = outcome
        return helper.login(manual)
    }

    @Test
    fun automaticLoginWithoutCredentialsAnnouncesNothing() {
        account.hasCredentials = false

        assertEquals(Constants.STATUS_FAIL_ABORT, helper.login())
        assertEquals(emptyList<String>(), events)
        assertFalse(account.loginCalled)
    }

    @Test
    fun progressIsAnnouncedBeforeTheResult() {
        assertEquals(Constants.STATUS_SUCCESS, loginWith(LoginOutcome.Success))
        assertEquals(listOf("${Constants.STATUS_IN_PROGRESS} true ", "${Constants.STATUS_SUCCESS} true "), events)
        assertTrue(account.progressSeenBeforeLogin)
    }

    @Test
    fun refusalsKeepTheOldTexts() {
        assertEquals(Constants.STATUS_FAIL_ABORT, loginWith(LoginOutcome.Refused(LoginResult.Rejected("登录失败，您还可以尝试 4 次"))))
        loginWith(LoginOutcome.Refused(LoginResult.Rejected(null)))
        loginWith(LoginOutcome.Refused(LoginResult.Unrecognized))

        assertEquals(
            listOf("登录失败，您还可以尝试 4 次", "登录失败,请检查账户信息", "登录失败,未知错误"),
            events.filter { !it.startsWith("${Constants.STATUS_IN_PROGRESS} ") }.map { it.substringAfter("true ") },
        )
    }

    @Test
    fun failuresHaveAResultEventToo() {
        assertEquals(Constants.STATUS_FAIL, loginWith(LoginOutcome.FormUnavailable(ForumException.Parse("no token"))))
        loginWith(LoginOutcome.FormUnavailable(ForumException.Server("您已经登录，请勿重复登录。")))
        loginWith(LoginOutcome.SubmitFailed(ForumException.Timeout(SocketTimeoutException())))
        loginWith(LoginOutcome.Incomplete)

        assertEquals(
            listOf("无法获取登录凭据", "您已经登录，请勿重复登录。", "登录失败 : 请求超时", "登录信息不完整"),
            events.filter { it.startsWith("${Constants.STATUS_FAIL} ") }.map { it.substringAfter("true ") },
        )
    }

    @Test
    fun loggedInPagesLinkToTheUsersSpace() {
        assertTrue(LoginHelper.checkLoggedin(Jsoup.parse("<div id='umenu'><cite><a href='space.php?uid=1'>u</a></cite></div>")))
        assertFalse(LoginHelper.checkLoggedin(Jsoup.parse("<div id='umenu'><a href='logging.php?action=login'>登录</a></div>")))
        assertFalse(LoginHelper.checkLoggedin(Jsoup.parse("<p>no header</p>")))
    }

    private inner class FakeAccount : AccountRepository {
        var outcome: LoginOutcome = LoginOutcome.Success
        var hasCredentials = true
        var loginCalled = false
        var progressSeenBeforeLogin = false
        override val generation: StateFlow<Int> = MutableStateFlow(0)
        override fun isLoggedIn() = false
        override fun hasStoredCredentials() = hasCredentials
        override suspend fun login(manual: Boolean): LoginOutcome {
            loginCalled = true
            progressSeenBeforeLogin = events.lastOrNull()?.startsWith("${Constants.STATUS_IN_PROGRESS} ") == true
            return outcome
        }
        override fun logout() {}
    }
}
