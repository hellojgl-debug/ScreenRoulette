package com.hellojgl.screenroulette

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun YouTubeTrailerPlayer(videoId: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("YouTube trailer", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose) { Text("Close") }
        }

        AndroidView(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().height(220.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = request.isForMainFrame ||
                            request.url.scheme !in setOf("https", "about")
                    }
                    loadDataWithBaseURL(
                        "https://${context.packageName}/",
                        trailerPlayerHtml(videoId),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            onRelease = { player ->
                player.stopLoading()
                player.destroy()
            }
        )
    }
}

private fun trailerPlayerHtml(videoId: String) = """
    <!doctype html>
    <html lang="en">
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta name="referrer" content="strict-origin-when-cross-origin">
      <style>
        html, body { margin: 0; height: 100%; background: #000; }
        iframe { display: block; width: 100%; height: 100%; border: 0; }
      </style>
    </head>
    <body>
      <iframe title="YouTube trailer"
        src="https://www.youtube.com/embed/$videoId?playsinline=1&amp;fs=0"
        allow="encrypted-media">
      </iframe>
    </body>
    </html>
""".trimIndent()
