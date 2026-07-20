package com.stremioshell.host.tv.pairing

import fi.iki.elonen.NanoHTTPD

/**
 * Tiny LAN-only web server shown during phone pairing. The phone opens the
 * page, submits the TMDB key and Comet URL from its own keyboard, and the
 * values are handed back via [onConfig] (called on a server thread).
 *
 * Runs only while the pairing screen is visible; bound to port 0 so the OS
 * assigns a free port, read back from [listeningPort] after start().
 */
class ConfigPairingServer(
  private val currentTmdbKey: String,
  private val currentAddonUrl: String,
  private val onConfig: (tmdbKey: String, addonUrl: String) -> Unit,
) : NanoHTTPD(0) {

  override fun serve(session: IHTTPSession): Response {
    return when {
      session.method == Method.POST && session.uri == "/config" -> handleSubmit(session)
      else -> html(formPage())
    }
  }

  private fun handleSubmit(session: IHTTPSession): Response {
    val body = HashMap<String, String>()
    runCatching { session.parseBody(body) }
    val tmdb = session.parameters["tmdb"]?.firstOrNull().orEmpty().trim()
    val addon = session.parameters["addon"]?.firstOrNull().orEmpty().trim()
    if (tmdb.isBlank() && addon.isBlank()) {
      return html(formPage(error = "Enter at least one value."))
    }
    onConfig(tmdb, addon)
    return html(donePage())
  }

  private fun html(body: String): Response =
    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

  private fun formPage(error: String? = null): String {
    val errorHtml = if (error != null) "<p class=\"err\">$error</p>" else ""
    return """
      <!doctype html><html><head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Set up Stremio Shell TV</title>
      <style>
        :root { color-scheme: dark; }
        body { margin:0; background:#0C0B1E; color:#E6E4F0; font-family:system-ui,sans-serif;
               display:flex; justify-content:center; }
        main { width:100%; max-width:520px; padding:28px 20px 60px; box-sizing:border-box; }
        h1 { font-size:22px; margin:0 0 4px; }
        p.sub { color:#B7B3CF; margin:0 0 24px; font-size:14px; }
        label { display:block; font-weight:600; margin:18px 0 6px; font-size:14px; }
        .hint { color:#B7B3CF; font-weight:400; font-size:12px; }
        input, textarea { width:100%; box-sizing:border-box; background:#161430; color:#fff;
                border:1px solid #3a366a; border-radius:10px; padding:14px; font-size:16px; }
        textarea { min-height:96px; resize:vertical; }
        button { margin-top:26px; width:100%; background:#7B5BF5; color:#fff; border:0;
                 border-radius:24px; padding:16px; font-size:17px; font-weight:600; }
        .err { background:#40202e; color:#ffb4c4; padding:10px 14px; border-radius:10px; font-size:14px; }
      </style></head><body><main>
      <h1>Set up Stremio Shell TV</h1>
      <p class="sub">Paste your keys here, then tap Save. They go straight to your TV over your home network.</p>
      $errorHtml
      <form method="POST" action="/config">
        <label>TMDB API key <span class="hint">themoviedb.org &rsaquo; Settings &rsaquo; API</span></label>
        <input name="tmdb" autocomplete="off" autocapitalize="off" spellcheck="false"
               value="${escape(currentTmdbKey)}" placeholder="TMDB API key">
        <label>Comet addon manifest URL <span class="hint">from your Comet instance, with your Real-Debrid key</span></label>
        <textarea name="addon" autocomplete="off" autocapitalize="off" spellcheck="false"
               placeholder="https://comet.../manifest.json">${escape(currentAddonUrl)}</textarea>
        <button type="submit">Save to TV</button>
      </form>
      </main></body></html>
    """.trimIndent()
  }

  private fun donePage(): String = """
    <!doctype html><html><head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Saved</title>
    <style>
      :root { color-scheme: dark; }
      body { margin:0; background:#0C0B1E; color:#E6E4F0; font-family:system-ui,sans-serif;
             display:flex; align-items:center; justify-content:center; height:100vh; text-align:center; }
      div { padding:24px; }
      h1 { color:#7B5BF5; }
    </style></head><body>
    <div><h1>Saved to your TV</h1><p>You can close this page and pick something to watch.</p></div>
    </body></html>
  """.trimIndent()

  private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
}
