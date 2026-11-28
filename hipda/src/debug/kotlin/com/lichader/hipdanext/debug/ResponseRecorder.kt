package com.lichader.hipdanext.debug

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug builds only: saves the text responses the app receives (HTML pages, `inajax` XML
 * replies, JSON), so real forum pages can become test fixtures.
 *
 * It records only while [dir] exists. `scripts/forum-captures.sh start` creates the
 * directory on a device and `stop` deletes it. Each response body is saved as received, in
 * its original encoding, and `index.tsv` lists the file, method, status, content type and
 * URL. Requests are never recorded: no request bodies, so no passwords; no headers; no cookies.
 * See docs/capturing-forum-pages.md.
 */
class ResponseRecorder(
    private val dir: File,
    private val now: () -> Date = { Date() },
) : Interceptor {

    private val sequence = AtomicInteger()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!dir.isDirectory) return response
        val type = response.body?.contentType() ?: return response
        val extension = extensionFor(type) ?: return response
        try {
            record(response, type, extension)
        } catch (e: IOException) {
            // Recording is a debugging aid; it must never break a request.
        }
        return response
    }

    private fun record(response: Response, type: MediaType, extension: String) {
        // peekBody copies without consuming, so the app still reads the whole body.
        val bytes = response.peekBody(MAX_BYTES).bytes()
        val request = response.request
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now())
        val name = "%s-%04d-%s-%s.%s".format(
            Locale.ROOT, stamp, sequence.incrementAndGet(), request.method, slug(request.url.encodedPath, request.url.query), extension,
        )
        synchronized(this) {
            File(dir, name).writeBytes(bytes)
            File(dir, INDEX).appendText(
                listOf(name, request.method, response.code.toString(), type.toString(), request.url.toString())
                    .joinToString("\t", postfix = "\n")
            )
        }
    }

    companion object {
        const val DIR_NAME = "forum-captures"
        const val INDEX = "index.tsv"
        private const val MAX_BYTES = 4L * 1024 * 1024

        /** The file extension for a text response, or null for anything else, such as images. */
        fun extensionFor(type: MediaType): String? = when {
            type.subtype.contains("html") -> "html"
            type.subtype.contains("xml") -> "xml"
            type.subtype.contains("json") -> "json"
            type.type == "text" -> "txt"
            else -> null
        }

        /** A file-name-safe short form of the path and query, such as `forum_pm.php_action=viewblack`. */
        fun slug(path: String, query: String?): String {
            val raw = path.trim('/') + (query?.let { "_$it" } ?: "")
            return raw.replace(Regex("[^A-Za-z0-9._=-]+"), "_").take(80).ifEmpty { "root" }
        }
    }
}
