package app.nutshell

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class App : Application() {
    companion object { const val CHANNEL = "feeds" }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_feeds), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val ctx = applicationContext
        thread { ContentBlocker.load(ctx); ContentBlocker.refreshIfStale(ctx) }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "feeds", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FeedWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}
