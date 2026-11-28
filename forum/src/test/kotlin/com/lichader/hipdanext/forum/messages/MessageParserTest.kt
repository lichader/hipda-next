package com.lichader.hipdanext.forum.messages

import com.lichader.hipdanext.forum.fixture
import com.lichader.hipdanext.forum.lists.Avatar
import com.lichader.hipdanext.forum.lists.ListItem
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageParserTest {

    @Test
    fun realConversation() {
        val messages = MessageParser.conversation(Jsoup.parse(fixture("messages/conversation-real.html")))

        assertEquals(
            listOf(ListItem(uid = "200001", author = "user_1", time = "2016-11-5 21:24", info = "消息 1", avatar = Avatar.OfUser("200001"))),
            messages,
        )
    }

    @Test
    fun theAccountsOwnMessagesTakeItsUidFromTheHeader() {
        val messages = MessageParser.conversation(Jsoup.parse(fixture("messages/conversation-synthetic.html")))

        assertEquals(listOf("user_1", "test_user", "user_1"), messages.map { it.author })
        assertEquals(listOf("200001", "100001", null), messages.map { it.uid })
        assertEquals(listOf(true, false, false), messages.map { it.isNew })
        // The message is HTML, as the conversation screen shows it, pretty-printed by Jsoup as before.
        assertEquals("消息 1\n<br>第二行 \n<a href=\"http://example.invalid/\">链接</a>", messages.first().info)
    }

    @Test
    fun aPageWithoutTheAccountHasNoMessages() {
        assertEquals(emptyList<ListItem>(), MessageParser.conversation(Jsoup.parse("<ul><li class=\"s_clear\"><p class=\"cite\"><cite>x</cite></p><div class=\"summary\">y</div></li></ul>")))
    }

    @Test
    fun repliesToASentMessage() {
        assertEquals(SendResult.Sent, MessageParser.sendResult(fixture("messages/send-sent-synthetic.xml")))
        assertEquals(SendResult.Refused("您不能给自己发消息，请修改。"), MessageParser.sendResult(fixture("messages/send-refused-real.xml")))
        assertEquals(SendResult.Refused(null), MessageParser.sendResult("<?xml version=\"1.0\"?><root><![CDATA[]]></root>"))
        assertEquals(SendResult.NoReply, MessageParser.sendResult(""))
    }
}
