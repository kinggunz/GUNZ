package com.gunz.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Peramban mini (WebView) dengan bilah alamat; dipakai untuk jendela Peramban dan YouTube. */
class WebPane(
    private val ctx: Context,
    startUrl: String,
    private val homeUrl: String,
    private val showAddress: Boolean
) {
    val view = FrameLayout(ctx)
    val webView = WebView(ctx)

    private val column = LinearLayout(ctx)
    private val fullHolder = FrameLayout(ctx)
    private val address = EditText(ctx)
    private val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val backBtn: ImageView
    private val fwdBtn: ImageView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    init {
        column.orientation = LinearLayout.VERTICAL
        column.setBackgroundColor(Color.parseColor("#FF202020"))

        val toolbar = LinearLayout(ctx)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setBackgroundColor(Color.parseColor("#FF2B2B2B"))

        backBtn = tool(Glyph.BACK) { if (webView.canGoBack()) webView.goBack() }
        fwdBtn = tool(Glyph.FORWARD) { if (webView.canGoForward()) webView.goForward() }
        val reload = tool(Glyph.RELOAD) { webView.reload() }
        val home = tool(Glyph.HOME) { webView.loadUrl(homeUrl) }
        toolbar.addView(backBtn, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(40)))
        toolbar.addView(fwdBtn, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(40)))
        toolbar.addView(reload, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(40)))
        toolbar.addView(home, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(40)))

        if (showAddress) {
            address.setSingleLine(true)
            address.setTextColor(Color.WHITE)
            address.setHintTextColor(Color.parseColor("#99FFFFFF"))
            address.hint = "Cari di Google atau ketik alamat web"
            address.textSize = 13f
            address.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            address.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            val pill = GradientDrawable()
            pill.setColor(Color.parseColor("#FF3C3C3C"))
            pill.cornerRadius = ctx.dp(16).toFloat()
            address.background = pill
            address.setPadding(ctx.dp(14), 0, ctx.dp(14), 0)
            address.setSelectAllOnFocus(true)
            address.setOnEditorActionListener { _, _, _ ->
                go(address.text.toString())
                hideKeyboard()
                webView.requestFocus()
                true
            }
            val alp = LinearLayout.LayoutParams(0, ctx.dp(32), 1f)
            alp.leftMargin = ctx.dp(4)
            alp.rightMargin = ctx.dp(8)
            toolbar.addView(address, alp)
        } else {
            val label = TextView(ctx)
            label.text = "YouTube"
            label.setTextColor(Color.parseColor("#CCFFFFFF"))
            label.textSize = 13f
            label.setPadding(ctx.dp(8), 0, 0, 0)
            toolbar.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        progress.max = 100
        progress.progressTintList = ColorStateList.valueOf(Color.parseColor("#4CC2FF"))
        progress.visibility = View.GONE

        setupWebView()

        column.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(40)))
        column.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(3)))
        column.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        fullHolder.setBackgroundColor(Color.BLACK)
        fullHolder.visibility = View.GONE
        view.addView(fullHolder, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateNav()
        webView.loadUrl(startUrl)
    }

    private fun tool(g: Glyph, onClick: () -> Unit): ImageView {
        val iv = ImageView(ctx)
        iv.setImageDrawable(GlyphIcon(g, Color.WHITE))
        iv.setPadding(ctx.dp(9), ctx.dp(11), ctx.dp(9), ctx.dp(11))
        iv.background = hoverBg()
        iv.setOnClickListener { onClick() }
        return iv
    }

    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.userAgentString = cleanUa(s.userAgentString)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(Color.WHITE)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                return !(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:"))
            }

            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && showAddress && !address.hasFocus()) address.setText(url)
                progress.visibility = View.VISIBLE
                updateNav()
            }

            override fun onPageFinished(v: WebView?, url: String?) {
                progress.visibility = View.GONE
                if (url != null && showAddress && !address.hasFocus()) address.setText(url)
                updateNav()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(v: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (v == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = v
                customCb = callback
                fullHolder.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                fullHolder.visibility = View.VISIBLE
                column.visibility = View.GONE
            }

            override fun onHideCustomView() {
                exitCustom()
            }
        }
    }

    private fun cleanUa(ua: String): String =
        ua.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")

    private fun exitCustom() {
        val v = customView ?: return
        fullHolder.removeView(v)
        fullHolder.visibility = View.GONE
        column.visibility = View.VISIBLE
        customView = null
        try {
            customCb?.onCustomViewHidden()
        } catch (e: Exception) {
        }
        customCb = null
    }

    private fun updateNav() {
        backBtn.alpha = if (webView.canGoBack()) 1f else 0.4f
        fwdBtn.alpha = if (webView.canGoForward()) 1f else 0.4f
    }

    private fun hideKeyboard() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(address.windowToken, 0)
    }

    fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        webView.loadUrl(url)
    }

    /** Kembali satu langkah (atau keluar layar penuh video). Mengembalikan true bila ada yang ditangani. */
    fun goBack(): Boolean {
        if (customView != null) {
            exitCustom()
            return true
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun currentUrl(): String = webView.url ?: homeUrl

    fun destroy() {
        try {
            exitCustom()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (e: Exception) {
        }
    }
}
