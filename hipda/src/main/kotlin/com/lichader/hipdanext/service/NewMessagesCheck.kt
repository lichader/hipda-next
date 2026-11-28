package com.lichader.hipdanext.service

import androidx.annotation.WorkerThread
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.lists.ListsApi
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** What the new-messages page says. */
class NewMessages(
    /** Conversations with unread messages. */
    val count: Int,
    /** The only such conversation, when there is exactly one. */
    val single: ListItem?,
    /** The header's count of new notices: thread, system and friend notices. */
    val noticeCount: Int,
)

/** Reads the new-messages page for [NotiHelper], which checks from worker threads (rule 7). */
@Singleton
class NewMessagesCheck @Inject constructor(private val api: ListsApi) {

    /** Blocks until the page is read. @throws ForumException */
    @WorkerThread
    fun fetch(): NewMessages = runBlocking {
        val page = api.newMessages()
        NewMessages(page.items.size, page.items.singleOrNull(), page.header.noticeCount)
    }
}
