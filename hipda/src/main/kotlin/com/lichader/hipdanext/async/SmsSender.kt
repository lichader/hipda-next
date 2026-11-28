package com.lichader.hipdanext.async

import androidx.appcompat.app.AlertDialog
import com.lichader.hipdanext.data.messages.MessageRepository
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.messages.Recipient
import com.lichader.hipdanext.forum.messages.SendResult
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What a screen hears about a message it sends. */
interface SmsPostListener {
    fun onSmsPrePost()

    /** @param status `Constants.STATUS_SUCCESS` or `STATUS_FAIL` */
    fun onSmsPostDone(status: Int, message: String, dialog: AlertDialog?)
}

/**
 * Sends private messages for the Java screens (rule 7), with the old `PostSmsAsyncTask`'s
 * callbacks and messages.
 */
@Singleton
class SmsSender @Inject constructor(
    private val repository: MessageRepository,
    private val errors: ForumErrorFormatter,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** Sends [content] to [uid], or to [username] if [uid] is empty. */
    fun send(uid: String?, username: String?, content: String, listener: SmsPostListener, dialog: AlertDialog?) {
        val to = if (!uid.isNullOrEmpty()) Recipient.Uid(uid) else Recipient.Username(username.orEmpty())
        listener.onSmsPrePost()
        scope.launch {
            val (status, message) = try {
                when (val result = repository.send(to, content)) {
                    SendResult.Sent -> Constants.STATUS_SUCCESS to "短消息发送成功."
                    is SendResult.Refused -> Constants.STATUS_FAIL to (result.reason ?: "短消息发送失败.")
                    SendResult.NoReply -> Constants.STATUS_FAIL to "短消息发送失败 :  无返回结果"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForumException.Parse) {
                Constants.STATUS_FAIL to "无法获取发送凭据"
            } catch (e: Exception) {
                Constants.STATUS_FAIL to "短消息发送失败 :  " + errors.format(e)
            }
            listener.onSmsPostDone(status, message, dialog)
        }
    }

    /** Seconds until the next message may be sent. */
    val waitSecondsToSend: Int get() = repository.waitSecondsToSend()
}
