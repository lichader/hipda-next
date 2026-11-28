package com.lichader.hipdanext.forum.user

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.parser.between
import org.jsoup.nodes.Document

/** Parses a user's profile page (`space.php?uid=`). Same rules as the app's old `HiParser.parseUserInfo`. */
internal object UserProfileParser {

    /** How many titled blocks the profile shows: the user group and the credits. */
    private const val SECTION_COUNT = 2

    /**
     * @throws ForumException.Server if the forum shows an error instead of the profile
     * @throws ForumException.Parse if the page has no profile
     */
    fun parse(doc: Document): UserProfile {
        Pages.errorMessage(doc)?.let { throw ForumException.Server(it) }
        val title = doc.selectFirst("div#profilecontent div.itemtitle")
            ?: throw ForumException.Parse("No div#profilecontent on the profile page")

        val username = title.selectFirst("h1")?.text()?.trim().orEmpty()
        val online = title.selectFirst("img")?.attr("src")?.contains("online") == true
        val uid = title.selectFirst("ul li")?.text()?.between("(UID:", ")")?.trim().orEmpty()
        val avatarUrl = doc.selectFirst("div.side div.profile_side div.avatar img")?.attr("src")
            ?.takeIf { it.isNotEmpty() }

        // The first block (user group) lists the statistics; the second (credits) only has its title.
        val statistics = doc.select("div.main div.s_clear ul.commonlist li").map { it.text() }
        val sections = doc.select("h3.blocktitle").take(SECTION_COUNT).mapIndexed { index, block ->
            ProfileSection(block.text(), if (index == 0) statistics else emptyList())
        }
        return UserProfile(uid, username, online, avatarUrl, sections)
    }
}
