package com.lichader.hipdanext.forum.blacklist

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.parser.between
import org.jsoup.nodes.Document

/** Parses the blacklist page (`pm.php?action=viewblack`). */
internal object BlacklistParser {

    /** The page's text when the blacklist is empty. */
    private const val EMPTY_LIST_TEXT = "暂无数据"

    /**
     * The blacklisted usernames, in page order and without duplicates.
     *
     * @throws ForumException.Server if the forum shows an error instead of the list
     * @throws ForumException.Parse if the page is not a blacklist page, or lists no users
     * but does not say that the list is empty
     */
    fun parse(doc: Document): List<String> {
        Pages.errorMessage(doc)?.let { throw ForumException.Server(it) }

        val container = doc.select("div.blacklist")
        if (container.isEmpty()) {
            throw ForumException.Parse("No div.blacklist on the blacklist page")
        }
        val usernames = doc.select("ul.commonlist a")
            .map { it.attr("href") }
            .filter { "space.php" in it }
            .map { it.between("username=", "&") }
            .filter { it.isNotEmpty() }
            .distinct()
        if (usernames.isEmpty() && EMPTY_LIST_TEXT !in container.text()) {
            throw ForumException.Parse("Blacklist page lists no users and is not marked empty")
        }
        return usernames
    }
}
