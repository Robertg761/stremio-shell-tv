package com.stremioshell.host.tv.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Seam for HTTP GETs so clients stay unit-testable without a server. */
fun interface HttpFetcher {
  /** Returns the response body for a 2xx response; throws IOException otherwise. */
  suspend fun get(url: String): String
}

object OkHttpFetcher : HttpFetcher {
  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).header("Accept", "application/json").build()
    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IOException("GET $url failed: HTTP ${response.code}")
      }
      body
    }
  }
}
