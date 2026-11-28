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

/** Where a post is sent. */
sealed interface PostTarget {

    /** A reply to the thread, or to one of its posts. */
    data class Reply(val tid: String) : PostTarget

    data class NewThread(val fid: Int, val typeId: String?) : PostTarget

    /** Changes, or with [PostForm.delete] deletes, post [pid] shown on [page]. */
    data class Edit(val fid: Int, val tid: String, val pid: String, val page: Int) : PostTarget

    data class Vote(val fid: Int, val tid: String) : PostTarget
}

/** What a post sends, from the editor ([EditorPage]) and what was written. */
class PostForm(
    val formhash: String,
    val message: String,
    /** A new thread's subject; an edit sends it when not empty. */
    val subject: String? = null,
    /** An edit's thread type, sent with a subject. */
    val typeId: String? = null,
    /** For an edit: delete the post. */
    val delete: Boolean = false,
    val newAttachments: List<String> = emptyList(),
    val deletedAttachments: List<String> = emptyList(),
    val pollAnswers: List<String> = emptyList(),
    /** For a reply to a post: notifies its author when not empty. */
    val noticeAuthor: String? = null,
    val noticeAuthorMsg: String? = null,
    val noticeTrimStr: String? = null,
)

/** The forum's answer to a post. */
sealed interface PostResult {

    /** Posted: the forum went on to thread [tid], whose page is [html]. */
    data class Posted(val tid: String, val html: String) : PostResult

    /** The deleted post was the thread's first, so the thread is gone. */
    object ThreadDeleted : PostResult

    /** Not posted; [message] is the forum's reason, if it gave one. */
    data class Refused(val message: String?) : PostResult
}

/** The forum's answer to an image upload. */
sealed interface ImageUpload {

    data class Uploaded(val imageId: String) : ImageUpload

    /** The forum answered as for an upload but gave no image ID. */
    data class NoImageId(val response: String) : ImageUpload

    /** The forum's answer was not an upload answer. */
    data class Unrecognized(val response: String) : ImageUpload
}
