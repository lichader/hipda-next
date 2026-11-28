package com.lichader.hipdanext.forum.thread

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.between
import com.lichader.hipdanext.forum.parser.pageHeader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Whitelist

/**
 * Parses a thread page (`viewthread.php`) into posts with structured content. Same rules as the
 * old `HiParserThreadDetail`, which built the app's HTML as it went; the app now does that from
 * the [Part]s. The page is changed while parsing (the poll and some nodes are removed).
 */
internal object ThreadParser {

    private const val SMILIES = "4d4y.com/forum/images/smilies/"
    private const val FORUM_IMAGES = "4d4y.com/forum/images/"
    private val URL = Regex("[(http(s)?):\\/\\/(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)")
    private val QUOTE_TAGS: Whitelist = Whitelist()
        .addTags("a", "br", "p", "b", "i", "strike", "strong", "u", "font")
        .addAttributes("a", "href")
        .addAttributes("font", "color")
        .addProtocols("a", "href", "http", "https")

    /**
     * @param tid the thread's ID, if known
     * @param baseUrl the forum's address, for relative image and attachment links
     * @throws ForumException.Parse if the page has no post list
     */
    fun parse(doc: Document, tid: String?, baseUrl: String): ThreadPage {
        // The last page; the current one is in <strong>. A thread with one page has no div.pages.
        var lastPage = 1
        var page = 1
        doc.select("div#wrap div.forumcontrol div.pages").firstOrNull()?.childNodes()?.forEach { n ->
            val number = digits((n as Element).text())
            if (number > lastPage) lastPage = number
            if (n.nodeName() == "strong") page = number
        }
        val header = pageHeader(doc)

        var fid = 0
        var title: String? = null
        doc.selectFirst("div#nav")?.let { nav ->
            val links = nav.select("a")
            links.firstOrNull { it.attr("href").indexOf("fid=") > 0 }?.let { fid = number(it.attr("href").between("fid=", "&")) }
            // The title is what's left of the navigation without its links.
            links.remove()
            title = nav.text().replace("»", "").trim()
        }
        // Only on the first page.
        if (title.isNullOrEmpty()) {
            doc.selectFirst("div#threadtitle")?.let {
                it.select("a").remove()
                title = it.text()
            }
        }

        val lists = doc.select("div#wrap div#postlist")
        if (lists.size != 1) throw ForumException.Parse("No post list on the thread page")
        val list = lists.first()
        val posts = mutableListOf<Post>()
        var postsPerPage: Int? = null
        for (i in 0 until list.childNodeSize()) {
            val postE = list.child(i)
            val id = postE.attr("id")
            if (id.length < "post_".length) continue
            val time = postE.selectFirst("table tbody tr td.postcontent div.postinfo div.posterinfo div.authorinfo em")?.text() ?: continue
            val floor = postE.selectFirst("table tbody tr td.postcontent div.postinfo strong a em")?.text() ?: continue
            val warned = postE.selectFirst("table tbody tr td.postcontent span.postratings a")?.attr("href")?.contains("viewwarning") == true

            // The posts per page follow the account's setting.
            if (i == 0) {
                if (page == 1 && lastPage > 1) {
                    postsPerPage = list.childNodeSize()
                } else if (page > 1) {
                    postsPerPage = (number(floor) - 1) / (page - 1)
                }
            }

            val authorLink = postE.selectFirst("table tbody tr td.postauthor div.postinfo a") ?: continue
            val uid = authorLink.attr("href").between("uid=", "&")
            if (uid.isEmpty()) continue

            posts += post(doc, postE, id.substring("post_".length), number(floor), floor, time, uid, authorLink.text(), warned, baseUrl)
        }
        val threadId = tid?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        return ThreadPage(threadId, fid, title, page, lastPage, posts, postsPerPage, header)
    }

    /** The uid of the first post's author, or empty; for showing only the thread author's posts. */
    fun authorId(doc: Document): String =
        doc.selectFirst("td.postauthor div.postinfo a")?.attr("href")?.between("uid=", "&").orEmpty()

