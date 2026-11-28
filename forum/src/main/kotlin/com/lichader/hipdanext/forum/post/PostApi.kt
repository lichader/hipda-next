package com.lichader.hipdanext.forum.post

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.FilePart
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.parser.between
import com.lichader.hipdanext.forum.session.SessionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** The forum's post editor, image uploads and posting. Main-safe; failures are [ForumException]s. */
interface PostApi {

    /**
     * The editor of [kind].
     *
     * @throws ForumException.RealNameRequired if the forum asks for real-name verification first
     * @throws ForumException.Server if the forum refused, saying why
     * @throws ForumException.Parse if the page has no post form
     */
    suspend fun editor(kind: EditorKind): EditorPage

    /** Uploads an image with the editor's [uid] and upload [hash]. */
    suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload

    /** Sends a post. It is sent once: a logged-out answer is [PostResult.Refused], not retried. */
    suspend fun submit(target: PostTarget, form: PostForm): PostResult
}

class HiPdaPostApi(
    private val endpoints: HiPdaEndpoints,
    private val transport: ForumTransport,
    private val session: SessionGuard,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PostApi {

    override suspend fun editor(kind: EditorKind): EditorPage {
        val page = session.served(endpoints.editor(kind))
        if ("memcp.php?action=bind" in page.url) throw ForumException.RealNameRequired()
        return EditorParser.parse(page.doc, quoting = kind is EditorKind.ReplyToPost || kind is EditorKind.Quote)
    }

    override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload {
        val response = transport.upload(
            endpoints.uploadImage(),
            mapOf("uid" to uid, "hash" to hash),
            FilePart("Filedata", fileName, mimeType, bytes),
        )
        return imageUpload(response)
    }

    override suspend fun submit(target: PostTarget, form: PostForm): PostResult {
        val response = transport.send(endpoints.submit(target), fields(target, form))
        // The forum answers a post by going on to the thread, or to the forum when a thread was deleted.
        val url = response.url
        if (form.delete && "forumdisplay.php" in url) return PostResult.ThreadDeleted
        val tid = url.between("tid=", "&")
        if (("viewthread.php" in url || "redirect.php" in url) && (tid.trim().toIntOrNull() ?: 0) > 0) {
            return PostResult.Posted(tid, response.body)
        }
        val reason = withContext(parseDispatcher) { Jsoup.parse(response.body).select("div.alert_info") }
        return PostResult.Refused(if (reason.isEmpty()) null else reason.text())
    }

    /** The form, as the old `PostHelper` sent it. */
    private fun fields(target: PostTarget, form: PostForm): List<Pair<String, String>> {
        val fields = mutableListOf(
            "formhash" to form.formhash,
            "posttime" to System.currentTimeMillis().toString(),
            "wysiwyg" to "0",
            "usesig" to "1",
            "message" to form.message,
        )
        if (target is PostTarget.Edit && form.delete) fields += "delete" to "1"
        form.newAttachments.forEach { fields += "attachnew[][description]" to it }
        form.deletedAttachments.forEach { fields += "attachdel[]" to it }
        when (target) {
            is PostTarget.NewThread -> {
                fields += "subject" to form.subject.orEmpty()
                fields += "attention_add" to "1"
            }
            is PostTarget.Edit -> if (!form.subject.isNullOrEmpty()) {
                fields += "subject" to form.subject
                if (!form.typeId.isNullOrEmpty()) fields += "typeid" to form.typeId
            }
            is PostTarget.Vote -> form.pollAnswers.sorted().forEach { fields += "pollanswers[]" to it }
            is PostTarget.Reply -> Unit
        }
        if (!form.noticeAuthor.isNullOrEmpty()) {
            fields += "noticeauthor" to form.noticeAuthor
            fields += "noticeauthormsg" to form.noticeAuthorMsg.orEmpty()
            fields += "noticetrimstr" to form.noticeTrimStr.orEmpty()
        }
        return fields
    }

    /** "DISCUZUPLOAD|0|1721652|1" gives image 1721652. */
    internal fun imageUpload(response: String): ImageUpload {
        if ("DISCUZUPLOAD" !in response) return ImageUpload.Unrecognized(response)
        // Split as Java's String.split did, dropping empty parts at the end.
        val parts = response.split("|").dropLastWhile { it.isEmpty() }
        return if (parts.size < 3 || parts[2] == "0") ImageUpload.NoImageId(response) else ImageUpload.Uploaded(parts[2])
    }
}
