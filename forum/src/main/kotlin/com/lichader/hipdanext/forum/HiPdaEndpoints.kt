package com.lichader.hipdanext.forum

import java.net.URLEncoder

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

    fun newMessages(): String = baseUrl + "pm.php?filter=newpm"

    /** The site's settings, outside the forum's folder. */
    fun siteConfig(): String = config.forumServer + "/config.php"

    fun notices(): String = baseUrl + "notice.php"

    /** New posts of the last day; a [searchId] from the first page fetches that search's pages. */
    fun newPosts(searchId: String?, page: Int): String {
        var url = if (searchId.isNullOrEmpty()) baseUrl + "search.php?srchfrom=86400&searchsubmit=yes" else searchById(searchId)
        if (page > 1) url += "&page=" + page
        return url
    }

    /**
     * A search; each page repeats it with `page`. The terms are encoded as GBK whatever the
     * posting encoding is, as the old search did.
     */
    fun search(text: String, author: String, forumId: String, fullText: Boolean, page: Int): String {
        var url = baseUrl + "search.php?srchtype=" + (if (fullText) "fulltext" else "title") +
            "&srchtxt=" + URLEncoder.encode(text, "GBK") +
            "&searchsubmit=true&st=on&srchuname=" + URLEncoder.encode(author, "GBK") +
            "&srchfilter=all&srchfrom=0&before=&orderby=lastpost&ascdesc=desc&srchfid%5B0%5D=" + forumId
        if (page > 1) url += "&page=" + page
        return url
    }

    /** A user's threads; a [searchId] from the first page fetches that search's pages. */
    fun userThreads(uid: String, searchId: String?, page: Int): String {
        var url = if (searchId.isNullOrEmpty()) {
            baseUrl + "search.php?srchfid=all&srchfrom=0&searchsubmit=yes&srchuid=" + uid
        } else {
            searchById(searchId)
        }
        if (page > 1) url += "&page=" + page
        return url
    }

    private fun searchById(searchId: String): String =
        baseUrl + "search.php?searchid=" + searchId + "&orderby=lastpost&ascdesc=desc&searchsubmit=yes"

    fun addSavedThread(item: String, tid: String): String =
        baseUrl + "my.php?item=" + item + "&action=add&inajax=1&ajaxtarget=favorite_msg&tid=" + tid

    fun removeSavedThread(item: String, tid: String): String =
        baseUrl + "my.php?item=" + item + "&action=remove&inajax=1&ajaxtarget=favorite_msg&tid=" + tid

    /** `my.php` lists of saved threads; [item] is `favorites` or `attention`. */
    fun savedThreads(item: String, page: Int): String {
        var url = baseUrl + "my.php?item=" + item + "&type=thread"
        if (page > 1) url += "&page=" + page
        return url
    }

    fun conversation(uid: String): String = baseUrl + "pm.php?daterange=5&uid=" + uid

    /** The message form, whose token the old send read; [uid] is empty for a message to a username. */
    fun messageForm(uid: String): String = baseUrl + "pm.php?daterange=1&uid=" + uid

    /** Sends a message to [uid], or, with null, to the username in the form's `msgto`. */
    fun sendMessage(uid: String?): String =
        baseUrl + "pm.php?action=send&pmsubmit=yes&infloat=yes&inajax=1" + (if (uid != null) "&uid=$uid" else "")

    fun clearConversation(uid: String): String = baseUrl + "pm.php?action=del&uid=" + uid + "&filter=privatepm"

    fun threadList(forumId: Int, page: Int, typeId: String?, orderByPostTime: Boolean): String {
        var url = baseUrl + "forumdisplay.php?fid=" + forumId + "&page=" + page
        if (typeId != null) url += "&filter=type&typeid=" + typeId
        if (orderByPostTime) url += "&orderby=dateline"
        return url
    }

    fun loginForm(): String = baseUrl + "logging.php?action=login"

    fun loginSubmit(): String = baseUrl + "logging.php?action=login&loginsubmit=yes&inajax=1"
}
