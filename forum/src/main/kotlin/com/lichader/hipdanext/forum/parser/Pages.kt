package com.lichader.hipdanext.forum.parser

import org.jsoup.nodes.Document

/** Parsing shared by all forum pages. */
internal object Pages {

    /**
     * The page's form token, or null if the page has none: from a form (`input[name=formhash]`), or
     * else from the logout link in the page header, which carries the same token.
     */
    fun formhash(doc: Document): String? =
        doc.selectFirst("input[name=formhash]")?.`val`()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("div#umenu a[href*=formhash=]")?.attr("href")?.between("formhash=", "&")
                ?.takeIf { it.isNotEmpty() }

    /**
     * The message of the forum's error box (`div.alert_error`) without its links, or null if
     * the page shows none. Pages report most rejected requests this way.
     */
    fun errorMessage(doc: Document): String? {
        val box = doc.selectFirst("div.alert_error") ?: return null
        box.select("a").remove()
        return box.text().takeIf { it.isNotEmpty() }
    }
}

/**
 * The text between the first [start] and the next [end] after it, or "" if [start] is
 * missing. An empty or missing [end] takes the rest of the string. Same behavior as the
 * app's `Utils.getMiddleString`.
 */
internal fun String.between(start: String, end: String): String {
    val from = indexOf(start)
    if (from == -1) return ""
    val startIndex = from + start.length
    val endIndex = if (end.isEmpty()) length else indexOf(end, startIndex).let { if (it == -1) length else it }
    return if (endIndex <= startIndex) "" else substring(startIndex, endIndex)
}