    private fun post(
        doc: Document, postE: Element, pid: String, floor: Int, floorText: String, time: String,
        uid: String, author: String, warned: Boolean, baseUrl: String,
    ): Post {
        val signature = postE.selectFirst("td.postbottom > div.signatures")?.text()
        fun post(parts: List<Part>, poll: Poll? = null, status: String? = null) =
            Post(pid, floor, time, uid, author, warned, status, signature, poll, parts)

        var messages = postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.t_msgfontfix table tbody tr td.t_msgfont")
        if (messages.isEmpty()) {
            val locked = postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.locked")
            if (locked.isNotEmpty()) return post(listOf(Part.Locked(locked.text())))
        }

        val poll = if (floorText == "1") poll(doc) else null

        val parts = mutableListOf<Part>()
        var pollFirstPost = false
        if (messages.isEmpty()) {
            messages = postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.specialmsg table tbody tr td.t_msgfont")
            pollFirstPost = floorText == "1"
        }
        if (pollFirstPost) {
            parts += Part.PollSummary(
                postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.pollinfo").text(),
                postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.pollchart table  tbody tr").map { it.text() },
            )
        }
        if (messages.isEmpty()) return post(parts + Part.Missing, poll)
        val message = messages.first()
        if (message.childNodeSize() == 0) return post(parts + Part.Empty, poll)

        // The status line is a field, not content.
        val status = message.selectFirst("i.pstatus")?.let { it.text().also { _ -> it.remove() } }

        Walker(parts, baseUrl).children(message, RunStyle())

        // Attached images listed under the message.
        for (dl in postE.select("table tbody tr td.postcontent div.defaultpost div.postmessage div.t_msgfontfix div.postattachlist dl.attachimg")) {
            val size = dl.selectFirst("em")?.let { sizeInBytes(it.text().between("(", ")")) } ?: 0L
            dl.selectFirst("img")?.let { parts += attachedImage(it, size, baseUrl) }
        }
        // Other attachments.
        postE.select("dl.t_attachlist p.attachname").forEachIndexed { j, name ->
            val links = name.select("a[href]")
            val link = links.firstOrNull() ?: return@forEachIndexed
            if (link.attr("href").startsWith("attachment.php?")) {
                links.remove()
                val description = name.text()
                if (j == 0) parts += Part.LineBreak
                parts += Part.Attachment(link.attr("href"), link.text(), description)
            }
        }
        return post(parts, poll, status)
    }

    /** The thread's poll form, which is then removed from the page, as before; null if absent or unreadable. */
    private fun poll(doc: Document): Poll? {
        val form = doc.selectFirst("form#poll") ?: return null
        return try {
            val info = form.selectFirst("div.pollinfo")!!
            info.select("a").remove()
            var title = info.html()
            val formhash = form.selectFirst("input[name=formhash]")?.`val`()
            form.selectFirst("p.polltimer")?.let { title += "<br>" + it.html() }
            val maxAnswer = if (info.text().trim().startsWith("多选投票")) {
                number(info.text().between("最多可选", "项")).takeIf { it > 1 }
            } else null

            val options = mutableListOf<PollOption>()
            val rows = form.select("div.pollchart > table > tbody > tr")
            for (row in rows) {
                val check = row.selectFirst("td.selector input")
                val text = row.selectFirst("td.polloption label")
                if (text != null) {
                    options += PollOption(check?.attr("value"), text.text(), null)
                } else {
                    val rates = row.select("td").lastOrNull()
                    if (rates != null && options.isNotEmpty() && rates.select("em").isNotEmpty()) {
                        options[options.size - 1] = options.last().copy(rates = rates.text())
                    }
                }
            }
            val footer = rows.lastOrNull()?.takeIf { it.select("td").isNotEmpty() }?.select("td")?.lastOrNull()?.let {
                it.select("button").remove()
                it.text()
            }
            Poll(title, formhash, maxAnswer, options, footer).also { form.remove() }
        } catch (e: Exception) {
            null
        }
    }

    /** Walks a message's nodes, collecting parts; a node's style comes from the tags around it. */
    private class Walker(private val parts: MutableList<Part>, private val baseUrl: String) {

        fun children(parent: Node, style: RunStyle) {
            var child: Node? = if (parent.childNodeSize() > 0) parent.childNode(0) else null
            while (child != null) {
                visit(child, style)
                // Read after the visit: an attachment's description, the next node, may have been removed.
                child = child.nextSibling()
            }
        }

