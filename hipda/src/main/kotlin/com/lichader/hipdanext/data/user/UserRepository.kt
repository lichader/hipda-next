package com.lichader.hipdanext.data.user

import com.lichader.hipdanext.cache.SignatureContainer
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.user.UserApi
import com.lichader.hipdanext.forum.user.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/** A user's profile with what the app knows about them locally. */
data class UserInfo(
    val profile: UserProfile,
    /** The signature from the user's posts, if the app has seen one in a thread; profiles don't show it. */
    val signature: String?,
)

/** Users' profiles. Functions are main-safe and throw [ForumException] if the forum request fails. */
interface UserRepository {

    suspend fun info(uid: String): UserInfo
}

@Singleton
class DefaultUserRepository @Inject constructor(private val api: UserApi) : UserRepository {

    override suspend fun info(uid: String): UserInfo =
        UserInfo(api.profile(uid), SignatureContainer.getSignature(uid))
}
