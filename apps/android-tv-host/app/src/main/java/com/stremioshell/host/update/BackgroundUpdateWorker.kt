package com.stremioshell.host.update

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.stremioshell.host.BuildConfig
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class BackgroundUpdateWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
  private val updateRepository = UpdateRepository()
  private val apkUpdateManager = ApkUpdateManager()

  override fun doWork(): Result {
    if (BuildConfig.DEBUG) {
      return Result.success()
    }

    val owner = BuildConfig.GITHUB_RELEASE_OWNER.trim()
    val repo = BuildConfig.GITHUB_RELEASE_REPO.trim()
    if (owner.isBlank() || repo.isBlank()) {
      Log.d(TAG, "Skipping periodic update check: release repository not configured.")
      return Result.success()
    }

    val info = runCatching {
      updateRepository.checkForUpdate(
        owner = owner,
        repo = repo,
        currentVersionName = BuildConfig.VERSION_NAME,
        isTvFlavor = BuildConfig.IS_TV
      )
    }.getOrElse { error ->
      Log.w(TAG, "Periodic update check failed: ${error.message}")
      return if (isRetryable(error)) Result.retry() else Result.success()
    }

    val decision = AutoUpdatePolicy.decide(
      updateInfo = info,
      hasDownloadedForVersion = info?.let {
        apkUpdateManager.hasDownloadedApkForVersion(applicationContext, it.latestVersionName)
      } ?: false,
      hasActiveDownload = apkUpdateManager.getActiveDownloadId(applicationContext) != null
    )

    return when (decision) {
      AutoUpdatePolicy.Decision.NO_UPDATE -> Result.success()
      AutoUpdatePolicy.Decision.ALREADY_DOWNLOADED -> Result.success()
      AutoUpdatePolicy.Decision.DOWNLOAD_IN_PROGRESS -> Result.success()
      AutoUpdatePolicy.Decision.START_DOWNLOAD -> {
        val updateInfo = info ?: return Result.success()
        runCatching {
          apkUpdateManager.clearDownloadedState(applicationContext, deleteApk = true)
          apkUpdateManager.startDownload(applicationContext, updateInfo)
          Log.d(TAG, "Queued background update download for ${updateInfo.latestVersionName}.")
        }.fold(
          onSuccess = { Result.success() },
          onFailure = { error ->
            Log.w(TAG, "Failed to queue background update download: ${error.message}")
            if (isRetryable(error)) Result.retry() else Result.success()
          }
        )
      }
    }
  }

  companion object {
    private const val TAG = "StremioHostUpdateWorker"
    private val GITHUB_ERROR_REGEX = Regex("""GitHub API error (\d{3})""")

    internal fun isRetryable(error: Throwable): Boolean {
      if (error is UnknownHostException || error is SocketTimeoutException || error is IOException) {
        return true
      }

      val message = error.message.orEmpty()
      val statusCode = GITHUB_ERROR_REGEX.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
      return statusCode == 429 || (statusCode != null && statusCode in 500..599)
    }
  }
}
