package com.lichader.hipdanext.ui

import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.forum.ForumException
import java.net.UnknownHostException

/**
 * Turns a failed forum operation into the text shown to the user. The texts are the same as
 * `OkHttpHelper.getErrorMessage()`. The forum's own error messages are shown unchanged.
 */
class ForumErrorFormatter(private val isConnected: () -> Boolean) {

    fun format(error: Throwable): String = when (error) {
        is ForumException.Server -> error.message
        is ForumException.Parse -> "页面解析错误"
        is ForumException.NotLoggedIn -> "请重新登录"
        is AccountChangedException -> "登录账号已变更，请重试"
        else -> if (!isConnected()) "请检查网络连接" else when (error) {
            is ForumException.Network ->
                if (error.cause is UnknownHostException) "请检查网络连接." else error.cause.javaClass.simpleName
            is ForumException.Timeout -> "请求超时"
            is ForumException.Http -> "错误代码 (${error.code})"
            else -> error.javaClass.simpleName
        }
    }
}
