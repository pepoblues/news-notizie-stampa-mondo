package it.mondofeed.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Risolve, quando possibile, un collegamento intermedio di Google News
 * nell'indirizzo originale dell'editore.
 *
 * Non blocca mai l'apertura: se non trova un indirizzo migliore restituisce
 * quello ricevuto, che verrà gestito dal browser interno.
 */
object GoogleNewsResolver {

    private val resolvedCache = ConcurrentHashMap<String, String>()

    private val cookieJar = object : CookieJar {
        private val cookies = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookiesToSave: List<Cookie>) {
            cookies[url.host] = cookiesToSave
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val saved = cookies[url.host].orEmpty().toMutableList()

            if (url.host.endsWith("google.com") && saved.none { it.name == "CONSENT" }) {
                saved += Cookie.Builder()
                    .name("CONSENT")
                    .value("YES+cb.20210720-07-p0.it+FX+410")
                    .domain(".google.com")
                    .path("/")
                    .secure()
                    .build()
            }
            return saved
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!isGoogleNewsUrl(cleanUrl)) return@withContext cleanUrl

        resolvedCache[cleanUrl]?.let { return@withContext it }

        val candidates = sequenceOf(
            extractQueryTarget(cleanUrl),
            decodeLegacyArticleUrl(cleanUrl),
            resolveThroughNetwork(cleanUrl)
        )

        val resolved = candidates
            .filterNotNull()
            .map(::decodeHtmlEntities)
            .firstOrNull(::isPublisherUrl)
            ?: cleanUrl

        resolvedCache[cleanUrl] = resolved
        resolved
    }

    fun isGoogleNewsUrl(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.endsWith("news.google.com") == true

    private fun extractQueryTarget(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val keys = listOf("url", "u", "q", "target", "dest", "destination")

        for (key in keys) {
            val value = parsed.queryParameter(key)?.trim().orEmpty()
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            }
        }
        return null
    }

    private fun decodeLegacyArticleUrl(url: String): String? {
        val encoded = url
            .substringAfter("/articles/", "")
            .substringBefore('?')
            .substringBefore('#')
            .trim()

        if (encoded.isBlank()) return null

        return runCatching {
            val bytes = Base64.decode(
                encoded,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val text = bytes.toString(Charsets.UTF_8)

            Regex("""https?://[^\u0000-\u0020"'<>\\]+""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .map { it.value }
                .firstOrNull(::isPublisherUrl)
        }.getOrNull()
    }

    private fun resolveThroughNetwork(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (isPublisherUrl(finalUrl)) return@use finalUrl

                val html = response.body?.string().orEmpty()
                extractPublisherUrlFromHtml(html)
            }
        }.getOrNull()
    }

    private fun extractPublisherUrlFromHtml(html: String): String? {
        if (html.isBlank()) return null

        val decoded = decodeHtmlEntities(html)
        val patterns = listOf(
            Regex("""<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            for (match in pattern.findAll(decoded)) {
                val value = match.groupValues.getOrNull(1)?.ifBlank { match.value }
                    ?: match.value
                if (isPublisherUrl(value)) return value
            }
        }
        return null
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

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\u003d", "=")
        .replace("\\u0026", "&")
        .replace("\\/", "/")
}
