package com.lichader.hipdanext.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.auth.AuthApi
import com.lichader.hipdanext.forum.auth.Credentials
import com.lichader.hipdanext.forum.auth.LoginResult
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.CookieManager
import java.net.HttpCookie
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private val services = SingletonEntryPoint.get()
    private val settings = services.settings()
    private val cookies = CookieManager().cookieStore
    private val api = FakeAuthApi()
    private val config = object : ForumConfig {
        override val forumServer = "https://forum.invalid"
        override val charset: Charset = Charsets.UTF_8
        override val userAgent = "test"
        override val sessionKey: String? get() = cookies.cookies.firstOrNull { it.name == "cdb_auth" }?.value
    }
    private lateinit var account: DefaultAccountRepository

    @Before
    fun setUp() {
        settings.setUsername("user_1")
        settings.setPassword("secret")
        settings.setSecQuestion("1")
        settings.setSecAnswer("answer")
        account = DefaultAccountRepository(api, settings, config, cookies, services.notiHelper(), services.favorites())
    }

    @Test
    fun automaticLoginWithoutStoredCredentialsSendsNothing() = runBlocking {
        settings.setPassword("")

        assertEquals(LoginOutcome.NoCredentials, account.login(manual = false))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun successfulLoginUsesTheStoredCredentialsAndStartsANewGeneration() = runBlocking {
        val before = account.generation.value

        assertEquals(LoginOutcome.Success, account.login(manual = true))

        assertEquals(listOf("token", "submit t1 user_1 secret 1 answer"), api.calls)
        assertEquals(before + 1, account.generation.value)
    }

    @Test
    fun anAutomaticLoginRenewsTheSessionAndKeepsTheGeneration() = runBlocking {
        val before = account.generation.value

        assertEquals(LoginOutcome.Success, account.login(manual = false))

        assertEquals(listOf("token", "submit t1 user_1 secret 1 answer"), api.calls)
        assertEquals(before, account.generation.value)
    }

    @Test
    fun refusalIsReportedAndKeepsTheGeneration() = runBlocking {
        api.result = LoginResult.Rejected("登录失败，您还可以尝试 4 次")
        val before = account.generation.value

        assertEquals(LoginOutcome.Refused(LoginResult.Rejected("登录失败，您还可以尝试 4 次")), account.login(manual = true))
        assertEquals(before, account.generation.value)
    }

    @Test
    fun anUnreadableFormSendsNoCredentials() = runBlocking {
        val error = ForumException.Server("您已经登录，请勿重复登录。")
        api.tokenError = error

        assertEquals(LoginOutcome.FormUnavailable(error), account.login(manual = true))
        assertEquals(listOf("token"), api.calls)
    }

    @Test
    fun incompleteDetailsAreReportedAfterReadingTheForm() = runBlocking {
        settings.setPassword("")

        assertEquals(LoginOutcome.Incomplete, account.login(manual = true))
        assertEquals(listOf("token"), api.calls)
    }

    @Test
    fun networkFailuresAreRetriedThreeTimes() = runBlocking {
        api.submitErrors += List(3) { ForumException.Timeout(SocketTimeoutException()) }

        val outcome = account.login(manual = true)

        assertTrue(outcome is LoginOutcome.SubmitFailed)
        assertEquals(3, api.calls.count { it.startsWith("submit") })
    }

    @Test
    fun aRetryCanSucceed() = runBlocking {
        api.submitErrors += ForumException.Network(IOException("reset"))

        assertEquals(LoginOutcome.Success, account.login(manual = true))
        assertEquals(2, api.calls.count { it.startsWith("submit") })
    }

    @Test
    fun automaticLoginsThatCoincideShareOne() = runBlocking {
        // The forum sets the session cookie on a successful submit.
        api.onSuccess = { cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_auth", "auth")) }
        api.submitGate = kotlinx.coroutines.CompletableDeferred()

        val first = async { account.login(manual = false) }
        val second = async { account.login(manual = false) }
        while (api.calls.none { it.startsWith("submit") }) yield()
        api.submitGate!!.complete(Unit)

        assertEquals(LoginOutcome.Success, first.await())
        assertEquals(LoginOutcome.Success, second.await())
        assertEquals(1, api.calls.count { it.startsWith("submit") })
    }

    @Test
    fun aManualLoginAlwaysRuns() = runBlocking {
        api.onSuccess = { cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_auth", "auth")) }
        account.login(manual = false)
        account.login(manual = true)

        assertEquals(2, api.calls.count { it.startsWith("submit") })
    }

    @Test
    fun loggedInMeansAUsernameAndTheSessionCookie() {
        assertFalse(account.isLoggedIn())
        cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_auth", "auth"))
        assertTrue(account.isLoggedIn())
    }

    @Test
    fun logoutForgetsTheAccountAndStartsANewGeneration() {
        cookies.add(URI.create("https://www.4d4y.com/forum/"), HttpCookie("cdb_auth", "auth"))
        settings.setUid("100002")
        val before = account.generation.value

        account.logout()

        assertEquals("", settings.username)
        assertEquals("", settings.password)
        assertEquals("", settings.uid)
        assertTrue(cookies.cookies.isEmpty())
        assertFalse(account.isLoggedIn())
        assertEquals(before + 1, account.generation.value)
    }

    private class FakeAuthApi : AuthApi {
        val calls = mutableListOf<String>()
        var tokenError: ForumException? = null
        val submitErrors = ArrayDeque<ForumException>()
        var result: LoginResult = LoginResult.Success
        var onSuccess: () -> Unit = {}
        var submitGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun loginToken(): String {
            calls += "token"
            tokenError?.let { throw it }
            return "t1"
        }

        override suspend fun submit(token: String, credentials: Credentials): LoginResult {
            calls += "submit $token ${credentials.username} ${credentials.password} ${credentials.questionId} ${credentials.answer}"
            submitGate?.await()
            submitErrors.removeFirstOrNull()?.let { throw it }
            if (result == LoginResult.Success) onSuccess()
            return result
        }
    }
}
