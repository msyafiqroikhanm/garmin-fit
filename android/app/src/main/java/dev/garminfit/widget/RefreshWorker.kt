package dev.garminfit.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.garminfit.Repo
import java.util.concurrent.TimeUnit

/** Every 2 hours, or right away from the Plan widget's sync button ([MANUAL]: pull Garmin first, no retries). */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manual = inputData.getBoolean(MANUAL, false)
        try {
            if (Repo.token(ctx) == null) return Result.success()
            return runCatching {
                // only the owner's account can pull from Garmin; a viewer just reloads
                if (manual && Repo.cachedToday(ctx)?.role == "owner") runCatching { Repo.api(ctx).sync() }
                Repo.fetchToday(ctx)
                runCatching { Repo.fetchPlan(ctx) }
            }.fold(
                { Widgets.updateAll(ctx); Result.success() },
                { if (!manual && runAttemptCount < 3) Result.retry() else Result.failure() },
            )
        } finally {
            // a failed sync still has to stop the widget saying "Syncing", even when its session has closed
            if (manual) { Repo.setWidgetSyncing(ctx, false); PlanWidget().updateAll(ctx) }
        }
    }

    companion object {
        private const val MANUAL = "manual"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("today-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** No network constraint: offline it fails at once and the widget stops saying "Syncing". */
        fun now(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(workDataOf(MANUAL to true)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("widget-sync", ExistingWorkPolicy.KEEP, req)
        }
    }
}
