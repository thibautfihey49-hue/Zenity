package com.zenity.pro

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText

    private var lastMediaUrl: String? = null
    private var ghostEnabled = false
    private var turboEnabled = false
    private var ultraEnabled = false
    private var quantumEnabled = false

    private val adHosts = listOf(
        "doubleclick", "googlesyndication", "adservice", "adsystem",
        "taboola", "outbrain", "adnxs", "scorecardresearch", "zedo",
        "advertising", "pagead", "ads.", "adserver"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)

        setupWebView()
        setupControls()

        webView.loadUrl("file:///android_asset/home.html")
    }

    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                urlBar.setText(url)
                detectMediaUrl(url)
                if (!ghostEnabled) {
                    addToHistory(url, url)
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { if (it != "about:blank") urlBar.setText(it) }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (!ultraEnabled) return super.shouldInterceptRequest(view, request)

                val url = request?.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)

                if (adHosts.any { url.contains(it) }) {
                    return emptyResponse()
                }
                if (url.contains("popup") || url.contains("popunder")) {
                    return emptyResponse()
                }
                if (url.contains("cookie") && url.contains("consent")) {
                    return emptyResponse()
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun setupControls() {
        findViewById<Button>(R.id.goButton).setOnClickListener {
            loadUrl(urlBar.text.toString().trim())
        }

        urlBar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrl(v.text.toString().trim())
                true
            } else false
        }

        findViewById<Button>(R.id.tabNew).setOnClickListener {
            webView.loadUrl("about:blank")
            urlBar.setText("")
            lastMediaUrl = null
            Toast.makeText(this, "Nouvel onglet", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.addFav).setOnClickListener {
            val url = urlBar.text.toString().trim()
            if (url.isEmpty() || url == "about:blank") {
                Toast.makeText(this, "Aucune page à ajouter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addToFavorites(url, webView.title ?: url)
            Toast.makeText(this, "Ajouté aux favoris", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.ghostMode).setOnClickListener {
            ghostEnabled = !ghostEnabled
            if (ghostEnabled) {
                webView.clearHistory()
                webView.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                Toast.makeText(this, "Ghost activé (navigation privée)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ghost désactivé", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.turboMode).setOnClickListener {
            turboEnabled = !turboEnabled
            val s = webView.settings
            s.loadsImagesAutomatically = !turboEnabled
            s.blockNetworkImage = turboEnabled
            Toast.makeText(
                this,
                if (turboEnabled) "Turbo activé (images bloquées)" else "Turbo désactivé",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<Button>(R.id.ultraMode).setOnClickListener {
            ultraEnabled = !ultraEnabled
            Toast.makeText(
                this,
                if (ultraEnabled) "Zenity Ultra activé (bloqueurs)" else "Zenity Ultra désactivé",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<Button>(R.id.quantumMode).setOnClickListener {
            quantumEnabled = !quantumEnabled
            val s = webView.settings
            if (quantumEnabled) {
                s.javaScriptEnabled = true
                s.domStorageEnabled = true
                s.databaseEnabled = true
                s.cacheMode = WebSettings.LOAD_DEFAULT
                s.loadsImagesAutomatically = true
                s.blockNetworkImage = false
                s.useWideViewPort = true
                s.loadWithOverviewMode = true
                Toast.makeText(this, "Zenity Quantum activé", Toast.LENGTH_SHORT).show()
            } else {
                s.cacheMode = WebSettings.LOAD_DEFAULT
                Toast.makeText(this, "Zenity Quantum désactivé", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.videoDownload).setOnClickListener {
            val url = lastMediaUrl
            if (url.isNullOrEmpty()) {
                Toast.makeText(this, "Aucune vidéo détectée", Toast.LENGTH_SHORT).show()
            } else {
                requestDownload(url)
            }
        }
    }

    private fun loadUrl(raw: String) {
        if (raw.isEmpty()) return
        val finalUrl = when {
            raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("file://") -> raw
            raw.contains(".") && !raw.contains(" ") -> "https://$raw"
            else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
        }
        webView.loadUrl(finalUrl)
    }

    private fun detectMediaUrl(url: String) {
        val lower = url.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".m3u8") ||
            lower.endsWith(".webm") || lower.contains("/video") ||
            lower.contains("googlevideo.com")
        ) {
            lastMediaUrl = url
            runOnUiThread {
                Toast.makeText(this, "Vidéo détectée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addToHistory(url: String, title: String) {
        try {
            val file = File(filesDir, "history.json")
            if (!file.exists()) file.writeText("[]")
            val arr = JSONArray(file.readText())
            val obj = JSONObject().apply {
                put("url", url)
                put("title", title)
            }
            arr.put(obj)
            // Keep last 100 entries
            while (arr.length() > 100) arr.remove(0)
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun addToFavorites(url: String, title: String) {
        try {
            val file = File(filesDir, "favorites.json")
            if (!file.exists()) file.writeText("[]")
            val arr = JSONArray(file.readText())
            // Avoid duplicates
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("url") == url) return
            }
            val obj = JSONObject().apply {
                put("url", url)
                put("title", title)
            }
            arr.put(obj)
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun requestDownload(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
                return
            }
        }

        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Zenity – Téléchargement")
                .setDescription(url)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "zenity_${System.currentTimeMillis()}.mp4"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            dm.enqueue(request)
            Toast.makeText(this, "Téléchargement lancé", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur téléchargement: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
