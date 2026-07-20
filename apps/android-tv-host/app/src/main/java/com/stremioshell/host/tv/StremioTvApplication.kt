package com.stremioshell.host.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

/**
 * Tunes Coil for a low-RAM TV: RGB_565 halves poster memory (posters are
 * opaque JPEGs), a bounded memory cache keeps GC pressure down during scroll,
 * and crossfade is off so focus moves stay cheap.
 */
class StremioTvApplication : Application(), ImageLoaderFactory {
  override fun newImageLoader(): ImageLoader {
    return ImageLoader.Builder(this)
      .allowRgb565(true)
      .crossfade(false)
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.20)
          .build()
      }
      .build()
  }
}
