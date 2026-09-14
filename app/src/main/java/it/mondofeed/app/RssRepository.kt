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
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit

class RssRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(source: FeedSource): List<Article> = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url(source.feedUrl)
                .header("User-Agent", "MondoFeed/0.2 (+Android RSS reader)")
                .build()
        ).execute()
        if (!response.isSuccessful) error("HTTP ${response.code}")

        response.body?.byteStream()?.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            val output = mutableListOf<Article>()
            var inItem = false
            var tag = ""
            var title = ""
            var link = ""
            var description = ""
            var published = ""

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name.lowercase()
                        if (tag == "item" || tag == "entry") {
                            inItem = true
                            title = ""
                            link = ""
                            description = ""
                            published = ""
                        }
                        if (inItem && tag == "link" && parser.getAttributeValue(null, "href") != null) {
                            link = parser.getAttributeValue(null, "href")
                        }
                    }
                    XmlPullParser.TEXT -> if (inItem) {
                        when (tag) {
                            "title" -> title += parser.text
                            "link" -> if (link.isBlank()) link += parser.text
                            "description", "summary", "content", "content:encoded" -> description += parser.text
                            "pubdate", "published", "updated", "dc:date", "date" -> published += parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("item", true) || parser.name.equals("entry", true)) {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                val parsedDate = parseDate(published.trim())
                                output += Article(
                                    title = title.trim(),
                                    link = link.trim(),
                                    description = description.replace(Regex("<[^>]*>"), "").trim(),
                                    published = published.trim(),
                                    displayDateTime = parsedDate.first,
                                    publishedEpochMillis = parsedDate.second,
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
            output.sortedByDescending { it.publishedEpochMillis }.take(30)
        } ?: emptyList()
    }

    suspend fun check(source: FeedSource) = runCatching {
        fetch(source)
        FeedStatus(source, true, "OK")
    }.getOrElse { FeedStatus(source, false, it.message ?: "Errore") }

    private fun parseDate(raw: String): Pair<String, Long> {
        if (raw.isBlank()) return "Data non disponibile" to 0L
        val instant = parseInstant(raw) ?: return raw.take(24) to 0L
        val local = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALIAN)
        return local.format(formatter) to instant.toEpochMilli()
    }

    private fun parseInstant(raw: String): Instant? {
        val parsers = listOf<(String) -> Instant>(
            { Instant.parse(it) },
            { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() },
            { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
            { ZonedDateTime.parse(it, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toInstant() },
            { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant() }
        )
        for (parser in parsers) {
            try { return parser(raw) } catch (_: Exception) { }
        }
        return null
    }
}
