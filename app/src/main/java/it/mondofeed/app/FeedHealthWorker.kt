package it.mondofeed.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray

class FeedHealthWorker(ctx:Context,p:WorkerParameters):CoroutineWorker(ctx,p){
    override suspend fun doWork():Result{
        val repo=RssRepository(); val statuses=Catalog.load(applicationContext).filter{it.enabled}.map{repo.check(it)}
        val json=JSONArray(); statuses.forEach{s->json.put(org.json.JSONObject().put("id",s.source.id).put("ok",s.ok).put("message",s.message).put("checkedAt",System.currentTimeMillis()))}
        applicationContext.getSharedPreferences("health",Context.MODE_PRIVATE).edit().putString("last",json.toString()).apply()
        return if(statuses.any{it.ok}) Result.success() else Result.retry()
    }
}
