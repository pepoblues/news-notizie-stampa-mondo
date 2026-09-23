package it.mondofeed.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GoogleNewsResolver {

    private data class DecodeParams(
        val id: String,
        val timestamp: Long,
        val signature: String
    )

    private val cache = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!isGoogleNewsUrl(clean)) return@withContext clean

        cache[clean]?.let { return@withContext it }

        val id = extractArticleId(clean)
        val resolved = sequenceOf(
            extractQueryTarget(clean),
            decodeLegacyUrl(id),
            id?.let(::fetchDecodeParams)?.let(::decodeModernUrl),
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
        val marker = segments.indexOfLast { it == "articles" || it == "read" }
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

    private fun fetchDecodeParams(articleId: String): DecodeParams? {
        val request = Request.Builder()
            .url("https://news.google.com/articles/$articleId?hl=it&gl=IT&ceid=IT:it")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string().orEmpty()

                val signature = Regex(
                    """data-n-a-sg=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(html)?.groupValues?.getOrNull(1)

                val timestamp = Regex(
                    """data-n-a-ts=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(html)?.groupValues?.getOrNull(1)?.toLongOrNull()

                if (signature.isNullOrBlank() || timestamp == null) null
                else DecodeParams(articleId, timestamp, signature)
            }
        }.getOrNull()
    }

    private fun decodeModernUrl(params: DecodeParams): String? {
        val innerRequest = JSONArray().apply {
            put("garturlreq")
            put(JSONArray().apply {
                put(JSONArray().apply {
                    put("X")
                    put("X")
                    put(JSONArray().apply { put("X"); put("X") })
                    put(null); put(null); put(1); put(1); put("IT:it")
                    put(null); put(1); put(null); put(null); put(null)
                    put(null); put(null); put(0); put(1)
                })
                put("X"); put("X"); put(1)
                put(JSONArray().apply { put(1); put(1); put(1) })
                put(1); put(1); put(null); put(0); put(0); put(null); put(0)
            })
            put(params.id)
            put(params.timestamp)
            put(params.signature)
        }.toString()

        val outerRequest = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONArray().apply {
                    put("Fbv4je")
                    put(innerRequest)
                    put(null)
                    put("generic")
                })
            })
        }.toString()

        val body = FormBody.Builder()
            .add("f.req", outerRequest)
            .build()

        val request = Request.Builder()
            .url("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
            .post(body)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Referer", "https://news.google.com/")
            .header("Accept-Language", "it-IT,it;q=0.9")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                extractPublisherFromBatch(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun extractPublisherFromBatch(raw: String): String? {
        val decoded = decodeEscapes(raw)

        val regexes = listOf(
            Regex("""garturlres[^\[]*\[?[^\"]*\"(https?://[^\"]+)""", RegexOption.IGNORE_CASE),
            Regex("""\["garturlres","(https?://[^"]+)""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
        )

        for (regex in regexes) {
            val value = regex.findAll(decoded)
                .map { match -> match.groupValues.getOrNull(1)?.ifBlank { match.value } ?: match.value }
                .firstOrNull(::isPublisherUrl)
            if (value != null) return value
        }
        return null
    }

    private fun followRedirect(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36"
            )
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
