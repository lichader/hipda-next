package com.lichader.hipdanext.forum.auth

/** What the forum's login form takes. [password] is as stored by the app; see [forumPassword]. */
data class Credentials(
    val username: String,
    val password: String,
    /** The security question's ID; "0" means none. */
    val questionId: String,
    val answer: String,
)

/** How the forum answered a login. */
sealed interface LoginResult {

    object Success : LoginResult

    /**
     * The forum refused the login, for example because of a wrong password. [message] is its text,
     * such as "登录失败，您还可以尝试 4 次", or null if the reply named no reason.
     */
    data class Rejected(val message: String?) : LoginResult

    /** A reply the adapter doesn't recognise, neither success nor a refusal. */
    object Unrecognized : LoginResult
}
