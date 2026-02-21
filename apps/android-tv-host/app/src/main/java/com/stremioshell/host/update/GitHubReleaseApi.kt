package com.stremioshell.host.update

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal data class GitHubAssetDto(
  val name: String,
  val browserDownloadUrl: String,
  val size: Long?
)

internal data class GitHubLatestReleaseDto(
  val tagName: String,
  val htmlUrl: String?,
  val body: String?,
  val publishedAt: String?,
  val assets: List<GitHubAssetDto>
)

class GitHubReleaseApi {
  internal fun fetchLatestRelease(owner: String, repo: String): GitHubLatestReleaseDto {
    val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 10_000
      readTimeout = 10_000
      setRequestProperty("User-Agent", "StremioShell")
      setRequestProperty("Accept", "application/vnd.github+json")
    }

    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val body = if (stream != null) {
      BufferedReader(InputStreamReader(stream)).use { it.readText() }
    } else {
      ""
    }
    if (code !in 200..299) {
      throw IllegalStateException("GitHub API error $code: $body")
    }

    val json = JSONObject(body)
    val assets = parseAssets(json.optJSONArray("assets"))
    return GitHubLatestReleaseDto(
      tagName = json.optString("tag_name").orEmpty(),
      htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() },
      body = json.optString("body").takeIf { it.isNotBlank() },
      publishedAt = json.optString("published_at").takeIf { it.isNotBlank() },
      assets = assets
    )
  }

  private fun parseAssets(rawAssets: JSONArray?): List<GitHubAssetDto> {
    if (rawAssets == null || rawAssets.length() == 0) {
      return emptyList()
    }

    val parsed = mutableListOf<GitHubAssetDto>()
    for (index in 0 until rawAssets.length()) {
      val assetJson = rawAssets.optJSONObject(index) ?: continue
      val name = assetJson.optString("name").orEmpty().trim()
      val downloadUrl = assetJson.optString("browser_download_url").orEmpty().trim()
      if (name.isBlank() || downloadUrl.isBlank()) {
        continue
      }
      val size = assetJson.optLong("size").takeIf { it > 0L }
      parsed += GitHubAssetDto(
        name = name,
        browserDownloadUrl = downloadUrl,
        size = size
      )
    }
    return parsed
  }
}
