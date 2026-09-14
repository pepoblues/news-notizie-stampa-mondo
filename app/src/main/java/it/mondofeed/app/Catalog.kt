package it.mondofeed.app

import android.content.Context
import org.json.JSONArray

object Catalog {
    fun load(context: Context): List<FeedSource> {
        val text=context.assets.open("feeds.json").bufferedReader().use { it.readText() }
        val a=JSONArray(text)
        return (0 until a.length()).map { i -> a.getJSONObject(i).run {
            FeedSource(getString("id"),getString("name"),getString("country"),getString("territory"),getString("language"),getString("category"),getString("feedUrl"),getString("websiteUrl"),optBoolean("enabled",true))
        }}
    }
}
