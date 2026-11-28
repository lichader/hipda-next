package com.lichader.hipdanext.forum.post

import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.FilePart
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.session.SessionGuard

/** The forum's post editor and image uploads. Main-safe; failures are [ForumException]s. */
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
}

class HiPdaPostApi(
    private val endpoints: HiPdaEndpoints,
    private val transport: ForumTransport,
    private val session: SessionGuard,
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

    /** "DISCUZUPLOAD|0|1721652|1" gives image 1721652. */
    internal fun imageUpload(response: String): ImageUpload {
        if ("DISCUZUPLOAD" !in response) return ImageUpload.Unrecognized(response)
        // Split as Java's String.split did, dropping empty parts at the end.
        val parts = response.split("|").dropLastWhile { it.isEmpty() }
        return if (parts.size < 3 || parts[2] == "0") ImageUpload.NoImageId(response) else ImageUpload.Uploaded(parts[2])
    }
}
