package it.mondofeed.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Catalog {
    fun load(context: Context): List<FeedSource> {
        val text = context.assets.open("feeds.json")
            .bufferedReader()
            .use { it.readText() }
        val array = JSONArray(text)
        return (0 until array.length())
            .map { parseSource(array.getJSONObject(it)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun parseSource(json: JSONObject): FeedSource {
        val feedUrl = json.optString("feedUrl")
            .trim()
            .takeIf { it.isNotBlank() }
        val feedUrls = json.optJSONArray("feedUrls")?.let { array ->
            (0 until array.length())
                .mapNotNull { index -> array.optString(index).trim().takeIf(String::isNotBlank) }
                .distinct()
        }.orEmpty()
        return FeedSource(
            id = json.getString("id"),
            name = json.getString("name"),
            country = json.getString("country"),
            territory = json.getString("territory"),
            language = json.getString("language"),
            category = json.getString("category"),
            feedUrl = feedUrl,
            feedUrls = feedUrls,
            websiteUrl = json.getString("websiteUrl"),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
