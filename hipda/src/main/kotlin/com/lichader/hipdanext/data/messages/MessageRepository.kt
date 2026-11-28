package com.lichader.hipdanext.data.messages

import android.content.Context
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.messages.MessagesApi
import com.lichader.hipdanext.forum.messages.Recipient
import com.lichader.hipdanext.forum.messages.SendResult
import com.lichader.hipdanext.service.NotiHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private messages. Main-safe; throws [ForumException] if a request fails, or
 * [AccountChangedException] if the account changed while a conversation loaded.
 */
interface MessageRepository {

    /** The messages exchanged with user [uid], oldest first. */
    suspend fun conversation(uid: String): List<ListItem>

    suspend fun send(to: Recipient, content: String): SendResult

    /** Deletes the conversation with user [uid]. */
    suspend fun clear(uid: String)

    /** Seconds until the next message may be sent: the forum allows one every 15 seconds. */
    fun waitSecondsToSend(): Int
}

@Singleton
class DefaultMessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MessagesApi,
    private val account: AccountRepository,
    private val notiHelper: NotiHelper,
) : MessageRepository {

    @Volatile
    private var lastSentAt = 0L

    override suspend fun conversation(uid: String): List<ListItem> {
        val generation = account.generation.value
        val conversation = api.conversation(uid)
        if (account.generation.value != generation) throw AccountChangedException()
        notiHelper.fetchNotificationAsync(context, conversation.header.noticeCount)
        return conversation.messages
    }

    override suspend fun send(to: Recipient, content: String): SendResult =
        api.send(to, content).also { if (it == SendResult.Sent) lastSentAt = System.currentTimeMillis() }

    override suspend fun clear(uid: String) = api.clear(uid)

    override fun waitSecondsToSend(): Int {
        val elapsed = (System.currentTimeMillis() - lastSentAt) / 1000
        return if (elapsed < SEND_INTERVAL_SECONDS) (SEND_INTERVAL_SECONDS - elapsed).toInt() else 0
    }

    private companion object {
        const val SEND_INTERVAL_SECONDS = 15L
    }
}
