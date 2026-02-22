package com.stremioshell.host.update

object AutoUpdatePolicy {
  enum class Decision {
    START_DOWNLOAD,
    ALREADY_DOWNLOADED,
    DOWNLOAD_IN_PROGRESS,
    NO_UPDATE
  }

  fun decide(
    updateInfo: UpdateInfo?,
    hasDownloadedForVersion: Boolean,
    hasActiveDownload: Boolean
  ): Decision {
    if (updateInfo == null) {
      return Decision.NO_UPDATE
    }
    if (hasActiveDownload) {
      return Decision.DOWNLOAD_IN_PROGRESS
    }
    if (hasDownloadedForVersion) {
      return Decision.ALREADY_DOWNLOADED
    }
    return Decision.START_DOWNLOAD
  }
}
