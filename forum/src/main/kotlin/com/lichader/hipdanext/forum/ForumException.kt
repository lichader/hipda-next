package com.lichader.hipdanext.forum

import java.io.IOException
import java.net.SocketTimeoutException

/**
 * The ways a forum operation can fail. Site adapter functions throw only these, apart from
 * coroutine cancellation. They carry no user-facing text of their own, except [Server],
 * which holds the forum's message.
 */
sealed class ForumException(message: String?, cause: Throwable? = null) : Exception(message, cause) {

    /** The request did not complete: no connection, DNS failure, TLS error, connection reset. */
    class Network(override val cause: IOException) : ForumException(cause.message, cause)

    /** The server did not answer in time. */
    class Timeout(override val cause: IOException) : ForumException(cause.message, cause)

    /** The server answered with a non-2xx status. */
    class Http(val code: Int, statusMessage: String) : ForumException("HTTP $code $statusMessage")

    /** The forum rejected the request and said why (its `div.alert_error` text). */
    class Server(override val message: String) : ForumException(message)

    /** The forum served a logged-out page, and logging in again with the stored credentials failed. */
    class NotLoggedIn : ForumException("Not logged in")

    /** The page did not have the expected structure. */
    class Parse(override val message: String) : ForumException(message)

    companion object {
        fun from(e: IOException): ForumException =
            if (e is SocketTimeoutException) Timeout(e) else Network(e)
    }
}
