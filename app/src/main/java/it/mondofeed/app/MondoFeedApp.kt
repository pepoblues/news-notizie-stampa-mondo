package it.mondofeed.app

import android.app.Application
import androidx.work.*
import java.util.concurrent.TimeUnit

class MondoFeedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<FeedHealthWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("feed-health", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
