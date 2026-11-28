package com.lichader.hipdanext.forum.messages

import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.forum.parser.between
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/** Same rules as the old `HiParser.parseSmsDetail` and `PostSmsAsyncTask`. */
internal object MessageParser {

    private const val NEW_PM_IMAGE = "images/default/notice_newpm.gif"

    /** A conversation's messages; [ListItem.info] is the message's HTML. Empty without the header's account link. */
    fun conversation(doc: Document): List<ListItem> {
        val me = doc.selectFirst("#umenu cite a.noborder") ?: return emptyList()
        val myUid = me.attr("href").between("uid=", "&")
        val myUsername = me.text()
        return doc.select("li.s_clear").mapNotNull { li ->
            val cite = li.selectFirst("p.cite") ?: return@mapNotNull null
            val author = cite.selectFirst("cite")?.text() ?: return@mapNotNull null
            val summary = li.selectFirst("div.summary") ?: return@mapNotNull null
            // The avatar link gives the author's uid; for the account's own messages, the header's.
            val uid = li.selectFirst("a.avatar")?.let { if (author == myUsername) myUid else it.attr("href").between("uid=", "&") }
            ListItem(
                uid = uid,
                author = author,
                time = cite.ownText(),
                info = summary.html(),
                isNew = cite.selectFirst("img")?.attr("src")?.contains(NEW_PM_IMAGE) == true,
                avatar = uid?.let { Avatar.OfUser(it) },
            )
        }
    }

    /** The reply to a sent message: XML whose text is the new message, or the forum's reason for refusing it. */
    fun sendResult(reply: String): SendResult {
        if (reply.isEmpty()) return SendResult.NoReply
        if (reply.contains("class=\"summary\"")) return SendResult.Sent
        val text = Jsoup.parse(reply, "", Parser.xmlParser()).select("root").lastOrNull()?.text().orEmpty()
        val reason = text.substringBefore("<")
        return SendResult.Refused(reason.ifEmpty { null })
    }
}
