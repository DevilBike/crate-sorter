package io.github.devilbike.cratesorter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

// Shows the hosted web app in a WebView and gives it a native bridge, so its work can keep
// running in a foreground service while the phone is locked, without playing any audio.
class MainActivity : Activity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = web.settings.userAgentString + " CrateSorterApp/1"
        // Keep the page's renderer at full priority even when it isn't on screen.
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        web.addJavascriptInterface(Bridge(), "CrateNative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        // Android 15 draws apps edge to edge, so pad the page clear of the status and navigation bars.
        if (Build.VERSION.SDK_INT >= 30) {
            web.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    // The WebView is deliberately not paused in onPause/onStop, so its work carries on in the background.

    @Deprecated("Still called on all supported versions")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }

    override fun onDestroy() {
        stopService(Intent(this, WorkService::class.java))
        web.destroy()
        super.onDestroy()
    }

    private fun startWork() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, WorkService::class.java))
    }

    inner class Bridge {
        // Timers in a hidden page get throttled, so the page asks for its waits to be timed natively.
        @JavascriptInterface
        fun sleep(id: Int, ms: Int) {
            handler.postDelayed({ web.evaluateJavascript("window.__crateWake && window.__crateWake($id)", null) }, ms.toLong())
        }

        @JavascriptInterface
        fun setWorking(on: Boolean) {
            handler.post { if (on) startWork() else stopService(Intent(this@MainActivity, WorkService::class.java)) }
        }

        @JavascriptInterface
        fun copy(text: String) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Crate Sorter", text))
        }

        @JavascriptInterface
        fun batteryUnrestricted(): Boolean =
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        @SuppressLint("BatteryLife")
        @JavascriptInterface
        fun askBattery() {
            handler.post {
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    companion object {
        const val START_URL = "https://devilbike.github.io/crate-sorter/"
    }
}
