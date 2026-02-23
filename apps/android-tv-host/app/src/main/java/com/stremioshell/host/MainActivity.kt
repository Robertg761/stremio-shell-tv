package com.stremioshell.host

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.stremioshell.host.compose.ComposeMainActivity

@Deprecated(
  message = "Legacy entrypoint retained for compatibility. Runtime launches ComposeMainActivity without WebView."
)
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(
      Intent(this, ComposeMainActivity::class.java).apply {
        action = intent?.action
        data = intent?.data
        if (intent?.extras != null) {
          putExtras(intent.extras!!)
        }
      }
    )
    finish()
  }
}
