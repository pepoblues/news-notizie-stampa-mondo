package it.mondofeed.app

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class RssRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(source: FeedSource): List<Article> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(source.feedUrl)
            .header("User-Agent", "MondoFeed/0.4 (+Android RSS reader)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")

            response.body?.byteStream()?.use { input ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(input, null)
                }

                val output = mutableListOf<Article>()
                var inItem = false
                var tag = ""
                var title = ""
                var link = ""
                var description = ""
                var published = ""
                var imageUrl: String? = null

                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            tag = parser.name.lowercase(Locale.ROOT)

                            if (tag == "item" || tag == "entry") {
                                inItem = true
                                title = ""
                                link = ""
                                description = ""
                                published = ""
                                imageUrl = null
                            }

                            if (inItem && tag == "link") {
                                parser.getAttributeValue(null, "href")?.let { link = it }
                            }

                            if (
                                inItem &&
                                (
                                    tag.endsWith("media:content") ||
                                    tag.endsWith("media:thumbnail") ||
                                    tag == "thumbnail"
                                )
                            ) {
                                imageUrl = imageUrl ?: normalizeImageUrl(
                                    parser.getAttributeValue(null, "url")
                                )
                            }

                            if (inItem && tag == "enclosure") {
                                val candidate = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type").orEmpty()

                                if (type.startsWith("image/", true) || looksLikeImage(candidate)) {
                                    imageUrl = imageUrl ?: normalizeImageUrl(candidate)
                                }
                            }
                        }

                        XmlPullParser.TEXT -> if (inItem) {
                            when {
                                tag == "title" -> title += parser.text
                                tag == "link" && link.isBlank() -> link += parser.text
                                tag == "description" ||
                                    tag == "summary" ||
                                    tag == "content" ||
                                    tag.endsWith("content:encoded") -> description += parser.text

                                tag == "pubdate" ||
                                    tag == "published" ||
                                    tag == "updated" ||
                                    tag.endsWith("dc:date") ||
                                    tag == "date" -> published += parser.text
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            if (
                                parser.name.equals("item", true) ||
                                parser.name.equals("entry", true)
                            ) {
                                if (title.isNotBlank() && link.isNotBlank()) {
                                    val parsedDate = parseDate(published.trim())
                                    val finalImage = imageUrl
                                        ?: normalizeImageUrl(extractImageFromHtml(description))

                                    output += Article(
                                        title = title.trim(),
                                        link = link.trim(),
                                        description = description
                                            .replace(Regex("<[^>]*>"), " ")
                                            .replace(Regex("\\s+"), " ")
                                            .trim(),
                                        published = published.trim(),
                                        displayDateTime = parsedDate.first,
                                        publishedEpochMillis = parsedDate.second,
                                        imageUrl = finalImage,
                                        source = source.name,
                                        country = source.country,
                                        territory = source.territory
                                    )
                                }
                                inItem = false
                            }
                        }
                    }
                    parser.next()
                }

                output
                    .sortedByDescending { it.publishedEpochMillis }
                    .take(30)
            } ?: emptyList()
        }
    }

    suspend fun check(source: FeedSource) = runCatching {
        fetch(source)
        FeedStatus(source, true, "OK")
    }.getOrElse {
        FeedStatus(source, false, it.message ?: "Errore")
    }

    private fun extractImageFromHtml(html: String): String? {
        if (html.isBlank()) return null

        val src = Regex(
            """<img[^>]+src\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)

        return src?.replace("&amp;", "&")
    }

    private fun normalizeImageUrl(value: String?): String? {
        val clean = value
            ?.trim()
            ?.replace("&amp;", "&")
            ?: return null

        if (
            !clean.startsWith("https://", ignoreCase = true) &&
            !clean.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }

        val lower = clean.lowercase(Locale.ROOT)

        if (
            lower.contains("spacer.gif") ||
            lower.contains("tracking") ||
            lower.contains("pixel") ||
            lower.contains("1x1")
        ) {
            return null
        }

        return clean
    }

    private fun looksLikeImage(value: String?): Boolean {
        val clean = value
            ?.lowercase(Locale.ROOT)
            ?.substringBefore('?')
            ?: return false

        return clean.endsWith(".jpg") ||
            clean.endsWith(".jpeg") ||
            clean.endsWith(".png") ||
            clean.endsWith(".webp") ||
            clean.endsWith(".gif")
    }

    private fun parseDate(raw: String): Pair<String, Long> {
        if (raw.isBlank()) return "Data non disponibile" to 0L

        val instant = parseInstant(raw) ?: return raw.take(24) to 0L
        val local = instant.atZone(ZoneId.systemDefault())

        return local.format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALIAN)
        ) to instant.toEpochMilli()
    }

    private fun parseInstant(raw: String): Instant? {
        val parsers = listOf<(String) -> Instant>(
            { Instant.parse(it) },
            { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() },
            { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
            {
                ZonedDateTime.parse(
                    it,
                    DateTimeFormatter.ofPattern(
                        "EEE, dd MMM yyyy HH:mm:ss Z",
                        Locale.ENGLISH
                    )
                ).toInstant()
            },
            {
                LocalDateTime.parse(
                    it,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
                ).atZone(ZoneId.systemDefault()).toInstant()
            }
        )

        for (dateParser in parsers) {
            try {
                return dateParser(raw)
            } catch (_: Exception) {
                // Prova il formato successivo.
            }
        }
        return null
    }
}
