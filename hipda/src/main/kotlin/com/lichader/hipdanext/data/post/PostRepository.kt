package com.lichader.hipdanext.data.post

import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.account.AccountChangedException
import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import com.lichader.hipdanext.forum.post.ImageUpload
import com.lichader.hipdanext.forum.post.PostApi
import com.lichader.hipdanext.forum.post.PostForm
import com.lichader.hipdanext.forum.post.PostResult
import com.lichader.hipdanext.forum.post.PostTarget
import javax.inject.Inject
import javax.inject.Singleton

/** The post editor, image uploads and posting. Main-safe; throws [ForumException] if a request fails. */
interface PostRepository {

    /**
     * The editor of [kind]; remembers the largest upload the forum accepts.
     *
     * @throws AccountChangedException if the account changed meanwhile
     */
    suspend fun editor(kind: EditorKind): EditorPage

    suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray): ImageUpload

    /** Sends a post, once. */
    suspend fun submit(target: PostTarget, form: PostForm): PostResult
}

@Singleton
class DefaultPostRepository @Inject constructor(
    private val api: PostApi,
    private val settings: HiSettingsHelper,
    private val account: AccountRepository,
) : PostRepository {

    override suspend fun editor(kind: EditorKind): EditorPage {
        val generation = account.generation.value
        val page = api.editor(kind)
        if (account.generation.value != generation) throw AccountChangedException()
        page.maxUploadSize?.let { settings.setMaxUploadFileSize(it) }
        return page
    }

    override suspend fun uploadImage(uid: String, hash: String, fileName: String, mimeType: String, bytes: ByteArray) =
        api.uploadImage(uid, hash, fileName, mimeType, bytes)

    override suspend fun submit(target: PostTarget, form: PostForm) = api.submit(target, form)
}
