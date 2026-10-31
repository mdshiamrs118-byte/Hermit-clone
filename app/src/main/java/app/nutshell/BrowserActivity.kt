package app.nutshell

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.widget.doAfterTextChanged
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.UUID

class BrowserActivity : AppCompatActivity(), QuickPanel.Host {

    companion object {
        const val EXTRA_APP = "app_id"
        const val EXTRA_URL = "url"
        const val INCOGNITO = "incognito"

        fun launchIntent(ctx: Context, appId: String, url: String? = null): Intent =
            Intent(ctx, BrowserActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_APP, appId)
                .apply { if (url != null) putExtra(EXTRA_URL, url) }

    }

    private lateinit var app: LiteApp
    private lateinit var web: WebView
    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progress: ProgressBar

    private var incognito = false
    private var profile: Profile? = null
    private var profileName: String? = null
    private var clearOnExit = false
    private var home = ""

    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var permCb: ((Map<String, Boolean>) -> Unit)? = null
    private var blobToken: String? = null
    private lateinit var moreBtn: ImageButton
    private lateinit var panel: QuickPanel

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            permCb?.invoke(r); permCb = null
        }
    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
            fileCb = null
        }

    // ------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)

        val id = intent.getStringExtra(EXTRA_APP) ?: INCOGNITO
        incognito = id == INCOGNITO
        app = if (incognito) {
            LiteApp(id = INCOGNITO, name = getString(R.string.incognito), url = "https://duckduckgo.com/",
                color = 0xFF424242.toInt(), scopeToSite = false)
        } else {
            Store.get(id) ?: run { finish(); return }
        }
        home = Uri.parse(app.url).host.orEmpty()

        val fg = app.color.onColor()
        toolbar = MaterialToolbar(this).apply {
            title = app.name
            setBackgroundColor(app.color)
            setTitleTextColor(fg)
            setSubtitleTextColor(fg)
            setNavigationIcon(R.drawable.ic_close)
            setNavigationIconTint(fg)
            setNavigationOnClickListener { finish() }
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(fg)
            progressBackgroundTintList = ColorStateList.valueOf(app.color)
        }
        web = WebView(this)
        attachProfile()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(app.color)
            addView(toolbar, LinearLayout.LayoutParams(MATCH, dp(56)))
            addView(progress, LinearLayout.LayoutParams(MATCH, dp(3)))
            addView(web, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        frame = FrameLayout(this).apply {
            setBackgroundColor(app.color)
            addView(root, MATCH, MATCH)
        }
        // Small floating button that replaces the toolbar's three dots when the toolbar is hidden.
        moreBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
            visibility = View.GONE
            setOnClickListener { panel.show() }
        }
        frame.addView(moreBtn, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(8), dp(8), 0)
        })
        panel = QuickPanel(this, this, frame)
        setContentView(frame)
        frame.applySystemBarPadding()
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = app.color.isLightColor()
            isAppearanceLightNavigationBars = app.color.isLightColor()
        }

        applyTaskDescription()

        buildMenu(fg)
        configureWebView()
        applyChrome()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    panel.isShowing -> panel.hide()
                    customView != null -> hideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: app.url)
    }

    /** Shows the lite app's name and color in the Recents screen. */
    @Suppress("DEPRECATION")
    private fun applyTaskDescription() {
        setTaskDescription(android.app.ActivityManager.TaskDescription(app.name, null as Bitmap?, app.color))
    }

    override fun onPause() { if (::web.isInitialized) web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }

    override fun onDestroy() {
        if (!::web.isInitialized) { super.onDestroy(); return } // finished early (app was deleted)
        frame.removeAllViews()
        web.stopLoading()
        web.destroy()
        if (clearOnExit) {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        if (incognito) profileName?.let {
            try { ProfileStore.getInstance().deleteProfile(it) } catch (_: Throwable) { }
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------------
    // WebView setup
    // ------------------------------------------------------------------------------------

    /** Gives each sandboxed lite app (and each incognito tab) its own cookie/storage profile. */
    private fun attachProfile() {
        val name = when {
            incognito -> "incognito_" + UUID.randomUUID().toString().replace("-", "").take(12)
            app.sandbox -> "app_${app.id}"
            else -> null
        } ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            profile = ProfileStore.getInstance().getOrCreateProfile(name)
            profileName = name
            WebViewCompat.setProfile(web, name)
        } else if (incognito) {
            clearOnExit = true // fallback: wipe the shared store when the tab closes
            toast("Isolated profiles aren’t supported by this WebView; data is cleared on close")
        }
    }

    private fun cookies(): CookieManager = profile?.cookieManager ?: CookieManager.getInstance()

    private fun configureWebView() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        web.settings.apply {
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(true)
            safeBrowsingEnabled = true
        }
        applySettings()
        web.addJavascriptInterface(BlobBridge(), "NutshellBlob")
        web.setDownloadListener { url, ua, cd, mime, _ ->
            if (url.startsWith("blob:")) fetchBlob(url, mime) else download(url, ua, cd, mime)
        }
        web.webViewClient = client()
        web.webChromeClient = chrome()
    }

    private fun applySettings() {
        val s = web.settings
        s.javaScriptEnabled = app.javascript
        s.setGeolocationEnabled(app.allowLocation)
        s.textZoom = app.textZoom
        s.userAgentString = if (app.desktopMode) {
            WebSettings.getDefaultUserAgent(this)
                .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
                .replace(" Mobile", "")
        } else null // null restores the default UA
        cookies().setAcceptCookie(true)
        cookies().setAcceptThirdPartyCookies(web, !app.blockThirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, app.forceDark)
        }
        s.javaScriptCanOpenWindowsAutomatically = !app.blockPopups
    }

    private fun client() = object : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (app.blockContent && ContentBlocker.isBlocked(request.url.host.orEmpty())) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url
            val scheme = u.scheme ?: return false
            if (scheme == "http" || scheme == "https") {
                val host = u.host ?: return false
                val leaving = app.scopeToSite && !incognito && request.isForMainFrame && request.hasGesture() &&
                    baseDomain(host) != baseDomain(home) && !isAuthHost(host)
                if (leaving) { openExternal(u); return true }
                return false
            }
            return when (scheme) {
                "about", "blob", "data", "javascript" -> false
                "file", "content" -> true
                else -> { handleSchemeUrl(u.toString()); true }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress.visibility = View.GONE
            toolbar.subtitle = Uri.parse(url).host
            if (app.javascript) Scripts.forPage(app, url).forEach { view.evaluateJavascript(it, null) }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val failing = request.url.toString()
            val esc = android.text.TextUtils.htmlEncode(failing)
            val html = "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<body style='font-family:sans-serif;padding:24px;color:#333'><h2>Can’t load this page</h2>" +
                "<p>${android.text.TextUtils.htmlEncode(error.description.toString())}</p>" +
                "<p><a href='$esc'>Try again</a></p></body>"
            view.loadDataWithBaseURL(failing, html, "text/html", "utf-8", failing)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            toast("The page crashed"); finish(); return true
        }
    }

    private fun chrome() = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) { progress.progress = newProgress }

        override fun onReceivedIcon(view: WebView, icon: Bitmap) {
            val host = Uri.parse(view.url ?: return).host ?: return
            if (!incognito && baseDomain(host) == baseDomain(home)) IconStore.savePageIcon(this@BrowserActivity, app.id, icon)
        }

        override fun onPermissionRequest(req: PermissionRequest) {
            runOnUiThread {
                val needs = ArrayList<String>()
                val grant = ArrayList<String>()
                for (r in req.resources) when (r) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> { needs += Manifest.permission.CAMERA; grant += r }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> { needs += Manifest.permission.RECORD_AUDIO; grant += r }
                }
                if (!app.allowMedia || grant.isEmpty()) { req.deny(); return@runOnUiThread }
                ask(needs) { got -> if (got.size == needs.size) req.grant(grant.toTypedArray()) else req.deny() }
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
            if (!app.allowLocation) { cb.invoke(origin, false, false); return }
            ask(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                cb.invoke(origin, it.isNotEmpty(), false)
            }
        }

        override fun onShowFileChooser(
            v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
        ): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = cb
            return try { fileLauncher.launch(params.createIntent()); true }
            catch (e: Exception) { fileCb = null; false }
        }

        override fun onCreateWindow(
            view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
        ): Boolean {
            if (app.blockPopups && !isUserGesture) return false
            // Pop-ups that the user asked for load in this same lite app window.
            val popup = WebView(this@BrowserActivity)
            profileName?.let {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.setProfile(popup, it)
            }
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    web.loadUrl(r.url.toString())
                    v.post { v.destroy() }
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowCustomView(view: View, cb: CustomViewCallback) {
            if (customView != null) { cb.onCustomViewHidden(); return }
            customView = view; customCb = cb
            frame.addView(view, MATCH, MATCH)
            root.visibility = View.GONE
            setBars(false)
        }

        override fun onHideCustomView() = hideCustomView()

        override fun onJsAlert(v: WebView, url: String, message: String, r: JsResult): Boolean {
            MaterialAlertDialogBuilder(this@BrowserActivity).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> r.confirm() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }

        override fun onJsConfirm(v: WebView, url: String, message: String, r: JsResult): Boolean {
            MaterialAlertDialogBuilder(this@BrowserActivity).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> r.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> r.cancel() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }
    }

    private fun hideCustomView() {
        customView?.let { frame.removeView(it) }
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        root.visibility = View.VISIBLE
        setBars(!app.fullscreen)
    }

    private fun setBars(show: Boolean) {
        WindowCompat.getInsetsController(window, frame).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (show) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ------------------------------------------------------------------------------------
    // Permissions, external links
    // ------------------------------------------------------------------------------------

    private fun ask(perms: List<String>, done: (List<String>) -> Unit) {
        val have = perms.filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        val missing = perms - have.toSet()
        if (missing.isEmpty()) { done(have); return }
        permCb = { r -> done(have + missing.filter { r[it] == true }) }
        permLauncher.launch(missing.toTypedArray())
    }

    private fun openExternal(u: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { toast("No app can open this link") }
    }

    private fun handleSchemeUrl(url: String) {
        try {
            val i = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (_: Exception) { toast("No app can open this link") }
    }

    // ------------------------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------------------------

    private fun download(url: String, ua: String, cd: String?, mime: String?) {
        if (!url.startsWith("http")) return
        val name = URLUtil.guessFileName(url, cd, mime)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            if (!mime.isNullOrBlank()) setMimeType(mime)
            addRequestHeader("User-Agent", ua)
            cookies().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        }
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        toast("Downloading $name")
    }

    /** blob: URLs only exist inside the page, so we read them there and hand the bytes back once. */
    private fun fetchBlob(url: String, mime: String) {
        val token = UUID.randomUUID().toString()
        blobToken = token
        web.evaluateJavascript(
            "(async function(){try{var r=await fetch(${JSONObject.quote(url)});var b=await r.blob();" +
                "var f=new FileReader();f.onloadend=function(){NutshellBlob.save(${JSONObject.quote(token)}," +
                "f.result,b.type||${JSONObject.quote(mime)})};f.readAsDataURL(b)}catch(e){}})()", null
        )
    }

    private inner class BlobBridge {
        @JavascriptInterface
        fun save(token: String, dataUrl: String, mime: String) {
            if (token != blobToken) return // only accept data we asked for
            blobToken = null
            try {
                val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                val name = "download_${System.currentTimeMillis()}.$ext"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                runOnUiThread { toast("Saved $name to Downloads") }
            } catch (_: Exception) {
                runOnUiThread { toast("Download failed") }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------------------------

    private fun buildMenu(fg: Int) {
        toolbar.menu.add(0, 1, 0, "Quick settings").apply {
            setIcon(R.drawable.ic_more_vert)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            icon?.mutate()?.setTint(fg)
        }
        toolbar.setOnMenuItemClickListener { panel.show(); true }
    }

    /** Toolbar vs. frameless vs. full-screen. */
    private fun applyChrome() {
        val bare = app.frameless || app.fullscreen
        toolbar.visibility = if (bare) View.GONE else View.VISIBLE
        moreBtn.visibility = if (bare) View.VISIBLE else View.GONE
        setBars(!app.fullscreen)
    }

    private fun save() { if (!incognito) Store.upsert(app) }

    // ---- QuickPanel.Host ---------------------------------------------------------------

    override fun liteApp(): LiteApp = app
    override fun isIncognito(): Boolean = incognito
    override fun canGoBack(): Boolean = web.canGoBack()
    override fun canGoForward(): Boolean = web.canGoForward()

    override fun onTile(t: QuickPanel.Tile) {
        val url = web.url.orEmpty()
        when (t) {
            QuickPanel.Tile.LITE_SETTINGS -> AppEditor.show(this, app, false) { saved ->
                app = saved; Store.upsert(saved); applySettings(); applyChrome(); web.reload()
            }
            QuickPanel.Tile.GLOBAL_SETTINGS -> GlobalSettings.show(this)
            QuickPanel.Tile.SHARE -> startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null))
            QuickPanel.Tile.COPY -> {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("link", url))
                toast("Link copied")
            }
            QuickPanel.Tile.EXTERNAL -> openExternal(Uri.parse(url))
            QuickPanel.Tile.PRINT -> {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                pm.print(app.name, web.createPrintDocumentAdapter(app.name), PrintAttributes.Builder().build())
            }
            QuickPanel.Tile.PIN -> pinShortcut(this, app)
            QuickPanel.Tile.CREATE -> createFromPage()
            QuickPanel.Tile.FIND -> findDialog()
            QuickPanel.Tile.READER -> web.evaluateJavascript(READER_JS, null)
            QuickPanel.Tile.SOURCE -> viewSource()
            QuickPanel.Tile.CLEAR -> clearData()
            QuickPanel.Tile.FEED -> subscribe()
            QuickPanel.Tile.TRANSLATE ->
                web.loadUrl("https://translate.google.com/translate?sl=auto&u=" + Uri.encode(url))
        }
    }

    override fun onToggle(t: QuickPanel.Toggle, on: Boolean) {
        when (t) {
            QuickPanel.Toggle.BLOCKER -> { app.blockContent = on; save(); web.reload() }
            QuickPanel.Toggle.POPUPS -> { app.blockPopups = on; save(); applySettings() }
            QuickPanel.Toggle.DESKTOP -> { app.desktopMode = on; save(); applySettings(); web.reload() }
            QuickPanel.Toggle.FRAMELESS -> { app.frameless = on; save(); applyChrome() }
            QuickPanel.Toggle.FULLSCREEN -> { app.fullscreen = on; save(); applyChrome() }
            QuickPanel.Toggle.DARK -> { app.forceDark = on; save(); applySettings() }
        }
    }

    override fun onScript(key: String, on: Boolean) {
        app.enabledScripts = if (on) (app.enabledScripts + key).distinct() else app.enabledScripts - key
        save()
        if (on) Scripts.forKey(key, web.url.orEmpty())?.let { web.evaluateJavascript(it, null) }
        else web.reload() // a running script can't be un-run, so start the page fresh
    }

    override fun onTextZoom(percent: Int, commit: Boolean) {
        app.textZoom = percent
        web.settings.textZoom = percent
        if (commit) save()
    }

    override fun onNav(n: QuickPanel.Nav) {
        when (n) {
            QuickPanel.Nav.HOME -> web.loadUrl(app.url)
            QuickPanel.Nav.BACK -> if (web.canGoBack()) web.goBack()
            QuickPanel.Nav.RELOAD -> web.reload()
            QuickPanel.Nav.FORWARD -> if (web.canGoForward()) web.goForward()
            QuickPanel.Nav.SETTINGS -> onTile(QuickPanel.Tile.LITE_SETTINGS)
        }
    }

    /** "Create a Lite App": starts from the page you're looking at. */
    private fun createFromPage() {
        val draft = LiteApp(
            name = web.title.orEmpty().take(30),
            url = web.url ?: app.url,
            color = PALETTE.random()
        )
        AppEditor.show(this, draft, true) { saved ->
            Store.upsert(saved)
            toast("Created “${saved.name}”")
            startActivity(launchIntent(this, saved.id))
        }
    }

    private fun findDialog() {
        val et = EditText(this).apply { isSingleLine = true; hint = "Find in page" }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        val d = MaterialAlertDialogBuilder(this).setView(box)
            .setPositiveButton("Next", null)
            .setNegativeButton("Previous", null)
            .setNeutralButton("Done", null)
            .setOnDismissListener { web.clearMatches() }
            .create()
        d.window?.apply {
            setGravity(Gravity.BOTTOM)
            setDimAmount(0f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        d.setOnShowListener {
            // Override the default click handlers so the bar stays open while you step through matches.
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { web.findNext(true) }
            d.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { web.findNext(false) }
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener { d.dismiss() }
            et.requestFocus()
        }
        et.doAfterTextChanged { web.findAllAsync(it.toString()) }
        d.show()
    }

    private fun viewSource() {
        web.evaluateJavascript("document.documentElement.outerHTML") { raw ->
            val html = (JSONTokener(raw).nextValue() as? String).orEmpty().take(300_000)
            val tv = TextView(this).apply {
                text = html
                setTextIsSelectable(true)
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            MaterialAlertDialogBuilder(this).setTitle("Page source")
                .setView(ScrollView(this).apply { addView(tv) })
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun subscribe() {
        val js = "(function(){var l=document.querySelector(" +
            "'link[rel=alternate][type*=rss],link[rel=alternate][type*=atom]');return l?l.href:''})()"
        web.evaluateJavascript(js) { raw ->
            val href = (JSONTokener(raw).nextValue() as? String).orEmpty()
            if (href.isBlank()) {
                toast("This page doesn’t advertise a feed")
            } else {
                app.feedUrl = href
                app.notify = true
                Store.upsert(app)
                toast("You’ll be notified about new posts")
                if (Build.VERSION.SDK_INT >= 33) ask(listOf(Manifest.permission.POST_NOTIFICATIONS)) { }
            }
        }
    }

    private fun clearData() {
        web.clearCache(true)
        web.clearHistory()
        cookies().removeAllCookies(null)
        (profile?.webStorage ?: WebStorage.getInstance()).deleteAllData()
        toast("Browsing data cleared")
    }
}

/** Replaces the page with a clean, readable version of its main content. */
private const val READER_JS = """(function(){
var c=document.querySelector('article')||document.querySelector('main');
if(!c){var best=null,bs=0;document.querySelectorAll('div,section').forEach(function(d){
var s=0;d.querySelectorAll(':scope > p').forEach(function(p){s+=p.innerText.length});
if(s>bs){bs=s;best=d}});c=best}
if(!c){return}
var clone=c.cloneNode(true);
clone.querySelectorAll('script,style,nav,aside,form,iframe,footer,header,noscript,button').forEach(function(e){e.remove()});
var title=document.title;
document.body.innerHTML='<div id="__reader"><h1></h1></div>';
document.querySelector('#__reader h1').textContent=title;
document.querySelector('#__reader').appendChild(clone);
document.head.insertAdjacentHTML('beforeend','<style>body{margin:0;background:#fafaf7;color:#222;font:18px/1.7 Georgia,serif}#__reader{max-width:42em;margin:auto;padding:16px}img,video{max-width:100%;height:auto}a{color:#0b57d0}</style>');
window.scrollTo(0,0)})();"""
