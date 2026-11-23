package com.hellojgl.screenroulette

import android.content.res.Configuration
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun YouTubeTrailerPlayer(videoId: String, onClose: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow, landscape) {
            val insetsController = dialogWindow?.let { WindowCompat.getInsetsController(it, dialogView) }
            if (landscape) {
                insetsController?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (landscape) insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        Surface(
            modifier = if (landscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(horizontal = 24.dp).widthIn(max = 440.dp).fillMaxWidth()
            },
            shape = if (landscape) RectangleShape else RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = if (landscape) Modifier.safeDrawingPadding() else Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (landscape) 12.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("YouTube trailer", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text("Close") }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth().then(
                        if (landscape) Modifier.weight(1f) else Modifier.height(220.dp)
                    ),
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
                    update = { player -> player.invalidate() },
                    onRelease = { player ->
                        player.stopLoading()
                        player.destroy()
                    }
                )
            }
        }
    }
}
