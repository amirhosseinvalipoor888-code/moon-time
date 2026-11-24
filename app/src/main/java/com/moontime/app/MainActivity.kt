package com.moontime.app

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadData(
                        """<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><body style="background:#0c1020;color:white;font:18px sans-serif;padding:24px;direction:rtl"><h3>اتصال به Moon Time برقرار نشد</h3><p>اینترنت گوشی را بررسی کن و دوباره تلاش کن.</p><button onclick="location.reload()">تلاش دوباره</button></body>""",
                        "text/html",
                        "UTF-8"
                    )
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl("https://krmuwbxjsjrezwnhybxa.supabase.co/functions/v1/moon-time")
        setContentView(webView)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
