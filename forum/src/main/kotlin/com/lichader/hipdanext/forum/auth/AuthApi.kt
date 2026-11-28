package com.lichader.hipdanext.forum.auth

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Logging in to the forum. A login takes two steps: [loginToken] reads the form's token, and
 * [submit] posts the credentials with it. The session cookie the forum sets on success is kept by
 * the app's HTTP client. All functions are main-safe and throw [ForumException] on failure.
 */
interface AuthApi {

    suspend fun loginToken(): String

    suspend fun submit(token: String, credentials: Credentials): LoginResult
}

class HiPdaAuthApi(
    private val transport: ForumTransport,
    private val endpoints: HiPdaEndpoints,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AuthApi {

    override suspend fun loginToken(): String {
        val html = transport.get(endpoints.loginForm())
        return withContext(parseDispatcher) { LoginParser.formToken(Jsoup.parse(html)) }
    }

    override suspend fun submit(token: String, credentials: Credentials): LoginResult {
        val form = linkedMapOf(
            "m_formhash" to token,
            "referer" to endpoints.home(),
            "loginfield" to "username",
            "username" to credentials.username,
            "password" to forumPassword(credentials.password),
            "questionid" to credentials.questionId,
            "answer" to credentials.answer,
            // Keep the session for 30 days.
            "cookietime" to "2592000",
        )
        return LoginParser.result(transport.post(endpoints.loginSubmit(), form))
    }
}
