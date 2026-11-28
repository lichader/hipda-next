package com.lichader.hipdanext.forum.lists

import com.lichader.hipdanext.forum.parser.PageHeader

/** Whose avatar a list row shows. The app turns it into an image. */
sealed interface Avatar {
    /** The avatar of the user with this uid. */
    data class OfUser(val uid: String) : Avatar

    /** An image the page itself links to. */
    data class Url(val url: String) : Avatar

    /** The app's avatar for system notices. */
    object System : Avatar
}

/**
 * One row of a simple list: a thread, a reply, a message or a notice. Fields a list doesn't
 * have are null. Text is the forum's, as before; a notice's [info] can hold `<u>` and `<br>`.
 */
data class ListItem(
    val tid: String? = null,
    val pid: String? = null,
    val uid: String? = null,
    val title: String? = null,
    val author: String? = null,
    val time: String? = null,
    val forum: String? = null,
    val info: String? = null,
    val isNew: Boolean = false,
    val avatar: Avatar? = null,
)

/** A page of a simple list, with the page header. */
data class ListPage(
    val items: List<ListItem>,
    /** The last page number the page links to; 1 for a single page. */
    val maxPage: Int,
    /** The search the page belongs to, for its following pages; null if it isn't a search. */
    val searchId: String?,
    val header: PageHeader,
)

/** A search's terms, as the search form takes them. */
data class SearchQuery(
    /** Words in the title, or in the post with [fullText]. */
    val text: String,
    /** The author's username, or empty for any author. */
    val author: String,
    /** The forum to search, or `0` for all forums. */
    val forumId: String,
    val fullText: Boolean,
)

/** The two lists of `my.php` that hold saved threads. */
enum class SavedThreads(
    /** The forum's name for the list, as `my.php?item=` takes it. */
    val item: String,
) {
    FAVORITES("favorites"),
    ATTENTION("attention"),
}
