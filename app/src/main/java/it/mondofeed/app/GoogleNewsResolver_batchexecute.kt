package it.mondofeed.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GoogleNewsResolver {

    private val cache = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!isGoogleNewsUrl(clean)) return@withContext clean

        cache[clean]?.let { return@withContext it }

        val articleId = extractArticleId(clean)
        val resolved = sequenceOf(
            extractQueryTarget(clean),
            decodeLegacyUrl(articleId),
            articleId?.let(::decodeWithBatchExecute),
            followRedirect(clean)
        )
            .filterNotNull()
            .map(::decodeEscapes)
            .firstOrNull(::isPublisherUrl)
            ?: clean

        cache[clean] = resolved
        resolved
    }

    fun isGoogleNewsUrl(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.endsWith("news.google.com") == true

    private fun extractArticleId(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val segments = parsed.pathSegments
        val marker = segments.indexOfLast {
            it == "articles" || it == "read"
        }
        return segments.getOrNull(marker + 1)?.takeIf { it.isNotBlank() }
    }

    private fun extractQueryTarget(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        for (key in listOf("url", "u", "q", "target", "dest", "destination")) {
            val value = parsed.queryParameter(key)?.trim().orEmpty()
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return runCatching { URLDecoder.decode(value, "UTF-8") }
                    .getOrDefault(value)
            }
        }
        return null
    }

    private fun decodeLegacyUrl(articleId: String?): String? {
        if (articleId.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(
                articleId,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val decoded = bytes.toString(Charsets.UTF_8)
            Regex("""https?://[^\u0000-\u0020"'<>\\]+""", RegexOption.IGNORE_CASE)
                .findAll(decoded)
                .map { it.value }
                .firstOrNull(::isPublisherUrl)
        }.getOrNull()
    }

    private fun decodeWithBatchExecute(articleId: String): String? {
        val requestPayload = """[[[\"Fbv4je\",\"[\\\"garturlreq\\\",[[\\\"it-IT\\\",\\\"IT\\\",[\\\"FINANCE_TOP_INDICES\\\",\\\"WEB_TEST_1_0_0\\\"],null,null,1,1,\\\"IT:it\\\",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],\\\"it-IT\\\",\\\"IT\\\",1,[2,3,4,8],1,0,\\\"655000234\\\",0,0,null,0],\\\"$articleId\\\"]\",null,\"generic\"]]]"""

        val body = FormBody.Builder()
            .add("f.req", requestPayload)
            .build()

        val request = Request.Builder()
            .url("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
            .post(body)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://news.google.com/")
            .header("Accept-Language", "it-IT,it;q=0.9")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string().orEmpty()
                extractBatchUrl(text)
            }
        }.getOrNull()
    }

    private fun extractBatchUrl(responseText: String): String? {
        val decoded = decodeEscapes(responseText)

        val marker = "[\"garturlres\",\""
        val start = decoded.indexOf(marker)
        if (start >= 0) {
            val valueStart = start + marker.length
            val valueEnd = decoded.indexOf("\",", valueStart)
            if (valueEnd > valueStart) {
                return decoded.substring(valueStart, valueEnd)
            }
        }

        return Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
            .findAll(decoded)
            .map { it.value }
            .firstOrNull(::isPublisherUrl)
    }

    private fun followRedirect(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Accept-Language", "it-IT,it;q=0.9")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                response.request.url.toString().takeIf(::isPublisherUrl)
            }
        }.getOrNull()
    }

    private fun isPublisherUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
        return (parsed.scheme == "http" || parsed.scheme == "https") &&
            !host.endsWith("google.com") &&
            !host.endsWith("googleusercontent.com") &&
            !host.endsWith("gstatic.com") &&
            !host.endsWith("doubleclick.net")
    }

    private fun decodeEscapes(value: String): String = value
        .replace("\\u003d", "=")
        .replace("\\u0026", "&")
        .replace("\\u003f", "?")
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
}
