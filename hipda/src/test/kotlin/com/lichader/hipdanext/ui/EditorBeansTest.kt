package com.lichader.hipdanext.ui

import com.lichader.hipdanext.async.PostHelper
import com.lichader.hipdanext.forum.post.EditorKind
import com.lichader.hipdanext.forum.post.EditorPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBeansTest {

    private val page = EditorPage(
        "a1b2c3d4", "[quote]内容 1[/quote]", "100001", "h", "主题 1", true, 2048, "r|200001|user_1", "内容 2", "[b]回复 [/b]",
        listOf("9000001", "9000002"), listOf("8000001"), linkedMapOf("0" to "分类", "9" to "聚会"), "9",
    )

    @Test
    fun eachModeOpensItsEditor() {
        assertEquals(EditorKind.Reply("3000001"), EditorBeans.kind(PostHelper.MODE_REPLY_THREAD, 2, "3000001", null))
        assertEquals(EditorKind.Reply("3000001"), EditorBeans.kind(PostHelper.MODE_QUICK_REPLY, 2, "3000001", null))
        assertEquals(EditorKind.ReplyToPost("3000001", "4000001"), EditorBeans.kind(PostHelper.MODE_REPLY_POST, 2, "3000001", "4000001"))
        assertEquals(EditorKind.Quote("3000001", "4000001"), EditorBeans.kind(PostHelper.MODE_QUOTE_POST, 2, "3000001", "4000001"))
        assertEquals(EditorKind.NewThread(2), EditorBeans.kind(PostHelper.MODE_NEW_THREAD, 2, null, null))
        assertEquals(EditorKind.Edit(2, "3000001", "4000001"), EditorBeans.kind(PostHelper.MODE_EDIT_POST, 2, "3000001", "4000001"))
    }

    @Test
    fun aReplyToAPostKeepsTheTextAsItsQuote() {
        val info = EditorBeans.of(page, PostHelper.MODE_QUOTE_POST)

        assertEquals(listOf("a1b2c3d4", "", "[quote]内容 1[/quote]", "100001", "h", "主题 1"), listOf(info.formhash, info.text, info.quoteText, info.uid, info.hash, info.subject))
        assertEquals(listOf("r|200001|user_1", "内容 2", "[b]回复 [/b]"), listOf(info.noticeAuthor, info.noticeAuthorMsg, info.noticeTrimStr))
        assertTrue(info.isDeleteable)
        assertEquals(listOf("9000001", "9000002"), info.allImages)
        assertEquals(listOf("8000001"), info.attaches)
        assertEquals(listOf("0", "9"), info.typeValues.keys.toList())
        assertEquals("9", info.typeId)
    }

    @Test
    fun otherwiseTheTextIsThePosts() {
        val info = EditorBeans.of(page, PostHelper.MODE_EDIT_POST)

        assertEquals(listOf("[quote]内容 1[/quote]", ""), listOf(info.text, info.quoteText))
        val noText = EditorBeans.of(EditorPage("f", null, null, null, null, false, null, null, null, null, emptyList(), emptyList(), emptyMap(), null), PostHelper.MODE_EDIT_POST)
        assertEquals(listOf("", ""), listOf(noText.text, noText.quoteText))
        assertNull(noText.typeId)
    }
}