        private fun visit(node: Node, style: RunStyle) {
            when (val name = node.nodeName()) {
                "i", "u", "em", "strike", "ol", "ul", "hr", "blockquote", "font" -> {
                    var inner = when (name) {
                        "i" -> style.copy(italic = true)
                        "u" -> style.copy(underline = true)
                        "strike" -> style.copy(strike = true)
                        else -> style
                    }
                    if (name == "font") {
                        val font = node as Element
                        if (font.attr("size") == "1") inner = inner.copy(smallFont = true)
                        val color = font.attr("color").trim()
                        if (color.isNotEmpty()) inner = inner.copy(colors = inner.colors + color)
                    }
                    children(node, inner)
                }
                "strong" -> {
                    val element = node as Element
                    val text = element.text()
                    val href = element.selectFirst("a[href]")?.attr("href")
                    val pid = href?.between("pid=", "&").orEmpty()
                    val tid = href?.between("ptid=", "&").orEmpty()
                    if (text.startsWith("回复 ") && text.contains("#")) {
                        val floor = digits(text.substring(0, text.indexOf("#")))
                        val author = text.substring(text.lastIndexOf("#") + 1).trim()
                        if (author.isNotEmpty() && isValidId(pid) && floor > 0) {
                            parts += Part.FloorLink(text, tid, pid, floor, author)
                            return
                        }
                    }
                    children(node, style.copy(bold = true))
                }
                "#text" -> text((node as TextNode).text(), style)
                "li" -> children(node, style)
                "br" -> parts += Part.LineBreak
                "p" -> if (!(node as Element).hasClass("imgtitle")) children(node, style)
                "img" -> image(node as Element)
                "span" -> span(node as Element, style)
                "a" -> link(node as Element, style)
                "div" -> div(node as Element, style)
                "table", "tbody", "dl", "dt", "dd" -> children(node, style)
                "tr" -> {
                    parts += Part.LineBreak
                    children(node, style)
                }
                "td" -> {
                    parts += Part.Space
                    children(node, style)
                }
                "script", "#data" -> video(node.toString())
                "#comment" -> {}
                else -> parts += Part.Unparsed(name, node.toString())
            }
        }

        /** Plain text, with web addresses and emails in it made into links. */
        private fun text(raw: String, style: RunStyle) {
            if (raw.isEmpty()) return
            // URLs are found in the text with < and > escaped, as before.
            val text = raw.replace("<", "&lt;").replace(">", "&gt;")
            var last = 0
            for (match in URL.findAll(text)) {
                plain(text.substring(last, match.range.first), style)
                val url = match.value
                parts += if (url.contains("@") && !url.contains("/")) Part.Email(url) else Part.Link(url, url, style.smallFont)
                last = match.range.last + 1
            }
            if (last < text.length) plain(text.substring(last), style)
        }

        private fun plain(escaped: String, style: RunStyle) {
            if (escaped.trim().isNotEmpty()) parts += Part.Text(escaped.replace("&lt;", "<").replace("&gt;", ">"), style)
        }

        /** A span with attachment links (not image attachments) is the attachment; others are walked. */
        private fun span(span: Element, style: RunStyle) {
            var attachment = false
            for (a in span.select("a")) {
                val href = a.attr("href")
                if (href.contains("attachment.php?") && !href.contains("nothumb=")) {
                    var description = ""
                    val next = span.nextSibling()
                    if (next != null && next.nodeName() == "#text") {
                        description = next.toString()
                        next.remove()
                    }
                    parts += Part.Attachment(href, a.text(), description)
                    attachment = true
                }
            }
            if (!attachment) children(span, style)
        }

        private fun link(a: Element, style: RunStyle) {
            val text = a.text()
            var url = a.attr("href")
            if (a.childNodeSize() > 0 && a.childNode(0).nodeName() == "img") {
                if (!url.startsWith("javascript:")) parts += Part.Link(url, url, false)
                children(a, style)
                return
            }
            if (url.startsWith("attachment.php?")) {
                parts += Part.Attachment(url, text, null)
                return
            }
            // Signature links ("小尾巴") are small.
            var smallFont = style.smallFont
            if (!smallFont && a.childNodeSize() > 0 && a.childNode(0).nodeName() == "font" && a.childNode(0).attr("size") == "1") {
                smallFont = true
            }
            if (smallFont) url = replaceOldDomain(url)
            parts += Part.Link(text, url, smallFont)
            // Rarely, a link holds images.
            a.select("img").forEach { image(it) }
        }

