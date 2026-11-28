package com.lichader.hipdanext.forum.thread

import com.lichader.hipdanext.forum.parser.PageHeader

/** A page of a thread, with the page header. */
data class ThreadPage(
    /** The thread's ID, from the request or, for a post's page, from the page itself. */
    val tid: String?,
    /** The forum the thread is in, or 0 if the page doesn't say. */
    val fid: Int,
    val title: String?,
    val page: Int,
    val lastPage: Int,
    val posts: List<Post>,
    /**
     * How many posts the account's setting puts on a page, worked out from this page's first post;
     * null if the page doesn't tell (a single page, or no first post).
     */
    val postsPerPage: Int?,
    val header: PageHeader,
)

data class Post(
    val pid: String,
    val floor: Int,
    val time: String,
    val uid: String,
    val author: String,
    /** The author has been warned for this post. */
    val warned: Boolean,
    /** The forum's status line, such as an edit note; null if none. */
    val status: String?,
    /** The author's signature as text; null if the post shows none. */
    val signature: String?,
    /** The thread's poll, on the first post. */
    val poll: Poll?,
    /** The post's content in order. */
    val parts: List<Part>,
)

/** Text styles from the tags around a run of post text. */
data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val smallFont: Boolean = false,
    /** Font colors as the tags give them, outermost first; an app shows the innermost it can read. */
    val colors: List<String> = emptyList(),
)

/** A piece of a post's content. Text keeps the forum's words; URLs are absolute where the forum's were relative. */
sealed interface Part {
    data class Text(val text: String, val style: RunStyle) : Part

    /** A line break, or the start of a table row. */
    object LineBreak : Part

    /** The start of a table cell. */
    object Space : Part

    data class Link(val text: String, val url: String, val smallFont: Boolean) : Part

    data class Email(val address: String) : Part

    /** One of the forum's smilies, shown inline. */
    data class Smiley(val src: String) : Part

    /** An image attached to the post; [size] is in bytes, or -1 or 0 if unknown. */
    data class AttachedImage(val url: String, val size: Long, val thumbUrl: String) : Part

    /** An image from another site. */
    data class ExternalImage(val src: String) : Part

    /** An image whose address the parser doesn't recognise. */
    data class UnknownImage(val src: String) : Part

    /** A file attached to the post; [description] is the forum's size and download note, if any. */
    data class Attachment(val url: String, val title: String, val description: String?) : Part

    /** "回复 N# author": a link to the post being answered. */
    data class FloorLink(val text: String, val tid: String, val pid: String, val floor: Int, val author: String) : Part

    /** A quoted post; [html] keeps line breaks, text styles and links only. */
    data class Quote(val html: String, val authorAndTime: String, val tid: String, val pid: String) : Part

    /** An embedded video; [youku] if [url] was turned into Youku's mobile page. */
    data class Video(val url: String, val youku: Boolean) : Part

    /** The text of a poll shown in place of the first post's message. */
    data class PollSummary(val info: String, val rows: List<String>) : Part

    /** A tag the parser doesn't handle; [html] is the node as the page has it. */
    data class Unparsed(val tag: String, val html: String) : Part

    /** The post is hidden from the account; [text] is the forum's notice. */
    data class Locked(val text: String) : Part

    /** The post has no message the parser can find, as when a moderator has hidden it. */
    object Missing : Part

    /** The post's message is empty. */
    object Empty : Part
}

data class Poll(
    /** The poll's description and, if any, its timer, as HTML. */
    val titleHtml: String,
    val formhash: String?,
    /** How many options may be chosen, for a multiple-choice poll; null otherwise. */
    val maxAnswer: Int?,
    val options: List<PollOption>,
    val footer: String?,
)

data class PollOption(val id: String?, val text: String, val rates: String?)
