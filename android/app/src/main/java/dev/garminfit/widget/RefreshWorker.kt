package dev.garminfit.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.garminfit.Repo
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (Repo.token(applicationContext) == null) return Result.success()
        return runCatching { Repo.fetchToday(applicationContext) }
            .fold(
                { Widgets.updateAll(applicationContext); Result.success() },
                { if (runAttemptCount < 3) Result.retry() else Result.failure() },
            )
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("today-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
