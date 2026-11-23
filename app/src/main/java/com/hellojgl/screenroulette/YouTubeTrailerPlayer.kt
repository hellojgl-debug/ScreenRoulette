package com.hellojgl.screenroulette

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

@Composable
internal fun YouTubeTrailerPlayer(videoId: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                            loadUrl(
                                "https://www.youtube.com/embed/$videoId?playsinline=1&fs=0",
                                mapOf("Referer" to "https://${context.packageName}/")
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
    }
}
