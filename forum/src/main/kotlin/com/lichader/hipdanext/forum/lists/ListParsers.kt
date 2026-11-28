package com.lichader.hipdanext.forum.lists

import com.lichader.hipdanext.forum.parser.between
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parsers for the simple lists, with the same rules as the old `HiParser`. A page without its
 * list has no items, as before; the app shows it as empty.
 */
internal object ListParsers {

    class Parsed(val items: List<ListItem>, val maxPage: Int = 1, val searchId: String? = null)

    private const val NEW_PM_IMAGE = "images/default/notice_newpm.gif"

    /** The highest page number among the page links (Discuz marks the current page with strong). */
    private fun maxPage(doc: Document, pages: String): Int =
        (doc.select("$pages a") + doc.select("$pages strong"))
            .maxOfOrNull { it.text().filter { c -> c in '0'..'9' }.toIntOrNull() ?: 0 }
            ?.coerceAtLeast(1) ?: 1

    private fun Element.isNewNotice(): Boolean =
        selectFirst("img")?.attr("src")?.contains(NEW_PM_IMAGE) == true

    /** `my.php?item=posts`: a row with the thread, then a row with the reply. */
    fun myReplies(doc: Document): Parsed {
        val table = doc.selectFirst("table.datatable") ?: return Parsed(emptyList())
        val items = mutableListOf<ListItem>()
        val rows = table.select("tr")
        var item = ListItem()
        // The first row is the header.
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (i % 2 == 1) {
                item = ListItem()
                val links = row.selectFirst("th")?.select("a") ?: continue
                if (links.size != 1) continue
                val href = links.first().attr("href")
                if (!href.contains("redirect.php?goto=")) continue
                item = item.copy(tid = href.between("ptid=", "&"), pid = href.between("pid=", "&"))
                val time = row.selectFirst("td.lastpost")?.text() ?: continue
                item = item.copy(title = links.first().text(), time = time, forum = row.selectFirst("td.forum")?.text())
            } else {
                // As before, the reply row adds the thread row's item, even one that was incomplete.
                val th = row.selectFirst("th")
                items += if (th == null) item else item.copy(info = th.text())
            }
        }
        return Parsed(items, maxPage(doc, "div.pages_btns div.pages"))
    }

    /** `my.php?item=threads`. */
    fun myThreads(doc: Document): Parsed {
        val table = doc.selectFirst("table.datatable") ?: return Parsed(emptyList())
        val rows = table.select("tr")
        val items = (1 until rows.size).mapNotNull { i ->
            val row = rows[i]
            val links = row.selectFirst("th")?.select("a") ?: return@mapNotNull null
            if (links.size != 1) return@mapNotNull null
            val href = links.first().attr("href")
            if (!href.contains("viewthread.php?tid=")) return@mapNotNull null
            val time = row.selectFirst("td.lastpost")?.text() ?: return@mapNotNull null
            ListItem(
                tid = href.between("viewthread.php?tid=", "&"),
                title = links.first().text(),
                time = time,
                forum = row.selectFirst("td.forum")?.text(),
            )
        }
        return Parsed(items, maxPage(doc, "div.pages_btns div.pages"))
    }

    /** `pm.php?filter=privatepm`: one row per conversation. */
    fun messages(doc: Document): Parsed {
        val list = doc.selectFirst("ul.pm_list") ?: return Parsed(emptyList())
        return Parsed(list.select("li").mapNotNull { li ->
            val cite = li.selectFirst("p.cite") ?: return@mapNotNull null
            val name = cite.selectFirst("cite") ?: return@mapNotNull null
            val uidLink = name.selectFirst("a") ?: return@mapNotNull null
            val summary = li.selectFirst("div.summary") ?: return@mapNotNull null
            ListItem(
                uid = uidLink.attr("href").between("uid=", "&"),
                title = summary.text(),
                author = name.text(),
                time = cite.ownText(),
                isNew = cite.isNewNotice(),
                avatar = li.selectFirst("a.avatar")?.selectFirst("img")?.attr("src")?.let { Avatar.Url(it) },
            )
        })
    }

    /** `notice.php`: replies, quotes, system notices and friend requests. */
    fun notices(doc: Document): Parsed {
        val feed = doc.selectFirst("ul.feed") ?: return Parsed(emptyList())
        return Parsed(feed.select("li").mapNotNull { li ->
            val div = li.selectFirst("div") ?: return@mapNotNull null
            when {
                div.hasClass("f_thread") -> threadNotice(div)
                div.hasClass("f_quote") || div.hasClass("f_reply") -> quoteOrReplyNotice(div)
                div.hasClass("f_manage") -> systemNotice(div)
                div.hasClass("f_buddy") -> friendNotice(div)
                else -> null
            }
        })
    }

    private fun friendNotice(root: Element): ListItem {
        val links = root.select("a")
        val first = links.firstOrNull()
        val uid = first?.attr("href")?.between("uid=", "&")
        val isNew = root.isNewNotice()
        // The add-friend link isn't part of the text.
        if (links.size > 1) links[1].remove()
        return ListItem(
            uid = uid, title = "好友信息", author = first?.text(), info = root.text(), isNew = isNew,
            avatar = uid?.let { Avatar.OfUser(it) },
        )
    }

    private fun systemNotice(root: Element): ListItem = ListItem(
        tid = root.selectFirst("a")?.attr("href")?.between("tid=", "&"),
        title = "系统信息",
        info = root.text(),
        isNew = root.isNewNotice(),
        avatar = Avatar.System,
    )

    private fun threadNotice(root: Element): ListItem? {
        var item = ListItem()
        var info = ""
        for (a in root.select("a")) {
            val href = a.attr("href")
            if (href.contains("space.php")) {
                // The users who replied.
                info += a.text() + " "
            } else if (href.contains("redirect.php?")) {
                item = item.copy(title = a.text(), tid = href.between("ptid=", "&"), pid = href.between("pid=", "&"))
                break
            }
        }
        val time = root.selectFirst("em")?.text() ?: return null
        info += if (root.text().contains("回复了您关注的主题")) "回复了您关注的主题" else "回复了您的帖子 "
        return item.copy(time = time, isNew = true, info = info)
    }

    private fun quoteOrReplyNotice(root: Element): ListItem? {
        var item = ListItem()
        for (a in root.select("a")) {
            val href = a.attr("href")
            if (href.contains("space.php")) {
                val uid = href.between("uid=", "&")
                item = item.copy(author = a.text(), avatar = Avatar.OfUser(uid))
            } else if (href.contains("viewthread.php")) {
                item = item.copy(title = a.text(), tid = href.between("tid=", "&"))
            } else if (href.contains("redirect.php")) {
                item = item.copy(tid = href.between("ptid=", "&"), pid = href.between("pid=", "&"))
            }
        }
        val time = root.selectFirst("em")?.text() ?: return null
        val summaries = root.select(".summary")
        val info = if (summaries.isEmpty()) "" else {
            val dds = summaries.select("dd")
            if (dds.size == 2) {
                "<u>您的帖子:</u>" + dds[0].text() + "<br><u>" + item.author + " 说:</u>" + dds[1].text()
            } else {
                summaries.first().text()
            }
        }
        return item.copy(time = time, info = info, isNew = root.isNewNotice())
    }

    /** `search.php` results: new posts, and a user's threads. */
    fun search(doc: Document): Parsed {
        val pageLinks = doc.select("div.pages_btns div.pages a") + doc.select("div.pages_btns div.pages strong")
        val searchId = pageLinks.firstOrNull()?.attr("href")?.between("searchid=", "&")
        val items = doc.select("tbody").mapNotNull { tbody ->
            val titleLink = tbody.selectFirst("tr th.subject a") ?: return@mapNotNull null
            val authorLink = tbody.selectFirst("tr td.author cite a") ?: return@mapNotNull null
            val spaceUrl = authorLink.attr("href")
            ListItem(
                tid = titleLink.attr("href").between("tid=", "&"),
                title = titleLink.text(),
                author = authorLink.text(),
                time = tbody.selectFirst("tr td.author em")?.text(),
                forum = tbody.selectFirst("tr td.forum")?.text(),
                avatar = if (spaceUrl.isEmpty()) null else Avatar.OfUser(spaceUrl.between("uid=", "&")),
            )
        }
        return Parsed(items, maxPage(doc, "div.pages_btns div.pages"), searchId)
    }

    /** `my.php?item=favorites` and `item=attention`. */
    fun savedThreads(doc: Document): Parsed {
        val items = doc.select("table.datatable tbody tr").mapNotNull { row ->
            val th = row.selectFirst("th") ?: return@mapNotNull null
            val link = th.selectFirst("a") ?: return@mapNotNull null
            ListItem(
                tid = link.attr("href").between("tid=", "&"),
                title = th.text(),
                time = row.selectFirst("td.lastpost")?.text()?.trim(),
                forum = row.selectFirst("td.forum")?.text()?.trim(),
            )
        }
        return Parsed(items, maxPage(doc, "div.pages"))
    }
}
