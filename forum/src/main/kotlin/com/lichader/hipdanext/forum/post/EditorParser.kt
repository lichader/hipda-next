package com.lichader.hipdanext.forum.post

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.parser.Pages
import com.lichader.hipdanext.forum.parser.between
import org.jsoup.nodes.Document

/** Reads the forum's post editor (`post.php`), following the old `PrePostAsyncTask`. */
internal object EditorParser {

    /**
     * @param quoting whether the editor replies to a post, so its notice fields are read
     * @throws ForumException.Server if the forum refused, saying why
     * @throws ForumException.Parse if the page has no form
     */
    fun parse(doc: Document, quoting: Boolean): EditorPage {
        val formhash = doc.selectFirst("input[name=formhash]")?.attr("value")
            ?: throw Pages.errorMessage(doc)?.let { ForumException.Server(it) } ?: ForumException.Parse("No post form")
        val page = Builder(formhash)
        // As before, a missing text box, script or upload token ends the reading there.
        val textBox = doc.selectFirst("textarea") ?: return page.build()
        page.text = textBox.text()
        val script = doc.selectFirst("script") ?: return page.build()
        page.uid = script.data().between("discuz_uid = ", ",")
        page.hash = doc.selectFirst("input[name=hash]")?.attr("value") ?: return page.build()

        page.subject = doc.selectFirst("input[name=subject]")?.attr("value")
        page.deletable = doc.selectFirst("input#delete") != null
        page.maxUploadSize = doc.selectFirst("div.uploadinfo")?.text()?.let(::maxUploadSize)
        if (quoting) {
            page.noticeAuthor = doc.selectFirst("input[name=noticeauthor]")?.attr("value")
            page.noticeAuthorMsg = doc.selectFirst("input[name=noticeauthormsg]")?.attr("value")
            page.noticeTrimStr = doc.selectFirst("input[name=noticetrimstr]")?.attr("value")
        }

        for (img in doc.select("div#unusedimgattachlist table.imglist img")) {
            val id = img.attr("id")
            if ("attachments/" in img.attr("src") && "_" in id) {
                val imageId = id.substringAfterLast("_")
                if (isId(imageId)) page.images += imageId
            }
        }
        for (img in doc.select("div.upfilelist img[id^=image_]")) {
            val imageId = img.attr("id").substring("image_".length)
            if (isId(imageId)) page.images += imageId
        }
        for (link in doc.select("div.upfilelist span a")) {
            val onclick = link.attr("onclick")
            if (!link.attr("href").startsWith("javascript")) continue
            if (onclick.startsWith("insertAttachimgTag")) {
                // <a href="javascript:;" onclick="insertAttachimgTag('2810014')" ...>Hi_160723_2240.jpg</a>
                val imageId = onclick.between("insertAttachimgTag('", "'")
                if (isId(imageId)) page.images += imageId
            } else if (onclick.startsWith("insertAttachTag")) {
                val attachmentId = onclick.between("insertAttachTag('", "'")
                if (isId(attachmentId)) page.attachments += attachmentId
            }
        }

        doc.select("#typeid > option").forEachIndexed { i, option ->
            page.types[option.`val`()] = option.text()
            if (i == 0 || option.attr("selected") == "selected") page.typeId = option.`val`()
        }
        return page.build()
    }

    /** "文件尺寸: 小于 8MB ..." gives 8 MB, in bytes, if over 1 KB. */
    private fun maxUploadSize(info: String): Int? {
        if ("文件尺寸" !in info) return null
        val size = info.uppercase().between("小于", "B").trim()
        val bytes = try {
            val number = size.substring(0, size.length - 1).toFloat()
            when (size.last()) {
                'K' -> (number * 1024).toInt()
                'M' -> (number * 1024 * 1024).toInt()
                else -> 0
            }.takeIf { number > 0 } ?: 0
        } catch (e: RuntimeException) {
            0
        }
        return bytes.takeIf { it > 1024 }
    }

    private fun isId(text: String) = text.isNotEmpty() && text.all(Char::isDigit)

    private class Builder(val formhash: String) {
        var text: String? = null
        var uid: String? = null
        var hash: String? = null
        var subject: String? = null
        var deletable = false
        var maxUploadSize: Int? = null
        var noticeAuthor: String? = null
        var noticeAuthorMsg: String? = null
        var noticeTrimStr: String? = null
        val images = LinkedHashSet<String>()
        val attachments = LinkedHashSet<String>()
        val types = LinkedHashMap<String, String>()
        var typeId: String? = null

        fun build() = EditorPage(
            formhash, text, uid, hash, subject, deletable, maxUploadSize, noticeAuthor, noticeAuthorMsg,
            noticeTrimStr, images.toList(), attachments.toList(), types, typeId,
        )
    }
}
