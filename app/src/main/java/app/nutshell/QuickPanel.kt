package app.nutshell

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.R as M
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/**
 * The "Quick Settings" side panel opened from the toolbar's three-dot button:
 * settings buttons, action tiles, per-app user scripts, toggle tiles, text-size slider and a nav row.
 */
class QuickPanel(
    private val act: AppCompatActivity,
    private val host: Host,
    private val container: FrameLayout
) {
    interface Host {
        fun liteApp(): LiteApp
        fun isIncognito(): Boolean
        fun canGoBack(): Boolean
        fun canGoForward(): Boolean
        fun onTile(t: Tile)
        fun onToggle(t: Toggle, on: Boolean)
        fun onScript(key: String, on: Boolean)
        fun onTextZoom(percent: Int, commit: Boolean)
        fun onNav(n: Nav)
    }

    enum class Tile { LITE_SETTINGS, GLOBAL_SETTINGS, SHARE, COPY, EXTERNAL, PRINT, PIN, CREATE, FIND, READER, SOURCE, CLEAR, FEED, BOOKMARKS, SEARCH, TRANSLATE }
    enum class Toggle { BLOCKER, POPUPS, DESKTOP, FRAMELESS, FULLSCREEN, DARK }
    enum class Nav { HOME, BACK, RELOAD, FORWARD, SETTINGS }

    private var overlay: FrameLayout? = null
    private var panel: View? = null
    val isShowing: Boolean get() = overlay != null

    private var cPanel = 0
    private var cTile = 0
    private var cText = 0
    private var cActive = 0
    private var cOnActive = 0
    private var cLine = 0

    private fun color(attr: Int, fallback: Int) = MaterialColors.getColor(act, attr, fallback)
    private fun dp(v: Int) = act.dp(v)

    private fun rounded(c: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun tinted(@DrawableRes res: Int, tint: Int): Drawable =
        ContextCompat.getDrawable(act, res)!!.mutate().apply { setTint(tint) }

    // ------------------------------------------------------------------------------------

    fun show() {
        if (overlay != null) return
        val app = host.liteApp()
        cPanel = color(M.attr.colorSurfaceContainer, Color.DKGRAY)
        cTile = color(M.attr.colorSurfaceContainerHigh, Color.GRAY)
        cText = color(M.attr.colorOnSurface, Color.WHITE)
        cActive = color(M.attr.colorPrimaryContainer, Color.CYAN)
        cOnActive = color(M.attr.colorOnPrimaryContainer, Color.BLACK)
        cLine = color(M.attr.colorOutlineVariant, Color.GRAY)

        val content = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        content.addView(TextView(act).apply {
            text = "Quick Settings"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
            setPadding(dp(4), dp(8), 0, dp(8))
        })
        if (!host.isIncognito()) {
            content.addView(chip("Lite App Settings", R.drawable.ic_settings) { closeThen { host.onTile(Tile.LITE_SETTINGS) } })
        }
        content.addView(chip("Global Settings", R.drawable.ic_settings) { closeThen { host.onTile(Tile.GLOBAL_SETTINGS) } })
        content.addView(Space(act), LinearLayout.LayoutParams(MATCH, dp(12)))

        // --- action tiles
        val actions = mutableListOf<View>()
        actions += actionTile("Share", R.drawable.ic_share, Tile.SHARE)
        actions += actionTile("Copy URL", R.drawable.ic_public, Tile.COPY)
        actions += actionTile("Open in browser", R.drawable.ic_public, Tile.EXTERNAL)
        actions += actionTile("Print", R.drawable.ic_print, Tile.PRINT)
        if (!host.isIncognito()) actions += actionTile("Add to Home Screen", R.drawable.ic_add, Tile.PIN)
        actions += actionTile("Create a Lite App", R.drawable.ic_add, Tile.CREATE)
        actions += actionTile("Find in page", R.drawable.ic_search, Tile.FIND)
        if (!host.isIncognito()) actions += actionTile("Bookmarks", R.drawable.ic_bookmark, Tile.BOOKMARKS)
        if (app.searches.isNotEmpty()) actions += actionTile("Search site", R.drawable.ic_search, Tile.SEARCH)
        actions += actionTile("Reader", R.drawable.ic_menu_book, Tile.READER)
        actions += actionTile("View source", R.drawable.ic_code, Tile.SOURCE)
        actions += actionTile("Clear data", R.drawable.ic_delete, Tile.CLEAR)
        if (!host.isIncognito()) actions += actionTile("Feed alerts", R.drawable.ic_rss, Tile.FEED)
        content.addView(grid(actions))
        content.addView(divider())

        // --- user scripts
        val scriptsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val chevron = ImageView(act).apply { setImageDrawable(tinted(R.drawable.ic_expand_less, cText)) }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            addView(ImageView(act).apply { setImageDrawable(tinted(R.drawable.ic_extension, cText)) },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
            addView(TextView(act).apply {
                text = "User Scripts"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)))
            setOnClickListener {
                val open = scriptsBox.visibility != View.VISIBLE
                scriptsBox.visibility = if (open) View.VISIBLE else View.GONE
                chevron.setImageDrawable(tinted(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, cText))
            }
        }
        content.addView(header)
        scriptsBox.addView(chip("Global Settings", R.drawable.ic_settings) { closeThen { host.onTile(Tile.GLOBAL_SETTINGS) } })
        Scripts.builtins.forEach { b ->
            scriptsBox.addView(
                if (b.js == null) scriptRow(b.name, b.icon, null, false) { closeThen { host.onTile(Tile.TRANSLATE) } }
                else scriptRow(b.name, b.icon, b.key, b.key in app.enabledScripts, null)
            )
        }
        Store.userScripts().forEach { src ->
            val key = Scripts.userKey(src)
            scriptsBox.addView(scriptRow(UserScript(src).name, R.drawable.ic_extension, key, key in app.enabledScripts, null))
        }
        content.addView(scriptsBox)
        content.addView(divider())

        // --- toggle tiles
        val toggles = mutableListOf<View>()
        toggles += toggleTile("Content Blocker", R.drawable.ic_visibility_off, app.blockContent, Toggle.BLOCKER)
        toggles += toggleTile("Block Popups", R.drawable.ic_visibility_off, app.blockPopups, Toggle.POPUPS)
        toggles += toggleTile("Desktop Mode", R.drawable.ic_desktop, app.desktopMode, Toggle.DESKTOP)
        toggles += toggleTile("Frameless", R.drawable.ic_crop_free, app.frameless, Toggle.FRAMELESS)
        toggles += toggleTile("Full-screen", R.drawable.ic_fullscreen, app.fullscreen, Toggle.FULLSCREEN)
        toggles += toggleTile("Dark Mode", R.drawable.ic_brightness, app.themeMode == "dark", Toggle.DARK)
        content.addView(grid(toggles))
        content.addView(divider())

        // --- text size
        content.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(TextView(act).apply { text = "Tt"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText) })
            addView(SeekBar(act).apply {
                max = 15
                progress = ((app.textZoom - 50) / 10).coerceIn(0, 15)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (fromUser) host.onTextZoom(50 + p * 10, false)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) = host.onTextZoom(50 + sb.progress * 10, true)
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(12), 0, dp(12), 0) })
            addView(TextView(act).apply { text = "Tt"; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText) })
        })

        // --- bottom nav row (fixed)
        val nav = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            addView(navButton(R.drawable.ic_first_page, true, false, Nav.HOME))
            addView(navButton(R.drawable.ic_arrow_back, host.canGoBack(), false, Nav.BACK))
            addView(navButton(R.drawable.ic_refresh, true, false, Nav.RELOAD))
            addView(navButton(R.drawable.ic_arrow_forward, host.canGoForward(), false, Nav.FORWARD))
            if (!host.isIncognito()) addView(navButton(R.drawable.ic_settings, true, true, Nav.SETTINGS))
        }

        val p = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            val r = dp(28).toFloat()
            background = GradientDrawable().apply {
                setColor(cPanel)
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            isClickable = true // swallow touches so they don't reach the scrim
            addView(ScrollView(act).apply { addView(content) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(divider(0))
            addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val scrim = View(act).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { hide() }
        }
        val width = minOf(dp(360), (act.resources.displayMetrics.widthPixels * 0.86f).toInt())
        val ov = FrameLayout(act).apply {
            addView(scrim, MATCH, MATCH)
            addView(p, FrameLayout.LayoutParams(width, MATCH, Gravity.END))
        }
        overlay = ov
        panel = p
        container.addView(ov, MATCH, MATCH)

        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(150).start()
        p.translationX = width.toFloat()
        p.animate().translationX(0f).setDuration(200).start()
    }

    fun hide(after: (() -> Unit)? = null) {
        val ov = overlay
        val p = panel
        if (ov == null || p == null) { after?.invoke(); return }
        overlay = null
        panel = null
        ov.getChildAt(0).animate().alpha(0f).setDuration(150).start()
        p.animate().translationX(p.width.toFloat()).setDuration(180).withEndAction {
            container.removeView(ov)
            after?.invoke()
        }.start()
    }

    private fun closeThen(block: () -> Unit) = hide(block)

    // ------------------------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------------------------

    private fun divider(marginDp: Int = 12) = View(act).apply {
        setBackgroundColor(cLine)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(marginDp), 0, dp(marginDp)) }
    }

    private fun chip(label: String, @DrawableRes iconRes: Int, click: () -> Unit) =
        MaterialButton(act, null, M.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            this.icon = tinted(iconRes, cText)
            iconTint = ColorStateList.valueOf(cText)
            setTextColor(cText)
            cornerRadius = dp(22)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) }
        }

    /** Lays tiles out three per row; short rows are padded so tiles keep an equal width. */
    private fun grid(tiles: List<View>): View {
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        tiles.chunked(3).forEach { rowTiles ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            rowTiles.forEach { row.addView(it, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            repeat(3 - rowTiles.size) { row.addView(Space(act), LinearLayout.LayoutParams(0, 0, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
            col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        return col
    }

    private fun baseTile(label: String, @DrawableRes iconRes: Int, startActive: Boolean): Pair<LinearLayout, (Boolean) -> Unit> {
        val iv = ImageView(act)
        val tv = TextView(act).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val t = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(88)
            setPadding(dp(6), dp(10), dp(6), dp(10))
            addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })
        }
        val style = { on: Boolean ->
            val fg = if (on) cOnActive else cText
            t.background = rounded(if (on) cActive else cTile, 20)
            iv.setImageDrawable(tinted(iconRes, fg))
            tv.setTextColor(fg)
        }
        style(startActive)
        return t to style
    }

    private fun actionTile(label: String, @DrawableRes iconRes: Int, tile: Tile): View {
        val (t, _) = baseTile(label, iconRes, false)
        t.setOnClickListener { closeThen { host.onTile(tile) } }
        return t
    }

    private fun toggleTile(label: String, @DrawableRes iconRes: Int, initial: Boolean, toggle: Toggle): View {
        var on = initial
        val (t, style) = baseTile(label, iconRes, on)
        t.setOnClickListener {
            on = !on
            style(on)
            host.onToggle(toggle, on)
        }
        return t
    }

    private fun scriptRow(name: String, @DrawableRes iconRes: Int, key: String?, checked: Boolean, action: (() -> Unit)?): View {
        val cb = if (key != null) CheckBox(act).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, b -> host.onScript(key, b) }
        } else null
        return LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(4), dp(8))
            addView(ImageView(act).apply { setImageDrawable(tinted(iconRes, cText)) },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
            addView(TextView(act).apply {
                text = name
                textSize = 16f
                setTextColor(cText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (cb != null) addView(cb, LinearLayout.LayoutParams(WRAP, WRAP))
            setOnClickListener { if (cb != null) cb.toggle() else action?.invoke() }
        }
    }

    private fun navButton(@DrawableRes iconRes: Int, enabled: Boolean, primary: Boolean, nav: Nav): View =
        ImageButton(act).apply {
            setImageDrawable(tinted(iconRes, if (primary) cOnActive else cText))
            background = rounded(if (primary) cActive else cTile, 26)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { closeThen { host.onNav(nav) } }
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
        }
}
