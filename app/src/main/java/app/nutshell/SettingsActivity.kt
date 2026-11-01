package app.nutshell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Lite App Settings" and everything beneath it. Pages are kept on a small stack so the back arrow
 * walks back up the same tree as the screen map: main → Behavior / Privacy / Theme / Tags /
 * Bookmarks / Feeds / Web Monitors / Search / Share, and main → Sandboxes, Privacy → Content Blocker.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP = "app_id"
        const val EXTRA_PAGE = "page"

        fun intent(ctx: Context, appId: String?, page: String? = null): Intent =
            Intent(ctx, SettingsActivity::class.java).apply {
                if (appId != null) putExtra(EXTRA_APP, appId)
                if (page != null) putExtra(EXTRA_PAGE, page)
            }
    }

    private lateinit var ui: Ui
    private lateinit var host: FrameLayout
    private var app: LiteApp? = null
    private val a get() = app!!
    private val stack = ArrayList<String>()
    private val expandedSandboxes = HashSet<String>()

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) setIconFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        app = intent.getStringExtra(EXTRA_APP)?.let { Store.get(it) }
        val start = intent.getStringExtra(EXTRA_PAGE) ?: "main"
        if (app == null && start != "sandboxes" && start != "blocker") { finish(); return }

        ui = Ui(this)
        host = FrameLayout(this).apply { setBackgroundColor(ui.cSurface) }
        setContentView(host)
        host.applySystemBarPadding()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        stack.add(start)
        render()
    }

    private fun back() {
        if (stack.size > 1) { stack.removeAt(stack.size - 1); render() } else finish()
    }

    private fun go(page: String) { stack.add(page); render() }
    private fun save() { app?.let { Store.upsert(it) } }

    private fun render() {
        host.removeAllViews()
        val v = when (stack.last()) {
            "main" -> mainPage()
            "behavior" -> behaviorPage()
            "privacy" -> privacyPage()
            "theme" -> themePage()
            "tags" -> tagsPage()
            "bookmarks" -> linksPage("Bookmarks", R.drawable.ic_bookmark, a.bookmarks,
                "Bookmark your frequently visited pages. Keep your favorite stuff on top.", { a.bookmarks = it },
                { c -> openBookmark(c) })
            "feeds" -> feedsPage()
            "monitors" -> monitorsPage()
            "search" -> searchPage()
            "share" -> sharePage()
            "sandboxes" -> sandboxesPage()
            "blocker" -> blockerPage()
            else -> mainPage()
        }
        host.addView(v, MATCH, MATCH)
    }

    // =========================================================================================
    // Main page
    // =========================================================================================

    private fun mainPage(): View {
        val p = ui.page("Lite App Settings") { back() }
        with(ui) {
            with(p.body) {
                // Sandbox block (no icon, indented under the title)
                addView(LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24) + dp(56), dp(8), dp(20), dp(12))
                    addView(TextView(this@SettingsActivity).apply {
                        text = "Sandbox"; textSize = 18f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(cText)
                    })
                    addView(TextView(this@SettingsActivity).apply {
                        text = "Sandboxes keep your Web browsing isolated in separate containers"
                        textSize = 15f; setTextColor(cSub)
                    })
                    addView(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(cCard, 28)
                        setPadding(dp(14), 0, dp(20), 0)
                        addView(sandboxIcon(a.sandbox, 26))
                        addView(TextView(this@SettingsActivity).apply {
                            text = Sandboxes.name(a.sandbox); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
                            setPadding(dp(14), 0, 0, 0)
                        })
                        setOnClickListener { sandboxSheet() }
                    }, LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(14) })
                    addView(TextView(this@SettingsActivity).apply {
                        text = "Global Settings"; textSize = 16f; setTextColor(cAccent)
                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        setPadding(0, dp(14), 0, dp(10))
                        setOnClickListener { go("sandboxes") }
                    })
                })

                addView(navRow(R.drawable.ic_speed, "Behavior", "Full-screen, Frameless, Desktop Mode, Custom User Agent") { go("behavior") })
                addView(navRow(R.drawable.ic_visibility_off, "Privacy", "Block Ads & Malware, Block Third-Party Cookies, Permissions") { go("privacy") })
                addView(navRow(R.drawable.ic_format_paint, "Theme", "Dark Mode, Icon, Theme") { go("theme") })
                addView(navRow(R.drawable.ic_label, "Tags", "Stay organized with tags") { go("tags") })
                addView(navRow(R.drawable.ic_bookmark, "Bookmarks", "Bookmark your frequently visited pages. Keep your favorite stuff on top.") { go("bookmarks") })
                addView(navRow(R.drawable.ic_rss, "Feeds", "Add a feed URL to view notifications.") { go("feeds") })
                addView(navRow(R.drawable.ic_rss, "Web Monitors", "Track changes to any page on the Web") { go("monitors") })
                addView(navRow(R.drawable.ic_search, "Search", "Add a search URL to search from within Lite Apps") { go("search") })
                addView(navRow(R.drawable.ic_share, "Share", "Share links with Lite Apps from other Android apps") { go("share") })

                addView(divider())
                addView(subheader("More Settings"))
                addView(navRow(R.drawable.ic_exit_to_app, "Global Settings", "Global Settings for all Lite Apps") { GlobalSettings.show(this@SettingsActivity) })

                addView(divider())
                addView(subheader("About"))
                addView(navRow(R.drawable.ic_apps, "Add to Home Screen") { pinShortcut(this@SettingsActivity, a) })
                addView(navRow(R.drawable.ic_share, "Share") {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.url), null))
                })
                addView(navRow(R.drawable.ic_remove_circle, "Delete") { confirmDelete() })

                addView(divider())
                addView(subheader("Name your Lite App"))
                addView(navRow(R.drawable.ic_public, "Title", a.name) {
                    prompt("Title", a.name) { a.name = it; save(); render() }
                })
                addView(navRow(R.drawable.ic_public, "URL", a.url) {
                    prompt("URL", a.url, uri = true, validate = ::urlError) { a.url = AppEditor.normalizeUrl(it); save(); render() }
                })
            }
        }
        return p.root
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this).setMessage("Delete “${a.name}”?")
            .setPositiveButton("Delete") { _, _ ->
                Store.delete(a.id)
                IconStore.delete(applicationContext, a.id)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    // ---- sandbox picker (bottom sheet) -----------------------------------------------------

    private fun sandboxSheet() {
        val dlg = BottomSheetDialog(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(16))
        }
        col.addView(TextView(this).apply { text = "Sandbox"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ui.cText); setPadding(ui.dp(8), 0, 0, ui.dp(8)) })
        col.addView(TextView(this).apply {
            text = "Use multiple accounts on any site, all active at the same time"
            textSize = 17f; setTextColor(ui.cSub); setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(16))
        })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Sandboxes.all.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { sb ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10))
                    background = ui.ripple()
                    alpha = if (sb.id == a.sandbox) 1f else 0.85f
                    addView(ui.sandboxIcon(sb.id, 28))
                    addView(TextView(this@SettingsActivity).apply {
                        text = Sandboxes.name(sb.id); textSize = 18f
                        typeface = Typeface.DEFAULT_BOLD; setTextColor(if (sb.id == a.sandbox) ui.cAccent else ui.cText)
                        setPadding(ui.dp(16), 0, 0, 0)
                    })
                    setOnClickListener { a.sandbox = sb.id; save(); dlg.dismiss(); render() }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        col.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        col.addView(ui.pill("Global Settings", R.drawable.ic_settings) { dlg.dismiss(); go("sandboxes") },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = ui.dp(12) })
        dlg.setContentView(col)
        dlg.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dlg.behavior.skipCollapsed = true
        dlg.show()
    }

    // =========================================================================================
    // Behavior / Privacy
    // =========================================================================================

    private fun behaviorPage(): View {
        val p = ui.page("Behavior") { back() }
        with(ui) {
            with(p.body) {
                addView(switchRow(R.drawable.ic_fullscreen, "Full-screen", a.fullscreen) { a.fullscreen = it; save() })
                addView(switchRow(R.drawable.ic_crop_free, "Frameless", a.frameless) { a.frameless = it; save() })
                addView(switchRow(R.drawable.ic_refresh, "Pull to refresh", a.pullToRefresh) { a.pullToRefresh = it; save() })
                addView(divider())
                addView(subheader("Content"))
                addView(switchRow(R.drawable.ic_image, "Load images", a.loadImages) { a.loadImages = it; save() })
                addView(switchRow(R.drawable.ic_desktop, "Desktop Mode", a.desktopMode) { a.desktopMode = it; save() })
                addView(switchRow(R.drawable.ic_speed, "Data Saver mode", a.dataSaver) { a.dataSaver = it; save() })
                addView(switchRow(R.drawable.ic_extension, "JavaScript", a.javascript) { a.javascript = it; save() })
                addView(navRow(R.drawable.ic_desktop, "Custom User Agent", userAgentLabel()) { userAgentDialog() })
            }
        }
        return p.root
    }

    private fun userAgentLabel() = when (a.userAgent) {
        "" -> "default"
        "desktop" -> "desktop"
        else -> a.userAgent
    }

    private fun userAgentDialog() {
        val options = arrayOf("Default", "Desktop", "Custom…")
        val checked = when (a.userAgent) { "" -> 0; "desktop" -> 1; else -> 2 }
        MaterialAlertDialogBuilder(this).setTitle("Custom User Agent")
            .setSingleChoiceItems(options, checked) { d, i ->
                d.dismiss()
                when (i) {
                    0 -> { a.userAgent = ""; save(); render() }
                    1 -> { a.userAgent = "desktop"; save(); render() }
                    else -> ui.prompt("User agent string", if (checked == 2) a.userAgent else "") { a.userAgent = it; save(); render() }
                }
            }.show()
    }

    private fun privacyPage(): View {
        val p = ui.page("Privacy") { back() }
        with(ui) {
            with(p.body) {
                addView(subheader("Content Blocker"))
                addView(switchRow(R.drawable.ic_block, "Block Ads & Malware", a.blockContent) { a.blockContent = it; save() })
                addView(navRow(R.drawable.ic_settings, "Global Settings") { go("blocker") })
                addView(divider())
                addView(subheader("Privacy"))
                addView(switchRow(R.drawable.ic_visibility_off, "Block Popups", a.blockPopups) { a.blockPopups = it; save() })
                addView(switchRow(R.drawable.ic_visibility_off, "Block Third-Party Cookies", a.blockThirdPartyCookies) { a.blockThirdPartyCookies = it; save() })
                addView(switchRow(R.drawable.ic_open_in_new, "Open links in Lite App", a.openLinksInApp) { a.openLinksInApp = it; save() })
                addView(switchRow(R.drawable.ic_android, "Launch Native Apps", a.launchNativeApps) { a.launchNativeApps = it; save() })
                addView(switchRow(R.drawable.ic_play_store, "Install Native Apps", a.installNativeApps) { a.installNativeApps = it; save() })
                addView(divider())
                addView(subheader("Permissions"))
                addView(switchRow(R.drawable.ic_location, "Location", a.allowLocation) { a.allowLocation = it; save() })
                addView(switchRow(R.drawable.ic_image, "Photos, Videos, and Files", a.allowFiles) { a.allowFiles = it; save() })
                addView(switchRow(R.drawable.ic_mic, "Camera and Microphone", a.allowMedia) { a.allowMedia = it; save() })
            }
        }
        return p.root
    }

    // =========================================================================================
    // Theme (mode, icon, color)
    // =========================================================================================

    private fun themePage(): View {
        val p = ui.page("Theme") { back() }
        with(ui) {
            with(p.body) {
                val indent = dp(24) + dp(56)

                val group = MaterialButtonToggleGroup(this@SettingsActivity).apply {
                    isSingleSelection = true
                    isSelectionRequired = true
                    listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (mode, label) ->
                        val b = MaterialButton(this@SettingsActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            id = View.generateViewId(); text = label; isAllCaps = false; tag = mode
                        }
                        addView(b)
                        if (a.themeMode == mode) check(b.id)
                    }
                    addOnButtonCheckedListener { g, checkedId, isChecked ->
                        if (isChecked) { a.themeMode = g.findViewById<View>(checkedId).tag as String; save() }
                    }
                }
                addView(group, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(indent, dp(4), 0, dp(20)) })

                addView(TextView(this@SettingsActivity).apply {
                    text = "Icon"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText); setPadding(indent, dp(8), 0, dp(10))
                })
                val preview = ImageView(this@SettingsActivity).apply {
                    setImageBitmap(IconStore.load(context, a.id) ?: letterIcon(a.name, a.color))
                    setOnClickListener { resetIconDialog() }
                }
                fun circleButton(icon: Int, click: () -> Unit) = ImageView(this@SettingsActivity).apply {
                    background = GradientDrawableOval(cCard)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    setImageDrawable(tinted(icon))
                    setOnClickListener { click() }
                }
                addView(LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(indent, 0, dp(16), 0)
                    addView(preview, LinearLayout.LayoutParams(dp(60), dp(60)))
                    listOf<Pair<Int, () -> Unit>>(
                        R.drawable.ic_image to { pickImage.launch("image/*") },
                        R.drawable.ic_edit to { drawIconDialog() },
                        R.drawable.ic_text_fields to { textIconDialog() }
                    ).forEach { (icon, click) ->
                        addView(circleButton(icon, click), LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(16) })
                    }
                })

                addView(TextView(this@SettingsActivity).apply {
                    text = "Color"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText); setPadding(indent, dp(28), 0, dp(10))
                })
                val hex = EditText(this@SettingsActivity).apply {
                    setText(hexOf(a.color)); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
                    isSingleLine = true
                    filters = arrayOf(android.text.InputFilter.LengthFilter(7))
                    setOnEditorActionListener { v, _, _ -> applyHex(v as EditText); true }
                    setOnFocusChangeListener { v, focus -> if (!focus) applyHex(v as EditText) }
                }
                val swatch = ImageView(this@SettingsActivity).apply {
                    background = GradientDrawableOval(a.color)
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    setImageDrawable(tinted(R.drawable.ic_palette, a.color.onColor()))
                    setOnClickListener { colorDialog() }
                }
                addView(LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(indent, 0, dp(16), 0)
                    addView(swatch, LinearLayout.LayoutParams(dp(52), dp(52)))
                    addView(hex, LinearLayout.LayoutParams(dp(150), WRAP).apply { leftMargin = dp(16) })
                })
            }
        }
        return p.root
    }

    private fun GradientDrawableOval(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
    }

    private fun hexOf(c: Int) = String.format("#%06x", c and 0xFFFFFF)

    private fun applyHex(et: EditText) {
        try {
            val parsed = Color.parseColor(et.text.toString().trim()) or (0xFF shl 24)
            if (parsed == a.color) return
            a.color = parsed
            save()
            render()
        } catch (_: IllegalArgumentException) {
            toast("Colors look like #1e88e5")
            et.setText(hexOf(a.color))
        }
    }

    private fun colorDialog() {
        val dlg = MaterialAlertDialogBuilder(this).setTitle("Color")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0) }
        var d: androidx.appcompat.app.AlertDialog? = null
        PALETTE.toList().chunked(4).forEach { rowColors ->
            val row = LinearLayout(this)
            rowColors.forEach { c ->
                row.addView(View(this).apply {
                    background = GradientDrawableOval(c)
                    setOnClickListener { a.color = c; save(); d?.dismiss(); render() }
                }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { setMargins(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) })
            }
            grid.addView(row)
        }
        d = dlg.setView(grid).setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun setCustomIcon(bmp: Bitmap) {
        IconStore.setCustom(applicationContext, a.id, bmp)
        a.iconCustom = true
        save()
        render()
    }

    private fun setIconFromUri(uri: Uri) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
            }
            if (bmp == null) toast("Couldn’t read that image") else setCustomIcon(bmp)
        }
    }

    private fun textIconDialog() {
        ui.prompt("Letters (1–3)", a.name.take(1).uppercase(), validate = { if (it.isBlank() || it.length > 3) "Use 1 to 3 characters" else null }) { letters ->
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = a.color
            c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, paint)
            paint.color = a.color.onColor()
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = if (letters.length == 1) size * 0.55f else size * 0.4f
            c.drawText(letters.uppercase(), size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
            setCustomIcon(bmp)
        }
    }

    private fun drawIconDialog() {
        val dv = DrawView(this).apply { canvasColor = a.color; penColor = a.color.onColor() }
        val colors = listOf(Color.BLACK, Color.WHITE, 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt())
        val palette = LinearLayout(this).apply {
            colors.forEach { c ->
                addView(View(this@SettingsActivity).apply {
                    background = GradientDrawableOval(c)
                    setOnClickListener { dv.penColor = c }
                }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)).apply { setMargins(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8)) })
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0)
            addView(dv, LinearLayout.LayoutParams(ui.dp(260), ui.dp(260)))
            addView(palette)
        }
        MaterialAlertDialogBuilder(this).setTitle("Draw an icon").setView(col)
            .setPositiveButton("Save") { _, _ -> setCustomIcon(dv.toBitmap(192)) }
            .setNeutralButton("Clear") { _, _ -> }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { d -> d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener { dv.clear() } }
    }

    private fun resetIconDialog() {
        MaterialAlertDialogBuilder(this).setMessage("Use the website’s own icon again?")
            .setPositiveButton("Use site icon") { _, _ ->
                IconStore.delete(applicationContext, a.id)
                a.iconCustom = false
                save()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { try { IconStore.fetch(applicationContext, a) } catch (_: Exception) { false } }
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    // =========================================================================================
    // Tags
    // =========================================================================================

    private fun tagsPage(): View {
        val p = ui.page("Tags") { back() }
        with(ui) {
            with(p.body) {
                Store.tags().forEach { tag ->
                    addView(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = ripple()
                        setPadding(dp(20), dp(10), dp(20), dp(10))
                        val cb = CheckBox(this@SettingsActivity).apply { isChecked = tag in a.tags }
                        cb.setOnCheckedChangeListener { _, on ->
                            a.tags = if (on) (a.tags + tag).distinct() else a.tags - tag
                            save()
                        }
                        addView(cb)
                        addView(TextView(this@SettingsActivity).apply {
                            text = tag; textSize = 18f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            setTextColor(cText); setPadding(dp(16), 0, 0, 0)
                        })
                        setOnClickListener { cb.toggle() }
                    })
                }
                addView(infoRow("Stay organized with tags"))
            }
            p.fab(R.drawable.ic_add) {
                prompt("Tag", okLabel = "OK") { t ->
                    if (t !in Store.tags()) Store.saveTags(Store.tags() + t)
                    render()
                }
            }
        }
        return p.root
    }

    // =========================================================================================
    // Bookmarks / Feeds / Web Monitors / Search / Share
    // =========================================================================================

    private fun openBookmark(l: Link) {
        startActivity(BrowserActivity.launchIntent(this, a.id, l.url))
    }

    private fun linksPage(
        title: String, emptyIcon: Int, items: List<Link>, info: String,
        set: (List<Link>) -> Unit, onOpen: ((Link) -> Unit)?
    ): View {
        val p = ui.page(title) { back() }
        with(ui) {
            with(p.body) {
                if (items.isEmpty()) addView(emptyCard(emptyIcon))
                items.forEach { l ->
                    addView(itemCard(l.title, l.url, onOpen?.let { f -> { f(l) } }) {
                        set(items - l); save(); render()
                    })
                }
                addView(infoRow(info))
            }
            p.fab(R.drawable.ic_add) {
                form(listOf(
                    Ui.Field("Title"),
                    Ui.Field("URL", a.url, uri = true, validate = ::urlError)
                )) { v -> set(items + Link(v[0], AppEditor.normalizeUrl(v[1]))); save(); render() }
            }
        }
        return p.root
    }

    private fun feedsPage(): View {
        val p = ui.page("Feeds") { back() }
        with(ui) {
            with(p.body) {
                if (a.feeds.isEmpty()) addView(emptyCard(R.drawable.ic_rss))
                a.feeds.forEach { f -> addView(itemCard(f.title, f.url, null) { a.feeds = a.feeds - f; save(); render() }) }
                addView(infoRow("Add a feed URL to view notifications."))
            }
            p.fab(R.drawable.ic_add) {
                form(
                    listOf(Ui.Field("Title"), Ui.Field("URL", a.url, uri = true, validate = ::urlError)),
                    test = { v, done ->
                        lifecycleScope.launch {
                            val n = withContext(Dispatchers.IO) {
                                try { FeedParser.parse(Net.get(AppEditor.normalizeUrl(v[1]))).size } catch (_: Exception) { -1 }
                            }
                            if (n > 0) done(true, "Feed works ($n items)") else done(false, "That doesn’t look like an RSS or Atom feed")
                        }
                    },
                    testHint = ERR_TEST_FEED
                ) { v ->
                    a.feeds = a.feeds + Link(v[0], AppEditor.normalizeUrl(v[1]))
                    save()
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    render()
                }
            }
        }
        return p.root
    }

    private fun monitorsPage(): View {
        val p = ui.page("Web Monitors") { back() }
        with(ui) {
            with(p.body) {
                if (a.monitors.isEmpty()) addView(emptyCard(R.drawable.ic_rss))
                a.monitors.forEach { m ->
                    addView(itemCard(m.title, "${m.url}\n${m.selector}", null) { a.monitors = a.monitors - m; save(); render() })
                }
                addView(infoRow("Track changes to any page on the Web"))
            }
            p.fab(R.drawable.ic_add) {
                form(
                    listOf(
                        Ui.Field("Title"),
                        Ui.Field("URL", a.url, uri = true, validate = ::urlError),
                        Ui.Field("CSS Selector")
                    ),
                    test = { v, done ->
                        lifecycleScope.launch {
                            val text = withContext(Dispatchers.IO) {
                                try { PageMonitor.read(AppEditor.normalizeUrl(v[1]), v[2]) } catch (_: Exception) { null }
                            }
                            when {
                                text == null -> done(false, "Couldn’t load that page or read that selector")
                                text.isEmpty() -> done(false, "Nothing on the page matches that selector")
                                else -> done(true, "Found: " + text.take(80))
                            }
                        }
                    },
                    testHint = ERR_TEST_MONITOR
                ) { v ->
                    a.monitors = a.monitors + Monitor(v[0], AppEditor.normalizeUrl(v[1]), v[2])
                    save()
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    render()
                }
            }
        }
        return p.root
    }

    private fun searchPage(): View = linkTemplatePage(
        "Search", R.drawable.ic_search, a.searches, "Add a search URL to search from within Lite Apps",
        ::searchUrlError, { a.searches = it }
    )

    private fun sharePage(): View = linkTemplatePage(
        "Share", R.drawable.ic_share, a.shares, "Share links with Lite Apps from other Android apps",
        ::shareUrlError, { a.shares = it }
    )

    /** Search and Share pages: the URL is a template with placeholders, so it is validated differently. */
    private fun linkTemplatePage(
        title: String, icon: Int, items: List<Link>, info: String,
        validate: (String) -> String?, set: (List<Link>) -> Unit
    ): View {
        val p = ui.page(title) { back() }
        with(ui) {
            with(p.body) {
                if (items.isEmpty()) addView(emptyCard(icon))
                items.forEach { l -> addView(itemCard(l.title, l.url, null) { set(items - l); save(); render() }) }
                addView(infoRow(info))
            }
            p.fab(R.drawable.ic_add) {
                form(listOf(
                    Ui.Field("Title"),
                    Ui.Field("URL", a.url, uri = true, validate = validate)
                )) { v -> set(items + Link(v[0], AppEditor.normalizeUrl(v[1]).replace("%25", "%"))); save(); render() }
            }
        }
        return p.root
    }

    // =========================================================================================
    // Sandboxes (global)
    // =========================================================================================

    private fun fmtSize(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format("%.1f kB", b / 1024.0)
        else -> String.format("%.1f MB", b / (1024.0 * 1024.0))
    }

    private fun sandboxesPage(): View {
        val p = ui.page("Sandboxes") { back() }
        with(ui) {
            with(p.body) {
                Sandboxes.all.forEach { sb ->
                    val open = sb.id in expandedSandboxes
                    val card = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = rounded(cCard, 14)
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                    }
                    card.addView(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(4), 0, dp(4))
                        addView(sandboxIcon(sb.id, 26))
                        addView(TextView(this@SettingsActivity).apply {
                            text = Sandboxes.name(sb.id); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
                            setPadding(dp(16), 0, 0, 0)
                        }, LinearLayout.LayoutParams(0, WRAP, 1f))
                        if (open && Sandboxes.editable(sb.id)) addView(android.widget.ImageButton(this@SettingsActivity).apply {
                            setImageDrawable(tinted(R.drawable.ic_edit)); background = ripple(true)
                            setOnClickListener {
                                prompt("Name", Sandboxes.name(sb.id)) { Store.setSandboxName(sb.id, it); render() }
                            }
                        }, LinearLayout.LayoutParams(dp(40), dp(40)))
                        addView(ImageView(this@SettingsActivity).apply {
                            setImageDrawable(tinted(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more))
                        }, LinearLayout.LayoutParams(dp(24), dp(24)))
                        setOnClickListener {
                            if (!expandedSandboxes.add(sb.id)) expandedSandboxes.remove(sb.id)
                            render()
                        }
                    })
                    if (open) {
                        fun sizeRow(label: String, size: () -> Long, clear: () -> Unit) {
                            val tv = TextView(this@SettingsActivity).apply { text = "…"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText) }
                            lifecycleScope.launch { tv.text = fmtSize(withContext(Dispatchers.IO) { size() }) }
                            card.addView(LinearLayout(this@SettingsActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dp(4), dp(8), 0, dp(4))
                                addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))
                                addView(pill(label, R.drawable.ic_delete) { clear() })
                            })
                        }
                        val ctx = applicationContext
                        sizeRow("Clear cache", { Sandboxes.cacheSize(ctx, sb.id) }) {
                            Sandboxes.clearCache(this@SettingsActivity, sb.id); toast("Cache cleared"); render()
                        }
                        sizeRow("Clear storage", { Sandboxes.storageSize(ctx, sb.id) }) {
                            MaterialAlertDialogBuilder(this@SettingsActivity)
                                .setMessage("Clear cookies and site data for “${Sandboxes.name(sb.id)}”? You’ll be signed out of sites that use it.")
                                .setPositiveButton("Clear") { _, _ -> Sandboxes.clearStorage(this@SettingsActivity, sb.id); toast("Storage cleared"); render() }
                                .setNegativeButton(android.R.string.cancel, null).show()
                        }
                        Sandboxes.usedBy(sb.id).forEach { name ->
                            card.addView(TextView(this@SettingsActivity).apply {
                                text = name; textSize = 15f; setTextColor(cSub); setPadding(dp(4), dp(6), 0, 0)
                            })
                        }
                    }
                    addView(card, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(12), dp(3), dp(12), dp(3)) })
                }
                addView(infoRow("Sandboxes keep your Web browsing isolated in separate containers"))
                addView(TextView(this@SettingsActivity).apply {
                    text = "Learn more"; textSize = 16f; setTextColor(cAccent)
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    setPadding(dp(24) + dp(56), 0, 0, dp(12))
                    setOnClickListener {
                        MaterialAlertDialogBuilder(this@SettingsActivity).setTitle("Sandboxes")
                            .setMessage("Lite apps that share a sandbox share cookies, logins and site data. Give two lite apps different sandboxes to stay signed in to two accounts on the same site at once. Incognito forgets everything when the lite app closes.")
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                })
            }
        }
        return p.root
    }

    // =========================================================================================
    // Content Blocker (global)
    // =========================================================================================

    private fun blockerPage(): View {
        val p = ui.page("Content Blocker") { back() }
        val ctx = applicationContext
        with(ui) {
            with(p.body) {
                val updated = Store.blockListsUpdatedAt
                addView(TextView(this@SettingsActivity).apply {
                    text = "${ContentBlocker.size()} hosts"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
                    setPadding(dp(20), 0, dp(20), 0)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = if (updated == 0L) "Not updated yet" else
                        DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString() +
                            ", " + android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(updated))
                    textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cSub); setPadding(dp(20), 0, dp(20), dp(12))
                })
                Store.blockLists().forEach { l ->
                    addView(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(20), dp(8), dp(8), dp(8))
                        val cb = CheckBox(this@SettingsActivity).apply { isChecked = l.enabled }
                        cb.setOnCheckedChangeListener { _, on ->
                            Store.saveBlockLists(Store.blockLists().map { if (it.id == l.id) it.copy(enabled = on) else it })
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    if (on && l.hosts < 0) try { ContentBlocker.download(ctx, l) } catch (_: Exception) { }
                                    ContentBlocker.load(ctx)
                                }
                                render()
                            }
                        }
                        addView(cb)
                        addView(LinearLayout(this@SettingsActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(16), 0, 0, 0)
                            addView(TextView(this@SettingsActivity).apply {
                                text = l.name; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
                                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                            })
                            addView(TextView(this@SettingsActivity).apply {
                                text = if (l.hosts < 0) "Not downloaded yet" else "${l.hosts} hosts"
                                textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cSub)
                            })
                        }, LinearLayout.LayoutParams(0, WRAP, 1f))
                        addView(android.widget.ImageButton(this@SettingsActivity).apply {
                            setImageDrawable(tinted(R.drawable.ic_remove_circle)); background = ripple(true)
                            setOnClickListener {
                                MaterialAlertDialogBuilder(this@SettingsActivity).setMessage("Remove “${l.name}”?")
                                    .setPositiveButton("Remove") { _, _ -> ContentBlocker.remove(ctx, l.id); render() }
                                    .setNegativeButton(android.R.string.cancel, null).show()
                            }
                        }, LinearLayout.LayoutParams(dp(48), dp(48)))
                        setOnClickListener { cb.toggle() }
                    })
                }
                addView(LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(28), dp(20), dp(12))
                    addView(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(ImageView(this@SettingsActivity).apply { setImageDrawable(tinted(R.drawable.ic_info)) },
                            LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(24) })
                        addView(TextView(this@SettingsActivity).apply {
                            text = "Block Ads & Malware"; textSize = 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(cSub)
                        })
                    })
                    addView(TextView(this@SettingsActivity).apply {
                        text = "Learn more"; textSize = 16f; setTextColor(cAccent)
                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        setPadding(dp(48), dp(14), 0, 0)
                        setOnClickListener { blockerHelp() }
                    })
                })
            }
            p.fab(R.drawable.ic_add, slot = 0) {
                form(
                    listOf(Ui.Field("Title"), Ui.Field("URL", uri = true, validate = ::urlError)),
                    help = { blockerHelp() }
                ) { v ->
                    val list = BlockList("custom-" + System.currentTimeMillis(), v[0], AppEditor.normalizeUrl(v[1]), true, -1)
                    Store.saveBlockLists(Store.blockLists() + list)
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            try { ContentBlocker.download(ctx, list) } catch (_: Exception) { }
                            ContentBlocker.load(ctx)
                        }
                        render()
                    }
                    render()
                }
            }
            p.fab(R.drawable.ic_refresh, slot = 1, mini = true) {
                toast("Updating block lists…")
                lifecycleScope.launch {
                    val n = withContext(Dispatchers.IO) { ContentBlocker.refresh(ctx) }
                    toast("$n hosts blocked")
                    render()
                }
            }
        }
        return p.root
    }

    private fun blockerHelp() {
        MaterialAlertDialogBuilder(this).setTitle("Block lists")
            .setMessage("Add the address of a public “hosts” list (lines like 0.0.0.0 ads.example.com) or a plain list of domains. " +
                "Requests to any listed domain, and its subdomains, are blocked in lite apps that have Block Ads & Malware turned on. " +
                "Lists are refreshed weekly, or tap the refresh button to update them now.")
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
