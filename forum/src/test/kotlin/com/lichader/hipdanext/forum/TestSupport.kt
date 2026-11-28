package com.lichader.hipdanext.forum

import java.nio.charset.Charset

class TestForumConfig(
    override var forumServer: String = "https://forum.invalid",
    override var charset: Charset = Charset.forName("GBK"),
    override var userAgent: String = "hipda-next test",
    override var sessionKey: String? = "session-1",
) : ForumConfig

/** A fixture under `src/test/resources/fixtures/`, such as `blacklist/list.html`. */
fun fixture(path: String): String =
    requireNotNull(TestForumConfig::class.java.getResource("/fixtures/$path")) { "No fixture $path" }
        .readText(Charsets.UTF_8)
