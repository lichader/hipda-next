package com.lichader.hipdanext.forum.site

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.http.ForumTransport

/** The site's own settings. Main-safe; throws [ForumException] on failure. */
interface SiteApi {

    /** The image host the site names in `config.php`, such as `https://img02.4d4y.com`, or null if it names none. */
    suspend fun imageHost(): String?
}

class HiPdaSiteApi(private val transport: ForumTransport, private val endpoints: HiPdaEndpoints) : SiteApi {

    override suspend fun imageHost(): String? {
        val json = transport.get(endpoints.siteConfig())
        val config: Map<String, String>? = try {
            Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
        } catch (e: JsonParseException) {
            throw ForumException.Parse("config.php is not a JSON object: ${e.message}")
        }
        return config?.get("CDN")
    }
}
