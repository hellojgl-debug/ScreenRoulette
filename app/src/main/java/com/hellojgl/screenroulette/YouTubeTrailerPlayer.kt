package com.hellojgl.screenroulette

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

@Composable
internal fun YouTubeTrailerPlayer(videoId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowWidth = configuration.screenWidthDp
    val scheme = MaterialTheme.colorScheme
    val latestOnClose by rememberUpdatedState(onClose)
    var playerWindow by remember { mutableStateOf<TrailerWindow?>(null) }

    DisposableEffect(context, videoId) {
        val window = TrailerWindow(
            context, videoId, scheme.surface.toArgb(), scheme.onSurface.toArgb(),
            scheme.primary.toArgb()
        ) { latestOnClose() }
        window.show()
        window.resize(landscape, windowWidth)
        playerWindow = window
        onDispose {
            playerWindow = null
            window.release()
        }
    }
    LaunchedEffect(playerWindow, landscape, windowWidth) {
        playerWindow?.resize(landscape, windowWidth)
    }
}

private class TrailerWindow(
    private val context: Context,
    videoId: String,
    surface: Int,
    textColor: Int,
    actionColor: Int,
    onClose: () -> Unit
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    // A hardware-accelerated native window gives decoded video an opaque surface.
    private val dialog = Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.60f)
            setBackgroundDrawable(ColorDrawable(surface))
            setFormat(PixelFormat.OPAQUE)
        }
        setCanceledOnTouchOutside(true)
        setOnDismissListener { onClose() }
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surface)
    }
    private val player = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame || (request.url.scheme != "https" && request.url.scheme != "about")
        }
        loadUrl(
            "https://www.youtube.com/embed/$videoId?playsinline=1&fs=0",
            mapOf("Referer" to "https://${context.packageName}/")
        )
    }
    private var fillsScreen = false

    init {
        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(
                TextView(context).apply {
                    text = "YouTube trailer"
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textColor)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(Button(context).apply {
                text = "Close"
                isAllCaps = false
                setTextColor(actionColor)
                setBackgroundColor(Color.TRANSPARENT)
                minimumHeight = dp(48)
                setOnClickListener { dialog.dismiss() }
            })
        }
        content.addView(toolbar)
        content.addView(player, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        dialog.setContentView(content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            if (fillsScreen) view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    fun show() = dialog.show()

    fun resize(landscape: Boolean, screenWidthDp: Int) {
        val window = dialog.window ?: return
        fillsScreen = landscape
        if (!landscape) content.setPadding(0, 0, 0, 0)
        player.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (landscape) 0 else dp(220),
            if (landscape) 1f else 0f
        )
        WindowCompat.setDecorFitsSystemWindows(window, !landscape)
        window.setLayout(
            if (landscape) ViewGroup.LayoutParams.MATCH_PARENT else dp((screenWidthDp - 32).coerceIn(240, 440)),
            if (landscape) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val controller = WindowCompat.getInsetsController(window, content)
        if (landscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        ViewCompat.requestApplyInsets(content)
        player.invalidate()
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        player.stopLoading()
        content.removeView(player)
        player.destroy()
    }
}
