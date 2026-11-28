package com.lichader.hipdanext.forum

/**
 * URLs of the forum's pages. They replace the static `HiUtils.*Url` fields one feature at a
 * time, and are built from [ForumConfig] on each call, so a server change applies at once.
 */
class HiPdaEndpoints(private val config: ForumConfig) {

    val baseUrl: String
        get() = config.forumServer + "/forum/"

    fun viewBlacklist(): String = baseUrl + "pm.php?action=viewblack"

    fun addToBlacklist(): String = baseUrl + "pm.php?action=addblack"

    fun removeFromBlacklist(): String = baseUrl + "pm.php?action=delblack"

    fun userProfile(uid: String): String = baseUrl + "space.php?uid=" + uid

    fun home(): String = baseUrl + "index.php"

    fun myReplies(page: Int): String = baseUrl + "my.php?item=posts&page=" + page

    fun myThreads(page: Int): String = baseUrl + "my.php?item=threads&page=" + page

    fun messages(): String = baseUrl + "pm.php?filter=privatepm"

    fun notices(): String = baseUrl + "notice.php"

    /** New posts of the last day; a [searchId] from the first page fetches that search's pages. */
    fun newPosts(searchId: String?, page: Int): String {
        var url = if (searchId.isNullOrEmpty()) baseUrl + "search.php?srchfrom=86400&searchsubmit=yes" else searchById(searchId)
        if (page > 1) url += "&page=" + page
        return url
    }

    private fun searchById(searchId: String): String =
        baseUrl + "search.php?searchid=" + searchId + "&orderby=lastpost&ascdesc=desc&searchsubmit=yes"

    /** `my.php` lists of saved threads; [item] is `favorites` or `attention`. */
    fun savedThreads(item: String, page: Int): String {
        var url = baseUrl + "my.php?item=" + item + "&type=thread"
        if (page > 1) url += "&page=" + page
        return url
    }

    fun threadList(forumId: Int, page: Int, typeId: String?, orderByPostTime: Boolean): String {
        var url = baseUrl + "forumdisplay.php?fid=" + forumId + "&page=" + page
        if (typeId != null) url += "&filter=type&typeid=" + typeId
        if (orderByPostTime) url += "&orderby=dateline"
        return url
    }

    fun loginForm(): String = baseUrl + "logging.php?action=login"

    fun loginSubmit(): String = baseUrl + "logging.php?action=login&loginsubmit=yes&inajax=1"
}
