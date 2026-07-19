package com.stremioshell.host.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.stremioshell.host.tv.data.WatchStateStore
import com.stremioshell.host.tv.player.MpvPlayerActivity
import com.stremioshell.host.tv.ui.StreamLauncher
import com.stremioshell.host.tv.ui.TvApp
import com.stremioshell.host.tv.ui.theme.StremioTvTheme
import kotlinx.coroutines.launch

/**
 * Native Compose TV app: TMDB catalogs + Comet (Real-Debrid) streams + libmpv
 * playback. Runs alongside the WebView shell until it reaches parity, then
 * takes over the launcher alias.
 */
class TvAppActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val watchStore = WatchStateStore(applicationContext)
    setContent {
      StremioTvTheme {
        TvApp(
          streamLauncher = StreamLauncher { screen, stream ->
            lifecycleScope.launch {
              val resumeMs = watchStore.get(MpvPlayerActivity.watchKeyFor(screen))?.positionMs ?: 0L
              startActivity(MpvPlayerActivity.createIntent(this@TvAppActivity, screen, stream, resumeMs))
            }
          }
        )
      }
    }
  }
}
