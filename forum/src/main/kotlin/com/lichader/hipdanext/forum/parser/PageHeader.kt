package com.lichader.hipdanext.forum.parser

import org.jsoup.nodes.Document

/** What every forum page's header says about the logged-in account. */
data class PageHeader(
    /** The account's uid from the header's space link, or null if the page shows none. */
    val accountUid: String?,
    /** The account's username as the forum spells it, or null with [accountUid]. */
    val accountName: String?,
    /** The count of new notices: thread notices, system messages and friend messages. */
    val noticeCount: Int,
)

/** Same rules as the old `HiParserThreadList` (account) and `NotiHelper.fetchNotification` (notices). */
internal fun pageHeader(doc: Document): PageHeader {
    val links = doc.select("#umenu cite a")
    val link = links.singleOrNull()
    val uid = link?.attr("href")?.between("space.php?uid=", "&")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    val name = link?.text()?.trim()?.takeIf { uid != null }
    val noticeCount = listOf("prompt_threads", "prompt_systempm", "prompt_friend").sumOf { prompt ->
        doc.selectFirst("div.promptcontent a#$prompt")?.text()?.between("(", ")")?.trim()?.toIntOrNull() ?: 0
    }
    return PageHeader(uid, name, noticeCount)
}
