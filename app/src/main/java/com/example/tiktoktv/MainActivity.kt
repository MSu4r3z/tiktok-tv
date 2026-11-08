package com.example.tiktoktv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var tvScript: String

    private var videoMode = true      // true = el control remoto maneja el video
    private var centerLong = false
    private var quality = 0           // índice dentro de QUALITIES
    private var showDiag = true       // muestra el viewport real al cargar

    companion object {
        const val HOME = "https://www.tiktok.com/foryou"

        // Ancho del viewport en píxeles CSS (= píxeles reales de renderizado).
        // La app lo escala a pantalla completa, así que ocupa todo el televisor
        // en cualquier resolución. Menos píxeles = más fluido.
        // Botón MENÚ / ROJO / INFO del control remoto para rotar.
        val QUALITIES = intArrayOf(1280, 1600, 1920)

        // Hosts de analítica. Lista conservadora: si algo se rompe, vacíala.
        val BLOCKED_HOSTS = listOf(
            "google-analytics.com", "googletagmanager.com",
            "doubleclick.net", "sentry.io"
        )
    }

    // Densidad fija: 1 píxel CSS = 1 píxel real, sin importar lo que reporte la caja.
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = 160
        config.fontScale = 1f
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        tvScript = assets.open("tv.js").bufferedReader().use { it.readText() }

        // Se guarda con otro nombre de clave para ignorar el valor de la versión anterior
        quality = getSharedPreferences("tv", MODE_PRIVATE)
            .getInt("quality2", 0).coerceIn(0, QUALITIES.size - 1)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        root.addView(web)
        setContentView(root)

        configureWebView()
        applyRenderScale()
        checkWebViewVersion()

        web.loadUrl(HOME)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = false
            // User-agent de escritorio, conservando la versión real de Chrome del WebView
            userAgentString = userAgentString
                .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
                .replace(" Mobile", "")
        }
        web.setInitialScale(0)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)   // necesario para el login
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }

        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val host = request.url.host ?: return null
                return if (BLOCKED_HOSTS.any { host.contains(it) })
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                else null
            }

            // Evita que la web intente abrir la app de TikTok (intent://, snssdk://...)
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme
                return !(scheme == "http" || scheme == "https")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(tvScript, null)
                view.evaluateJavascript("window.tv && tv.setUI(${!videoMode})", null)

                if (showDiag) {
                    showDiag = false
                    view.evaluateJavascript(
                        "(innerWidth+'x'+innerHeight+' @'+devicePixelRatio)"
                    ) { r ->
                        Toast.makeText(
                            this@MainActivity,
                            "Viewport CSS: ${r.trim('"')}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        web.requestFocus()
    }

    /**
     * Renderiza el WebView en un viewport de QUALITIES[quality] px de ancho
     * (misma proporción que la pantalla) y lo escala a pantalla completa.
     */
    private fun applyRenderScale() {
        val target = QUALITIES[quality]
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)

        if (dm.widthPixels <= target) {
            web.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            web.scaleX = 1f
            web.scaleY = 1f
            return
        }

        val scale = dm.widthPixels.toFloat() / target
        val h = Math.ceil((dm.heightPixels / scale).toDouble()).toInt()
        web.layoutParams = FrameLayout.LayoutParams(target, h)
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = scale
        web.scaleY = scale
    }

    private fun cycleQuality() {
        quality = (quality + 1) % QUALITIES.size
        getSharedPreferences("tv", MODE_PRIVATE).edit().putInt("quality2", quality).apply()
        applyRenderScale()
        showDiag = true
        web.reload()
        Toast.makeText(this, "Calidad: ${QUALITIES[quality]} px", Toast.LENGTH_SHORT).show()
    }

    private fun checkWebViewVersion() {
        if (Build.VERSION.SDK_INT < 26) return
        val v = WebView.getCurrentWebViewPackage()?.versionName ?: return
        val major = v.substringBefore('.').toIntOrNull() ?: return
        if (major < 110) {
            Toast.makeText(
                this,
                "WebView $v es antiguo. Actualiza 'Android System WebView' en Play Store.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun js(call: String) =
        web.evaluateJavascript("window.tv && tv.$call", null)

    private fun toggleMode() {
        videoMode = !videoMode
        js("setUI(${!videoMode})")
        Toast.makeText(
            this,
            if (videoMode) "Modo VIDEO" else "Modo NAVEGACIÓN (login / búsqueda)",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        val center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER

        // Cambiar calidad de renderizado
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_PROG_RED ||
            k == KeyEvent.KEYCODE_INFO) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) cycleQuality()
            return true
        }

        // Pulsación larga en OK: alterna entre modo video y modo navegación
        if (center && e.action == KeyEvent.ACTION_DOWN && e.isLongPress) {
            centerLong = true
            toggleMode()
            return true
        }
        if (center && e.action == KeyEvent.ACTION_UP && centerLong) {
            centerLong = false
            return true
        }

        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) {
                val onFeed = web.url?.contains("/foryou") == true
                if (!onFeed && web.canGoBack()) web.goBack() else finish()
            }
            return true
        }

        // En modo navegación el WebView maneja el foco normalmente
        if (!videoMode) return super.dispatchKeyEvent(e)

        val action = when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "prev()"
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> "next()"
            KeyEvent.KEYCODE_DPAD_LEFT -> "like()"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "mute()"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "toggle()"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "toggle()"
            else -> return super.dispatchKeyEvent(e)
        }

        // OK actúa al soltar (para distinguir pulsación larga); el resto, al presionar
        val trigger = if (center) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN
        if (e.action == trigger && (center || e.repeatCount == 0)) js(action)
        return true
    }

    override fun onResume() { super.onResume(); web.onResume() }

    override fun onPause() {
        CookieManager.getInstance().flush()   // guarda la sesión
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
