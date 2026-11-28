package com.lichader.hipdanext.forum.messages

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.parser.PageHeader
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.parser.pageHeader
import com.lichader.hipdanext.forum.session.FormhashCache
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whom a private message goes to. */
sealed interface Recipient {
    data class Uid(val uid: String) : Recipient

    /** A username typed by hand. */
    data class Username(val username: String) : Recipient
}

/** The forum's answer to a message. */
sealed interface SendResult {
    object Sent : SendResult

    /** The forum refused it; [reason] is its message, if it gave one. */
    data class Refused(val reason: String?) : SendResult

    /** The forum answered with nothing. */
    object NoReply : SendResult
}

/** A conversation with one user, with the page header. */
data class Conversation(val messages: List<ListItem>, val header: PageHeader)

/** Private messages. Main-safe; throws [ForumException] on failure. */
interface MessagesApi {

    /** The messages exchanged with user [uid], oldest first. */
    suspend fun conversation(uid: String): Conversation

    /** @throws ForumException.Parse if the form token can't be found */
    suspend fun send(to: Recipient, content: String): SendResult

    /** Deletes the conversation with user [uid]. */
    suspend fun clear(uid: String)
}

class HiPdaMessagesApi(
    private val transport: ForumTransport,
    private val endpoints: HiPdaEndpoints,
    private val formhash: FormhashCache,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MessagesApi {

    override suspend fun conversation(uid: String): Conversation {
        val doc = session.page(endpoints.conversation(uid))
        return withContext(parseDispatcher) { Conversation(MessageParser.conversation(doc), pageHeader(doc)) }
    }

    override suspend fun send(to: Recipient, content: String): SendResult {
        val token = formhash.get() ?: fetchFormhash(if (to is Recipient.Uid) to.uid else "")
        val form = linkedMapOf(
            "formhash" to token,
            "lastdaterange" to System.currentTimeMillis().toString(),
            "handlekey" to "pmreply",
            "message" to content,
        )
        val url = when (to) {
            is Recipient.Uid -> endpoints.sendMessage(to.uid)
            is Recipient.Username -> endpoints.sendMessage(null).also { form["msgto"] = to.username }
        }
        val reply = transport.post(url, form)
        return withContext(parseDispatcher) { MessageParser.sendResult(reply) }
    }

    override suspend fun clear(uid: String) {
        session.page(endpoints.clearConversation(uid))
    }

    /** The token from the message form, as the old send did when it had none. */
    private suspend fun fetchFormhash(uid: String): String {
        val doc = session.page(endpoints.messageForm(uid))
        withContext(parseDispatcher) { formhash.put(Pages.formhash(doc)) }
        return formhash.get() ?: throw ForumException.Parse("No formhash on the message form")
    }
}
