package com.lichader.hipdanext.forum.post

/** Which of the forum's post editors to open. */
sealed interface EditorKind {

    data class Reply(val tid: String) : EditorKind

    /** A reply to post [pid], which notifies its author. */
    data class ReplyToPost(val tid: String, val pid: String) : EditorKind

    /** A reply quoting post [pid], which notifies its author. */
    data class Quote(val tid: String, val pid: String) : EditorKind

    data class NewThread(val fid: Int) : EditorKind

    data class Edit(val fid: Int, val tid: String, val pid: String) : EditorKind
}

/**
 * The forum's post editor: the form's tokens and what it already holds. Fields the page does not
 * have are null or empty; the forum leaves out everything after the text box when there is none.
 */
class EditorPage(
    val formhash: String,
    /** The text box's content: the post being edited, or for a reply to a post, its quote. */
    val text: String?,
    /** The account's uid, which image uploads need. */
    val uid: String?,
    /** The image upload token. */
    val hash: String?,
    val subject: String?,
    /** Whether the edited post can be deleted. */
    val deletable: Boolean,
    /** The largest file the forum accepts, in bytes, if the page says and it is over 1 KB. */
    val maxUploadSize: Int?,
    /** For a reply to a post: the fields that notify its author. */
    val noticeAuthor: String?,
    val noticeAuthorMsg: String?,
    val noticeTrimStr: String?,
    /** IDs of the account's images the post can use: unused uploads first, then the post's own. */
    val images: List<String>,
    /** IDs of the post's other attachments. */
    val attachments: List<String>,
    /** The forum's thread types, by ID, in the page's order. */
    val types: Map<String, String>,
    /** The selected thread type, or the first. */
    val typeId: String?,
)

/** The forum's answer to an image upload. */
sealed interface ImageUpload {

    data class Uploaded(val imageId: String) : ImageUpload

    /** The forum answered as for an upload but gave no image ID. */
    data class NoImageId(val response: String) : ImageUpload

    /** The forum's answer was not an upload answer. */
    data class Unrecognized(val response: String) : ImageUpload
}
