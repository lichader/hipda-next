package com.lichader.hipdanext.forum.http

import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.forum.ForumException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends forum requests and returns the decoded response body.
 *
 * The functions are main-safe. OkHttp runs the call and reads the body on its own threads.
 * Cancelling the calling coroutine cancels the HTTP call. All failures are reported as
 * [ForumException].
 */
class ForumTransport(
    private val client: OkHttpClient,
    private val config: ForumConfig,
) {

    suspend fun get(url: String): String = execute(newRequest(url).get().build())

    /**
     * Posts [form] as `application/x-www-form-urlencoded`. Values are percent-encoded in
     * [ForumConfig.charset] (the forum expects GBK by default); names are sent as given.
     */
    suspend fun post(url: String, form: Map<String, String>): String {
        // URLEncoder.encode(String, Charset) needs API 33; the String overload exists on API 21.
        val charsetName = config.charset.name()
        val body = FormBody.Builder().apply {
            for ((name, value) in form) {
                addEncoded(name, URLEncoder.encode(value, charsetName))
            }
        }.build()
        return execute(newRequest(url).post(body).build())
    }

    private fun newRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", config.userAgent)
            .cacheControl(CacheControl.FORCE_NETWORK)

    private suspend fun execute(request: Request): String {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(ForumException.from(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    // The body is read here, on OkHttp's thread, so the response is always
                    // closed, even when the coroutine was cancelled in the meantime.
                    val body = try {
                        response.use(::decode)
                    } catch (e: Throwable) {
                        // Anything else must still resume the caller, or it would wait forever.
                        continuation.resumeWithException(if (e is IOException) ForumException.from(e) else e)
                        return
                    }
                    continuation.resume(body)
                }
            })
        }
    }

    private fun decode(response: Response): String {
        if (!response.isSuccessful) {
            throw ForumException.Http(response.code, response.message)
        }
        val contentType = response.header("Content-Type").orEmpty().uppercase()
        val charset = when {
            "UTF" in contentType -> Charsets.UTF_8
            "GBK" in contentType -> GBK
            else -> config.charset
        }
        return String(response.body!!.bytes(), charset)
    }

    private companion object {
        val GBK: Charset = Charset.forName("GBK")
    }
}
