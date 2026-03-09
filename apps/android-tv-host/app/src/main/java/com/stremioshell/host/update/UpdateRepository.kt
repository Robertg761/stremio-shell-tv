package com.stremioshell.host.update

class UpdateRepository(
  private val api: GitHubReleaseApi = GitHubReleaseApi()
) {
  fun checkForUpdate(
    owner: String,
    repo: String,
    currentVersionName: String
  ): UpdateInfo? {
    val latest = api.fetchLatestRelease(owner, repo)
    val latestTag = latest.tagName.trim()
    if (latestTag.isBlank()) {
      return null
    }

    if (!isNewerVersion(latestTag, currentVersionName)) {
      return null
    }

    val selectedApk = selectApkAsset(latest.assets) ?: return null
    return UpdateInfo(
      latestVersionName = latestTag.removePrefix("v").removePrefix("V"),
      apkName = selectedApk.name,
      apkUrl = selectedApk.browserDownloadUrl,
      releaseNotes = latest.body.orEmpty().trim(),
      releaseUrl = latest.htmlUrl.orEmpty().trim(),
      apkSizeBytes = selectedApk.size,
      publishedAt = latest.publishedAt?.trim()
    )
  }

  private fun isNewerVersion(latestTag: String, currentVersionName: String): Boolean {
    val latestSemVer = SemVer.parseOrNull(latestTag)
    val currentSemVer = SemVer.parseOrNull(currentVersionName)
    if (latestSemVer != null && currentSemVer != null) {
      return latestSemVer > currentSemVer
    }

    val normalizedLatest = normalizeVersionLabel(latestTag)
    val normalizedCurrent = normalizeVersionLabel(currentVersionName)
    return normalizedLatest != normalizedCurrent
  }

  private fun normalizeVersionLabel(value: String): String {
    return value
      .trim()
      .removePrefix("v")
      .removePrefix("V")
      .substringBefore('-')
      .lowercase()
  }

  private fun selectApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
    val apkAssets = assets.filter { asset ->
      asset.name.endsWith(".apk", ignoreCase = true)
    }
    if (apkAssets.isEmpty()) {
      return null
    }

    val flavorToken = "-tv-"
    val flavorMatches = apkAssets.filter { asset ->
      asset.name.contains(flavorToken, ignoreCase = true)
    }
    val nonDebugFlavorMatch = flavorMatches.firstOrNull { asset ->
      !asset.name.contains("debug", ignoreCase = true)
    }
    if (nonDebugFlavorMatch != null) {
      return nonDebugFlavorMatch
    }
    if (flavorMatches.isNotEmpty()) {
      return flavorMatches.first()
    }

    return apkAssets.firstOrNull { asset ->
      !asset.name.contains("debug", ignoreCase = true)
    } ?: apkAssets.firstOrNull()
  }
}
