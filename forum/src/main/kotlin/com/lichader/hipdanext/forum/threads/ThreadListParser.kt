package com.lichader.hipdanext.forum.threads

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.between
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses a forum's thread list (`forumdisplay.php`). Same rules as the old `HiParserThreadList`,
 * without its side effects and filters: the app filters sticky and blacklisted threads.
 */
internal object ThreadListParser {

    /**
     * The page's threads. A row without a title, author, creation time, counts or last poster is
     * skipped, as before.
     *
     * @throws ForumException.Parse if the page has no thread rows (`tbody[id]`)
     */
    fun parse(doc: Document): List<ThreadSummary> {
        val rows = doc.select("tbody[id]")
        if (rows.isEmpty()) throw ForumException.Parse("No thread rows on the forum page")
        return rows.mapNotNull(::row)
    }

    private fun row(row: Element): ThreadSummary? {
        val folder = row.selectFirst("td.folder")
        val folderImage = folder?.selectFirst("img")?.attr("src")
        val sticky = folderImage?.contains("/pin_") == true
        val poll = row.selectFirst("td.icon")?.selectFirst("img")?.attr("src")?.contains("/poll") == true

        val subject = row.selectFirst("th.subject") ?: return null
        val titleLink = subject.select("span a").firstOrNull() ?: return null
        val style = titleLink.attr("style")
        val types = subject.select("em a")

        val author = row.select("td.author").firstOrNull() ?: return null
        val authorLink = author.select("cite a").firstOrNull() ?: return null
        val userLink = authorLink.attr("href")
        if (userLink.length < "space.php?uid=".length) return null
        val timeCreate = author.select("em").firstOrNull()?.text() ?: return null

        val nums = row.select("td.nums").firstOrNull() ?: return null
        val replies = nums.select("strong").firstOrNull()?.text() ?: return null
        val views = nums.select("em").firstOrNull()?.text() ?: return null
        val lastPost = row.select("td.lastpost cite").firstOrNull()?.text() ?: return null

        val lastPage = row.select("span.threadpages a").lastOrNull()?.attr("href").orEmpty()
            .between("page=", "&").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

        return ThreadSummary(
            tid = titleLink.attr("href").between("tid=", "&"),
            title = titleLink.text(),
            titleColor = if (style.isEmpty()) null else style.between("color:", "").trim(),
            type = if (types.isEmpty()) null else types.text(),
            isNew = folder?.select("img")?.firstOrNull()?.attr("src").orEmpty().contains("new"),
            sticky = sticky,
            poll = poll,
            author = authorLink.text(),
            authorId = userLink.between("uid=", "&"),
            timeCreate = timeCreate,
            timeUpdate = row.select("td.lastpost em a").firstOrNull()?.text(),
            replyCount = replies,
            viewCount = views,
            lastPost = lastPost,
            withPictures = row.select("img.attach").any { it.attr("src").endsWith("image_s.gif") },
            maxPage = lastPage?.toInt() ?: 1,
        )
    }
}