        private fun div(div: Element, style: RunStyle) {
            when {
                div.hasClass("t_attach") || div.hasClass("attach_popup") -> {}
                div.hasClass("quote") -> {
                    val href = div.select("a").map { it.attr("href") }.firstOrNull { it.contains("redirect.php?goto=findpost") }
                    val heading = div.selectFirst("font[size=2]")
                    val authorAndTime = heading?.text().orEmpty()
                    heading?.remove()
                    div.select("[style*=display][style*=none]").remove()
                    parts += Part.Quote(
                        Jsoup.clean(div.html(), "", QUOTE_TAGS, Document.OutputSettings().prettyPrint(false)),
                        authorAndTime, href?.between("ptid=", "&").orEmpty(), href?.between("pid=", "&").orEmpty(),
                    )
                }
                else -> children(div, style)
            }
        }

        /** Flash videos; Youku players become Youku's mobile page. */
        private fun video(html: String) {
            var url = html.between("'src', '", "'")
            if (url.startsWith("http://player.youku.com/player.php")) {
                url = "http://v.youku.com/v_show/id_" + url.between("sid/", "/v.swf")
                if (!url.endsWith(".html")) url += ".html"
                parts += Part.Video(url, youku = true)
            } else if (url.startsWith("http")) {
                parts += Part.Video(url, youku = false)
            }
        }

        private fun image(img: Element) {
            val src = absolute(img.attr("src"), baseUrl)
            val id = img.attr("id")
            when {
                id.startsWith("aimg") || src.contains("images/common/none.gif") -> {
                    val menu = img.parent().parent().select("div#${id}_menu").firstOrNull()
                    val size = menu?.let { sizeInBytes(it.text().between("(", ")")) } ?: 0L
                    parts += attachedImage(img, size, baseUrl)
                }
                src.contains(SMILIES) -> parts += Part.Smiley(src)
                // The forum's own icons and inline base64 images are left out.
                src.contains(FORUM_IMAGES) || src.contains("data:image/") -> {}
                src.contains("://") -> parts += Part.ExternalImage(src)
                else -> parts += Part.UnknownImage(src)
            }
        }
    }

    private fun attachedImage(img: Element, size: Long, baseUrl: String): Part.AttachedImage {
        val src = absolute(img.attr("src"), baseUrl)
        val file = absolute(img.attr("file"), baseUrl)
        var onclick = img.attr("onclick")
        onclick = if (onclick.startsWith("zoom") && onclick.contains("attachment")) "attachment" + onclick.between("attachment", "'") else ""
        onclick = absolute(onclick, baseUrl)
        val thumbUrl = if (src.isNotEmpty() && src.contains("thumb.")) src else ""
        var fullUrl = onclick.ifEmpty { file }
        if (fullUrl.isEmpty()) fullUrl = thumbUrl
        return Part.AttachedImage(fullUrl, size, thumbUrl)
    }

    private fun absolute(url: String, baseUrl: String): String = if (url.isEmpty() || url.contains("://")) url else baseUrl + url

    /** Old forum domain links in signatures point at the current one. */
    private fun replaceOldDomain(url: String): String = if (url.contains(".hi-pda.com")) url.replace(".hi-pda.com", ".4d4y.com") else url

    /** "708 Bytes", "100.1 KB", "2.22 MB" in bytes; -1 if unreadable, as before. */
    private fun sizeInBytes(text: String): Long {
        val size = text.trim().uppercase()
        return try {
            when {
                size.endsWith("KB") -> Math.round(size.replace("KB", "").trim().toDouble() * 1024)
                size.endsWith("MB") -> Math.round(size.replace("MB", "").trim().toDouble() * 1024 * 1024)
                size.endsWith("BYTES") -> size.replace("BYTES", "").trim().toLong()
                else -> -1
            }
        } catch (e: NumberFormatException) {
            -1
        }
    }

    /** The ASCII digits of [text] as a number, or 0. */
    private fun digits(text: String): Int = text.filter { it in '0'..'9' }.toIntOrNull() ?: 0

    private fun number(text: String): Int = text.trim().toIntOrNull() ?: 0

    private fun isValidId(id: String): Boolean = id.isNotEmpty() && number(id) > 0
}
