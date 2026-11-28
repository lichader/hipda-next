package com.lichader.hipdanext.ui

import com.lichader.hipdanext.bean.ContentImg
import com.lichader.hipdanext.bean.DetailBean
import com.lichader.hipdanext.bean.DetailListBean
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.bean.PollBean
import com.lichader.hipdanext.bean.PollOptionBean
import com.lichader.hipdanext.cache.SignatureContainer
import com.lichader.hipdanext.cache.SmallImages
import com.lichader.hipdanext.forum.thread.Part
import com.lichader.hipdanext.forum.thread.Poll
import com.lichader.hipdanext.forum.thread.Post
import com.lichader.hipdanext.forum.thread.RunStyle
import com.lichader.hipdanext.forum.thread.ThreadPage
import com.lichader.hipdanext.ui.textstyle.TextStyle
import com.lichader.hipdanext.utils.HiUtils
import com.lichader.hipdanext.utils.Logger
import com.vdurmont.emoji.EmojiParser

/**
 * Renders a thread page into the legacy adapter's beans, with the HTML the old parser built while
 * it parsed. The choices that depend on the app stay here: readable text colors, the blacklist,
 * the app's small images, the signature cache and the error report mode.
 */
object ThreadDetailBeans {

    @JvmStatic
    fun of(page: ThreadPage, settings: HiSettingsHelper): DetailListBean = DetailListBean().apply {
        setPage(page.page)
        setLastPage(page.lastPage)
        page.tid?.let { setTid(it) }
        setFid(page.fid)
        page.title?.let { setTitle(EmojiParser.parseToUnicode(it)) }
        // Each post is complete before it is added: adding numbers its images.
        page.posts.forEach { add(post(it, page.page, settings)) }
    }

    private fun post(post: Post, page: Int, settings: HiSettingsHelper): DetailBean = DetailBean().apply {
        setPage(page)
        postId = post.pid
        timePost = post.time
        floor = post.floor
        isWarned = post.warned
        uid = post.uid
        author = post.author
        if (settings.isInBlacklist(post.author)) {
            author = "[[黑名单用户]]"
            return@apply
        }
        avatarUrl = HiUtils.getAvatarUrlByUid(post.uid)
        post.signature?.let { SignatureContainer.putSignature(post.uid, it) }
        post.poll?.let { poll = pollBean(it) }
        post.status?.let { postStatus = it }
        post.parts.forEach { render(it, contents, settings) }
    }

    private fun render(part: Part, contents: DetailBean.Contents, settings: HiSettingsHelper) {
        when (part) {
            is Part.Text -> contents.addText(part.text.replace("<", "&lt;").replace(">", "&gt;"), textStyle(part.style))
            Part.LineBreak -> contents.addText("<br>")
            Part.Space -> contents.addText(" ")
            is Part.Link -> contents.addLink(part.text, part.url, part.smallFont)
            is Part.Email -> contents.addEmail(part.address)
            is Part.Smiley -> contents.addText("<img src=\"" + part.src + "\"/>")
            is Part.AttachedImage -> contents.addImg(ContentImg(part.url, part.size, part.thumbUrl))
            is Part.ExternalImage ->
                if (SmallImages.contains(part.src)) contents.addText("<img src=\"" + part.src + "\"/>") else contents.addImg(part.src)
            is Part.UnknownImage -> contents.addNotice("[[ERROR:UNPARSED IMG:" + part.src + "]]")
            is Part.Attachment -> contents.addAttach(part.url, part.title, part.description)
            is Part.FloorLink -> contents.addGoToFloor(part.text, part.tid, part.pid, part.floor, part.author)
            is Part.Quote -> contents.addQuote(part.html, part.authorAndTime, part.tid, part.pid)
            is Part.Video -> contents.addLink(
                (if (part.youku) "YouKu视频自动转换手机通道 " else "FLASH VIDEO,手机可能不支持 ") + part.url, part.url, false,
            )
            is Part.PollSummary -> contents.addText(pollSummary(part))
            is Part.Unparsed -> if (settings.isErrorReportMode) {
                contents.addNotice("[[ERROR:UNPARSED TAG:" + part.tag + ":" + part.html + "]]")
                Logger.e("[[ERROR:UNPARSED TAG:" + part.tag + "]]")
            }
            is Part.Locked -> contents.addNotice(part.text)
            Part.Missing -> contents.addNotice("[[!!找不到帖子内容，可能是该帖被管理员或版主屏蔽!!]]")
            Part.Empty -> contents.addNotice("[[无内容]]")
        }
    }

    /** The app's style for a run; of its font colors, the innermost readable one wins, as before. */
    private fun textStyle(style: RunStyle) = TextStyle().apply {
        isBold = style.bold
        isItalic = style.italic
        isUnderline = style.underline
        isStrike = style.strike
        isSmallFont = style.smallFont
        style.colors.forEach { setColor(it) }
    }

    private fun pollSummary(summary: Part.PollSummary): String {
        val sb = StringBuilder(summary.info).append("<br>")
        summary.rows.forEachIndexed { j, row ->
            if (j % 2 == 0 && j < summary.rows.size - 1) sb.append(row)
            if (j % 2 == 1) sb.append(row).append("<br>")
        }
        return sb.append("<br>").toString()
    }

    private fun pollBean(poll: Poll) = PollBean().apply {
        title = poll.titleHtml
        poll.formhash?.let { formhash = it }
        poll.maxAnswer?.let { maxAnswer = it }
        pollOptions = poll.options.map { option ->
            PollOptionBean().apply {
                option.id?.let { optionId = it }
                text = option.text
                option.rates?.let { rates = it }
            }
        }
        poll.footer?.let { footer = it }
    }
}
