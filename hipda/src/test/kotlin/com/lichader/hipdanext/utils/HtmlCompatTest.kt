package com.lichader.hipdanext.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HtmlCompatTest {

    @Test
    fun fromHtmlStripsMarkup() {
        val text = HtmlCompat.fromHtml("确认删除<br><b>注意</b>").toString()
        assertEquals("确认删除\n注意", text)
    }
}
