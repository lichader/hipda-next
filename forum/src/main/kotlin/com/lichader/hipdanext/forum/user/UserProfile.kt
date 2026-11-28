package com.lichader.hipdanext.forum.user

/** A user's public profile, from `space.php?uid=`. */
data class UserProfile(
    val uid: String,
    val username: String,
    /** Whether the forum shows the user as online now; users who hide their status show as offline. */
    val online: Boolean,
    /** The avatar image's URL, or null if the page shows none. */
    val avatarUrl: String?,
    /** The profile's statistics, such as user group, registration date and post counts. */
    val sections: List<ProfileSection>,
)

/** A titled block of the profile, such as "用户组: 高级会员" and its lines, or "积分: 0". */
data class ProfileSection(val title: String, val lines: List<String>)
