package com.lichader.hipdanext.forum.threads

import com.lichader.hipdanext.forum.parser.PageHeader

/** One row of a forum's thread list. Counts and times are the forum's text, as before. */
data class ThreadSummary(
    val tid: String,
    val title: String,
    /** The title's CSS color, such as "#EE1B2E", or null for the default color. */
    val titleColor: String?,
    /** The thread type shown before the title, such as "手机", or null. */
    val type: String?,
    val isNew: Boolean,
    val sticky: Boolean,
    val poll: Boolean,
    val author: String,
    val authorId: String,
    val timeCreate: String,
    val timeUpdate: String?,
    val replyCount: String,
    val viewCount: String,
    /** Who posted last. */
    val lastPost: String,
    val withPictures: Boolean,
    val maxPage: Int,
)

/** A page of a forum's thread list, with the page header. */
data class ThreadListPage(val threads: List<ThreadSummary>, val header: PageHeader)
