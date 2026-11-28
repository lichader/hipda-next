package com.lichader.hipdanext.data.account

import com.lichader.hipdanext.async.FavoriteHelper
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.auth.AuthApi
import com.lichader.hipdanext.forum.auth.Credentials
import com.lichader.hipdanext.forum.auth.LoginResult
import com.lichader.hipdanext.service.NotiHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.CookieStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The logged-in account changed while a request ran, so its result belongs to the previous account
 * and was not stored (TD-22).
 */
class AccountChangedException : Exception("The account changed while the request ran")

/** How a login ended. */
sealed interface LoginOutcome {

    object Success : LoginOutcome

    /** An automatic login was not tried, because no username or password is stored. */
    object NoCredentials : LoginOutcome

    /** The stored login details are incomplete. */
    object Incomplete : LoginOutcome

    /** The forum refused the login ([LoginResult.Rejected]) or answered unexpectedly ([LoginResult.Unrecognized]). */
    data class Refused(val result: LoginResult) : LoginOutcome

    /** The login form could not be read, so nothing was sent. */
    data class FormUnavailable(val error: ForumException) : LoginOutcome

    /** Sending the login failed on every attempt. */
    data class SubmitFailed(val error: ForumException) : LoginOutcome
}

/**
 * The logged-in forum account: its session, login and logout.
 *
 * [generation] changes whenever the account may have changed, on every successful login and every
 * logout (an account switch is a logout plus a login). Work started under an older generation
 * belongs to a previous account, and its results can be discarded (TD-22).
 */
interface AccountRepository {

    val generation: StateFlow<Int>

    /** Whether a username is stored and the forum's session cookie is present. */
    fun isLoggedIn(): Boolean

    /** Whether a username and password are stored, so an automatic login can be tried. */
    fun hasStoredCredentials(): Boolean

    /**
     * Logs in with the stored credentials. Main-safe. Logins run one at a time; an automatic login
     * that waited while another one succeeded reuses that session instead of logging in again.
     */
    suspend fun login(manual: Boolean): LoginOutcome

    /** Forgets the account: credentials, session cookies and the account's cached data. */
    fun logout()
}

@Singleton
class DefaultAccountRepository @Inject constructor(
    private val api: AuthApi,
    private val settings: HiSettingsHelper,
    private val config: ForumConfig,
    private val cookieStore: CookieStore,
    private val notiHelper: NotiHelper,
    private val favoriteHelper: FavoriteHelper,
) : AccountRepository {

    private val _generation = MutableStateFlow(0)
    override val generation: StateFlow<Int> = _generation.asStateFlow()

    /** One login at a time: the jobs' relogin and the site adapter's session renewal can coincide. */
    private val loginLock = Mutex()

    override fun isLoggedIn(): Boolean = !settings.username.isNullOrEmpty() && config.sessionKey != null

    override fun hasStoredCredentials(): Boolean =
        !settings.username.isNullOrEmpty() && !settings.password.isNullOrEmpty()

    override suspend fun login(manual: Boolean): LoginOutcome {
        if (!manual && !hasStoredCredentials()) return LoginOutcome.NoCredentials
        val seen = _generation.value
        return loginLock.withLock {
            // Another login finished while this one waited; for an automatic login, its session will do.
            if (!manual && _generation.value != seen && isLoggedIn()) LoginOutcome.Success else submitLogin()
        }
    }

    private suspend fun submitLogin(): LoginOutcome {
        val token = try {
            api.loginToken()
        } catch (e: ForumException) {
            return LoginOutcome.FormUnavailable(e)
        }
        if (!settings.isLoginInfoValid) return LoginOutcome.Incomplete

        val credentials = Credentials(
            username = settings.username,
            password = settings.password,
            questionId = settings.secQuestion.orEmpty(),
            answer = settings.secAnswer.orEmpty(),
        )
        var lastError: ForumException? = null
        repeat(MAX_ATTEMPTS) {
            val result = try {
                api.submit(token, credentials)
            } catch (e: ForumException) {
                lastError = e
                return@repeat
            }
            if (result != LoginResult.Success) return LoginOutcome.Refused(result)
            _generation.update { it + 1 }
            return LoginOutcome.Success
        }
        return LoginOutcome.SubmitFailed(lastError!!)
    }

    override fun logout() {
        settings.setUsername("")
        settings.setPassword("")
        settings.setSecQuestion("")
        settings.setSecAnswer("")
        settings.setUid("")
        notiHelper.clearNotification()
        cookieStore.removeAll()
        favoriteHelper.clearAll()
        settings.setBlacklists(ArrayList())
        _generation.update { it + 1 }
    }

    private companion object {
        /** Sending the login is retried on network failures, as before. */
        const val MAX_ATTEMPTS = 3
    }
}
