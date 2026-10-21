package it.mondofeed.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject

class FeedHealthWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = RssRepository()
        val statuses = Catalog.load(applicationContext).filter { it.enabled }.map { repository.check(it) }
        val json = JSONArray()
        statuses.forEach { status ->
            json.put(JSONObject()
                .put("id", status.source.id)
                .put("name", status.source.name)
                .put("ok", status.ok)
                .put("message", status.message)
                .put("feedCount", status.source.allFeedUrls().size)
                .put("checkedAt", System.currentTimeMillis()))
        }
        applicationContext.getSharedPreferences("health", Context.MODE_PRIVATE)
            .edit().putString("last", json.toString()).apply()
        return if (statuses.any { it.ok }) Result.success() else Result.retry()
    }
}
