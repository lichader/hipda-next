package com.lichader.hipdanext.ui

import com.lichader.hipdanext.bean.SimpleListItemBean
import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import com.lichader.hipdanext.glide.GlideHelper
import com.lichader.hipdanext.utils.HiUtils

/** The legacy adapter's [SimpleListItemBean]s for the simple lists, until they are redesigned. */
object ListBeans {

    @JvmStatic
    fun of(items: List<ListItem>): List<SimpleListItemBean> = items.map { item ->
        SimpleListItemBean().apply {
            tid = item.tid
            pid = item.pid
            uid = item.uid
            title = item.title
            author = item.author
            time = item.time
            forum = item.forum
            info = item.info
            isNew = item.isNew
            when (val avatar = item.avatar) {
                is Avatar.OfUser -> avatarUrl = HiUtils.getAvatarUrlByUid(avatar.uid)
                is Avatar.Url -> avatarUrl = avatar.url
                Avatar.System -> avatarUrl = GlideHelper.SYSTEM_AVATAR_FILE.absolutePath
                null -> {}
            }
        }
    }
}
