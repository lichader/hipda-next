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

    fun loginForm(): String = baseUrl + "logging.php?action=login"

    fun loginSubmit(): String = baseUrl + "logging.php?action=login&loginsubmit=yes&inajax=1"
}
