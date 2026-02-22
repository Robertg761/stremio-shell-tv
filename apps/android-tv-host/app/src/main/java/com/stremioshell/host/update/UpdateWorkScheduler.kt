package com.stremioshell.host.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.stremioshell.host.BuildConfig
import java.util.concurrent.TimeUnit

object UpdateWorkScheduler {
  private const val UNIQUE_WORK_NAME = "stremio-shell-update-hourly"

  fun ensureScheduled(context: Context) {
    if (BuildConfig.DEBUG) {
      return
    }

    val owner = BuildConfig.GITHUB_RELEASE_OWNER.trim()
    val repo = BuildConfig.GITHUB_RELEASE_REPO.trim()
    if (owner.isBlank() || repo.isBlank()) {
      return
    }

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val request = PeriodicWorkRequestBuilder<BackgroundUpdateWorker>(1, TimeUnit.HOURS)
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request
    )
  }
}
