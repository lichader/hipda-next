package com.lichader.hipdanext.ui

import com.lichader.hipdanext.forum.ForumException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ForumErrorFormatterTest {

    private var connected = true
    private val formatter = ForumErrorFormatter { connected }

    @Test
    fun forumMessagesAreShownUnchanged() {
        assertEquals("用户名不存在", formatter.format(ForumException.Server("用户名不存在")))
    }

    @Test
    fun anExpiredSessionThatCouldNotBeRenewedAsksToLogInAgain() {
        assertEquals("请重新登录", formatter.format(ForumException.NotLoggedIn()))
    }

    @Test
    fun aResultForAPreviousAccountSaysToRetry() {
        assertEquals("登录账号已变更，请重试", formatter.format(com.lichader.hipdanext.data.account.AccountChangedException()))
    }

    @Test
    fun parseErrorsHaveAFixedText() {
        assertEquals("页面解析错误", formatter.format(ForumException.Parse("No div.blacklist")))
    }

    @Test
    fun requestFailuresUseTheOldOkHttpHelperTexts() {
        assertEquals("请检查网络连接.", formatter.format(ForumException.Network(UnknownHostException("x"))))
        assertEquals("ConnectException", formatter.format(ForumException.Network(ConnectException())))
        assertEquals("请求超时", formatter.format(ForumException.Timeout(SocketTimeoutException())))
        assertEquals("错误代码 (503)", formatter.format(ForumException.Http(503, "Service Unavailable")))
        assertEquals("IllegalStateException", formatter.format(IllegalStateException()))
    }

    @Test
    fun requestFailuresWhileOfflineSayToCheckTheConnection() {
        connected = false
        assertEquals("请检查网络连接", formatter.format(ForumException.Timeout(SocketTimeoutException())))
        assertEquals("请检查网络连接", formatter.format(ForumException.Http(503, "")))
    }
}
