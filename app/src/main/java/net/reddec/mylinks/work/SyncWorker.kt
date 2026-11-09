package net.reddec.mylinks.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.reddec.mylinks.data.Db
import net.reddec.mylinks.data.Prefs
import net.reddec.mylinks.data.Store
import net.reddec.mylinks.data.SyncEngine
import net.reddec.mylinks.data.SyncOutcome
import java.util.concurrent.TimeUnit

/**
 * Background reconciliation. WorkManager without play-services runs on the platform
 * JobScheduler, so this works on de-Googled devices.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val store = Store(Db(applicationContext))
        return when (SyncEngine(prefs, store).run(force = false)) {
            is SyncOutcome.Ok -> Result.success()
            SyncOutcome.NotConfigured -> Result.success()
            SyncOutcome.NeedsLogin -> Result.success()
            is SyncOutcome.Failed -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "mylinks-periodic-sync"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
