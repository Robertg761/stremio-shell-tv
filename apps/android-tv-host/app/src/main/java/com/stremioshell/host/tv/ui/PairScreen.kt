package com.stremioshell.host.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.pairing.encodeQrBitmap

@Composable
fun PairScreen(viewModel: TvAppViewModel, onPaired: () -> Unit) {
  val state by viewModel.pairing.collectAsState()

  LaunchedEffect(Unit) { viewModel.startPairing() }
  DisposableEffect(Unit) { onDispose { viewModel.stopPairing() } }

  LaunchedEffect(state) {
    if (state is TvAppViewModel.PairingState.Received) onPaired()
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    when (val s = state) {
      is TvAppViewModel.PairingState.Ready -> ReadyContent(s.url)
      is TvAppViewModel.PairingState.Failed -> CenteredMessage(s.message)
      else -> CenteredLoading("Starting pairing...")
    }
  }
}

@Composable
private fun ReadyContent(url: String) {
  val qr = remember(url) { encodeQrBitmap(url, 520).asImageBitmap() }
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Text("Set up with your phone", style = MaterialTheme.typography.headlineMedium)
    Text(
      "Scan this code with your phone's camera, then paste your TMDB key and Comet URL there.",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 40.dp),
    )
    Image(
      bitmap = qr,
      contentDescription = "Pairing QR code",
      modifier = Modifier
        .size(260.dp)
        .background(Color.White)
        .padding(10.dp),
    )
    Text(
      "or open  $url  in your phone browser",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "Your phone must be on the same Wi-Fi as this TV.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
