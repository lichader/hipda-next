package com.lichader.hipdanext.forum.auth

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.Pages
import org.jsoup.nodes.Document
import java.security.MessageDigest

/** Parses the login form and the login reply. Same rules as the app's old `LoginHelper`. */
internal object LoginParser {

    /** The reply's text on success: "欢迎您回来 <group> <username>". */
    private const val SUCCESS_TEXT = "欢迎您回来"

    /** The reply's text on a refusal, followed by the reason up to "次", such as "您还可以尝试 4 次". */
    private const val FAILURE_TEXT = "登录失败"

    /**
     * The login form's token.
     *
     * @throws ForumException.Server with the forum's notice (`div.alert_info`, without its links) if
     * it shows one instead of the form
     * @throws ForumException.Parse if the page has neither
     */
    fun formToken(doc: Document): String {
        doc.selectFirst("input[name=formhash]")?.attr("value")?.takeIf { it.isNotEmpty() }?.let { return it }
        // Without its link text ("如果您的浏览器没有自动跳转…"), as for the forum's error boxes.
        doc.selectFirst("div.alert_info")?.let { notice ->
            notice.select("a").remove()
            throw ForumException.Server(notice.text())
        }
        Pages.errorMessage(doc)?.let { throw ForumException.Server(it) }
        throw ForumException.Parse("No formhash on the login page")
    }

    /** The result of a login, from the raw `inajax` XML reply. */
    fun result(reply: String): LoginResult = when {
        SUCCESS_TEXT in reply -> LoginResult.Success
        FAILURE_TEXT in reply -> {
            val start = reply.indexOf(FAILURE_TEXT)
            val end = reply.indexOf("次", start)
            LoginResult.Rejected(if (end > start) reply.substring(start, end + 1) else null)
        }
        else -> LoginResult.Unrecognized
    }
}

/**
 * The password as the login form sends it: the MD5 hex digest of the stored password, with
 * backslashes and double quotes escaped first, as the forum's own login page does. A stored value of
 * exactly 32 characters is taken to be a digest already and sent as is (see TD-47).
 */
internal fun forumPassword(stored: String): String {
    if (stored.isEmpty() || stored.length == 32) return stored
    val escaped = stored.replace("\\", "\\\\").replace("\"", "\\\"")
    return MessageDigest.getInstance("MD5").digest(escaped.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
