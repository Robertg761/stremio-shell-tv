package com.stremioshell.host.compose

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.stremioshell.host.BuildConfig
import com.stremioshell.host.R
import com.stremioshell.host.update.ApkUpdateManager
import com.stremioshell.host.update.AutoUpdatePolicy
import com.stremioshell.host.update.UpdateInfo
import com.stremioshell.host.update.UpdateRepository

class ComposeUpdateController(
  private val activity: ComponentActivity,
  private val diagnosticsStore: DiagnosticsStore
) {
  private val updateRepository = UpdateRepository()
  private val apkUpdateManager = ApkUpdateManager()

  private var updateCheckInFlight = false
  private var promptedDownloadedVersion: String? = null
  private var activeUpdateDialog: AlertDialog? = null
  private var receiverRegistered = false

  private val updateDownloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
        return
      }

      val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
      val trackedDownloadId = apkUpdateManager.getActiveDownloadId(activity)
      if (completedDownloadId <= 0L || trackedDownloadId == null || completedDownloadId != trackedDownloadId) {
        return
      }

      val result = apkUpdateManager.queryDownload(activity)
      if (result?.status == DownloadManager.STATUS_SUCCESSFUL) {
        diagnosticsStore.record("update", "download complete id=$completedDownloadId")
        maybePromptForDownloadedUpdate(force = true)
      } else {
        diagnosticsStore.record("update", "download failed id=$completedDownloadId reason=${result?.reason}")
        Toast.makeText(activity, activity.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
      }
    }
  }

  fun onCreate() {
    ContextCompat.registerReceiver(
      activity,
      updateDownloadReceiver,
      IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    receiverRegistered = true
  }

  fun onDestroy() {
    activeUpdateDialog?.dismiss()
    if (receiverRegistered) {
      activity.unregisterReceiver(updateDownloadReceiver)
      receiverRegistered = false
    }
  }

  fun onResume() {
    maybePromptForDownloadedUpdate(force = false)
  }

  fun checkForUpdates(manual: Boolean) {
    if (updateCheckInFlight) {
      if (manual) {
        Toast.makeText(activity, activity.getString(R.string.check_updates_in_progress), Toast.LENGTH_SHORT).show()
      }
      return
    }

    if (BuildConfig.DEBUG) {
      if (manual) {
        Toast.makeText(activity, activity.getString(R.string.check_updates_unavailable_debug), Toast.LENGTH_SHORT).show()
      }
      return
    }

    val owner = BuildConfig.GITHUB_RELEASE_OWNER.trim()
    val repo = BuildConfig.GITHUB_RELEASE_REPO.trim()
    if (owner.isBlank() || repo.isBlank()) {
      diagnosticsStore.record("update", "release repo not configured")
      if (manual) {
        Toast.makeText(activity, activity.getString(R.string.check_updates_failed), Toast.LENGTH_SHORT).show()
      }
      return
    }

    updateCheckInFlight = true
    Thread {
      val result = runCatching {
        updateRepository.checkForUpdate(
          owner = owner,
          repo = repo,
          currentVersionName = BuildConfig.VERSION_NAME,
          isTvFlavor = BuildConfig.IS_TV
        )
      }

      activity.runOnUiThread {
        updateCheckInFlight = false
        if (activity.isFinishing || activity.isDestroyed) {
          return@runOnUiThread
        }

        result.exceptionOrNull()?.let { error ->
          diagnosticsStore.record("update", "check failed ${error.message}")
          if (manual) {
            Toast.makeText(activity, activity.getString(R.string.check_updates_failed), Toast.LENGTH_SHORT).show()
          }
        }

        val info = result.getOrNull()
        if (info != null) {
          if (manual) {
            maybeShowUpdateAvailableDialog(info)
          } else {
            maybeAutoDownloadUpdate(info)
          }
        } else if (manual) {
          Toast.makeText(activity, activity.getString(R.string.check_updates_none), Toast.LENGTH_SHORT).show()
        }
      }
    }.start()
  }

  private fun maybeAutoDownloadUpdate(info: UpdateInfo) {
    val decision = AutoUpdatePolicy.decide(
      updateInfo = info,
      hasDownloadedForVersion = apkUpdateManager.hasDownloadedApkForVersion(activity, info.latestVersionName),
      hasActiveDownload = apkUpdateManager.getActiveDownloadId(activity) != null
    )

    when (decision) {
      AutoUpdatePolicy.Decision.NO_UPDATE -> Unit
      AutoUpdatePolicy.Decision.DOWNLOAD_IN_PROGRESS -> {
        diagnosticsStore.record("update", "download already in progress")
      }
      AutoUpdatePolicy.Decision.ALREADY_DOWNLOADED -> {
        diagnosticsStore.record("update", "already downloaded ${info.latestVersionName}")
        maybePromptForDownloadedUpdate(force = true)
      }
      AutoUpdatePolicy.Decision.START_DOWNLOAD -> {
        startUpdateDownload(info, showUserFeedback = false)
      }
    }
  }

  private fun maybeShowUpdateAvailableDialog(info: UpdateInfo) {
    if (apkUpdateManager.hasDownloadedApkForVersion(activity, info.latestVersionName)) {
      maybePromptForDownloadedUpdate(force = true)
      return
    }
    if (activeUpdateDialog?.isShowing == true) {
      return
    }

    val releaseNotes = info.releaseNotes.trim()
    val summary = releaseNotes
      .lineSequence()
      .filter { it.isNotBlank() }
      .take(6)
      .joinToString(separator = "\n")
      .ifBlank { "A newer build is available on GitHub Releases." }

    val suffix = if (releaseNotes.length > summary.length) "\n\n..." else ""
    val publishedAt = info.publishedAt?.let { "\nPublished: $it" } ?: ""
    val message = "$summary$suffix$publishedAt"

    activeUpdateDialog = AlertDialog.Builder(activity)
      .setTitle(activity.getString(R.string.update_available_title, info.latestVersionName))
      .setMessage(message)
      .setPositiveButton(R.string.update_download_button) { _, _ ->
        startUpdateDownload(info, showUserFeedback = true)
      }
      .setNeutralButton(R.string.update_view_release_button) { _, _ ->
        info.releaseUrl.takeIf { it.isNotBlank() }?.let { releaseUrl ->
          activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
        }
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun startUpdateDownload(info: UpdateInfo, showUserFeedback: Boolean) {
    runCatching {
      apkUpdateManager.clearDownloadedState(activity, deleteApk = true)
      val downloadId = apkUpdateManager.startDownload(activity, info)
      diagnosticsStore.record("update", "download started id=$downloadId version=${info.latestVersionName}")
      if (showUserFeedback) {
        Toast.makeText(
          activity,
          activity.getString(R.string.update_download_started, info.latestVersionName),
          Toast.LENGTH_LONG
        ).show()
      }
    }.onFailure {
      diagnosticsStore.record("update", "download start failed ${it.message}")
      if (showUserFeedback) {
        Toast.makeText(activity, activity.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun maybePromptForDownloadedUpdate(force: Boolean) {
    if (!apkUpdateManager.hasPendingDownloadedUpdate(activity, BuildConfig.VERSION_NAME)) {
      return
    }

    val downloadedVersion = apkUpdateManager.getDownloadedVersionName(activity) ?: return
    if (!force && promptedDownloadedVersion == downloadedVersion) {
      return
    }

    promptedDownloadedVersion = downloadedVersion
    if (apkUpdateManager.needsUnknownSourcesPermission(activity)) {
      showEnableUnknownSourcesDialog(downloadedVersion)
    } else {
      showInstallDownloadedUpdateDialog(downloadedVersion)
    }
  }

  private fun showEnableUnknownSourcesDialog(version: String) {
    if (activeUpdateDialog?.isShowing == true) {
      return
    }

    activeUpdateDialog = AlertDialog.Builder(activity)
      .setTitle(activity.getString(R.string.update_download_ready_title))
      .setMessage(
        activity.getString(R.string.update_download_ready_message, version) +
          "\n\n" +
          activity.getString(R.string.update_enable_installs_message)
      )
      .setPositiveButton(R.string.update_enable_installs_button) { _, _ ->
        activity.startActivity(apkUpdateManager.buildUnknownSourcesSettingsIntent(activity))
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun showInstallDownloadedUpdateDialog(version: String) {
    if (activeUpdateDialog?.isShowing == true) {
      return
    }

    activeUpdateDialog = AlertDialog.Builder(activity)
      .setTitle(activity.getString(R.string.update_download_ready_title))
      .setMessage(activity.getString(R.string.update_download_ready_message, version))
      .setPositiveButton(R.string.update_install_button) { _, _ ->
        launchDownloadedInstaller()
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun launchDownloadedInstaller() {
    val installIntent = apkUpdateManager.buildInstallIntentFromDownloadedApk(activity)
    if (installIntent != null) {
      activity.startActivity(installIntent)
      return
    }

    if (apkUpdateManager.needsUnknownSourcesPermission(activity)) {
      val version = apkUpdateManager.getDownloadedVersionName(activity) ?: "latest"
      showEnableUnknownSourcesDialog(version)
      return
    }

    Toast.makeText(activity, activity.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
  }
}
