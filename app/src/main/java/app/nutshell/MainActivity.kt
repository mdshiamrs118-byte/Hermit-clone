package app.nutshell

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.google.android.material.R as M
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val items = ArrayList<LiteApp>()
    private val triedIcons = HashSet<String>()
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var viewToggle: ImageButton
    private var grid = false
    private var cText = Color.WHITE
    private var cPill = Color.DKGRAY

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        cleanupIncognitoProfiles()
        grid = Store.gridView
        cText = MaterialColors.getColor(this, M.attr.colorOnSurface, Color.WHITE)
        cPill = MaterialColors.getColor(this, M.attr.colorSurfaceContainerHigh, Color.DKGRAY)

        val title = TextView(this).apply {
            text = "Lite Apps"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
            setPadding(dp(24), dp(40), dp(24), dp(12))
        }

        list = RecyclerView(this).apply {
            clipToPadding = false
            setPadding(dp(8), 0, dp(8), dp(96))
        }
        empty = TextView(this).apply {
            text = "No lite apps yet.\nTap Create to turn any website into one."
            gravity = Gravity.CENTER
            setTextColor(cText)
            alpha = 0.7f
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
        }
        val fab = ExtendedFloatingActionButton(this).apply {
            text = "Create"
            setIconResource(R.drawable.ic_add)
            setOnClickListener { edit(LiteApp(color = PALETTE.random()), true) }
        }
        val body = FrameLayout(this).apply {
            addView(list, MATCH, MATCH)
            addView(empty, MATCH, MATCH)
            addView(fab, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(dp(16), dp(16), dp(16), dp(16))
            })
        }

        // ---- bottom bar: menu, search / URL box, list-grid switch, settings
        val search = EditText(this).apply {
            hint = "Search or type URL"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(cPill, 24)
            setPadding(dp(20), 0, dp(20), 0)
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) {
                    go(v.text.toString()); v.text = ""; true
                } else false
            }
        }
        viewToggle = iconButton(if (grid) R.drawable.ic_apps else R.drawable.ic_view_list) {
            grid = !grid
            Store.gridView = grid
            applyLayout()
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(iconButton(R.drawable.ic_menu) { mainMenu(it) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(search, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
            addView(viewToggle, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(iconButton(R.drawable.ic_settings) { GlobalSettings.show(this@MainActivity) },
                LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, M.attr.colorSurface, Color.BLACK))
            addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        setContentView(root)
        root.applySystemBarPadding()
        applyLayout()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun rounded(c: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun tint(res: Int): Drawable = ContextCompat.getDrawable(this, res)!!.mutate().apply { setTint(cText) }

    private fun ripple(ctx: Context): Drawable? {
        val ta = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    private fun iconButton(res: Int, click: (View) -> Unit) = ImageButton(this).apply {
        setImageDrawable(tint(res))
        background = ripple(context)
        setOnClickListener { click(it) }
    }

    private fun dotView(color: Int, sizeDp: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { rightMargin = dp(8) }
    }

    private fun applyLayout() {
        list.layoutManager = if (grid) GridLayoutManager(this, 4) else LinearLayoutManager(this)
        list.adapter = AppAdapter()
        viewToggle.setImageDrawable(tint(if (grid) R.drawable.ic_apps else R.drawable.ic_view_list))
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun reload() {
        items.clear()
        items.addAll(Store.apps())
        list.adapter?.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val ctx = applicationContext
        items.filter { !IconStore.has(ctx, it.id) && triedIcons.add(it.id) }.forEach { a ->
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { try { IconStore.fetch(ctx, a) } catch (_: Exception) { false } }
                if (ok) list.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun cleanupIncognitoProfiles() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val store = ProfileStore.getInstance()
                store.allProfileNames.filter { it.startsWith("incognito_") }.forEach {
                    try { store.deleteProfile(it) } catch (_: Exception) { /* still in use */ }
                }
            }
        } catch (_: Throwable) { }
    }

    /** Search/URL box: opens in an incognito tab so nothing is saved to any lite app. */
    private fun go(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val isUrl = t.startsWith("http://") || t.startsWith("https://") || (!t.contains(' ') && t.contains('.'))
        val url = if (isUrl) AppEditor.normalizeUrl(t) else "https://duckduckgo.com/?q=" + Uri.encode(t)
        startActivity(BrowserActivity.launchIntent(this, BrowserActivity.INCOGNITO, url))
    }

    private fun mainMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "New incognito tab")
            menu.add(0, 2, 1, "Block lists…")
            menu.add(0, 3, 2, "User scripts…")
            menu.add(0, 4, 3, "About")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> startActivity(BrowserActivity.launchIntent(this@MainActivity, BrowserActivity.INCOGNITO))
                    2 -> GlobalSettings.blockLists(this@MainActivity)
                    3 -> GlobalSettings.userScripts(this@MainActivity)
                    4 -> MaterialAlertDialogBuilder(this@MainActivity).setTitle("Nutshell 1.0")
                        .setMessage("An independent, open source lite-apps browser. Not affiliated with or derived from Hermit or Chimbori.")
                        .setPositiveButton(android.R.string.ok, null).show()
                }
                true
            }
        }.show()
    }

    // ---- app actions ----------------------------------------------------------------------

    private fun edit(a: LiteApp, isNew: Boolean) = AppEditor.show(this, a, isNew) { saved ->
        Store.upsert(saved)
        if (saved.notify && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        reload()
    }

    private fun open(a: LiteApp) = startActivity(BrowserActivity.launchIntent(this, a.id))

    private fun confirmDelete(a: LiteApp) {
        MaterialAlertDialogBuilder(this).setMessage("Delete “${a.name}”?")
            .setPositiveButton("Delete") { _, _ ->
                Store.delete(a.id)
                IconStore.delete(applicationContext, a.id)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun appMenu(anchor: View, a: LiteApp) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Open")
            menu.add(0, 2, 1, "Settings")
            menu.add(0, 3, 2, "Add to home screen")
            menu.add(0, 4, 3, "Delete")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> open(a)
                    2 -> edit(a, false)
                    3 -> pinShortcut(this@MainActivity, a)
                    4 -> confirmDelete(a)
                }
                true
            }
        }.show()
    }

    // ---- list / grid ----------------------------------------------------------------------

    private class VH(
        val row: View, val icon: ImageView, val name: TextView, val url: TextView?,
        val dot: View, val gear: View?, val more: View?
    ) : RecyclerView.ViewHolder(row)

    private inner class AppAdapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            if (grid) gridHolder() else listHolder()

        private fun listHolder(): VH {
            val icon = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val name = TextView(this@MainActivity).apply {
                textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText); maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val url = TextView(this@MainActivity).apply {
                textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText); alpha = 0.7f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val dot = dotView(Color.GRAY, 10)
            val urlRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(dot); addView(url, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(name); addView(urlRow)
            }
            val gear = iconButton(R.drawable.ic_settings) { }
            val more = iconButton(R.drawable.ic_more_vert) { }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(8), dp(8))
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                isClickable = true
                isFocusable = true
                addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)).apply { rightMargin = dp(20) })
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(gear, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(more, LinearLayout.LayoutParams(dp(40), dp(48)))
            }
            return VH(row, icon, name, url, dot, gear, more)
        }

        private fun gridHolder(): VH {
            val icon = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val name = TextView(this@MainActivity).apply {
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cText); gravity = Gravity.CENTER
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            }
            val dot = dotView(Color.GRAY, 8).apply { (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(6) }
            val nameRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(dot); addView(name, LinearLayout.LayoutParams(WRAP, WRAP))
            }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(14), dp(4), dp(14))
                isClickable = true
                isFocusable = true
                addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)))
                addView(nameRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
            }
            return VH(row, icon, name, null, dot, null, null)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val a = items[position]
            val ctx = h.row.context
            h.icon.setImageBitmap(IconStore.load(ctx, a.id) ?: letterIcon(a.name, a.color))
            h.name.text = a.name
            h.url?.text = a.url
            (h.dot.background as? GradientDrawable)?.setColor(a.color)
            h.row.setOnClickListener { open(a) }
            h.row.setOnLongClickListener { appMenu(h.row, a); true }
            h.gear?.setOnClickListener { edit(a, false) }
            h.more?.setOnClickListener { appMenu(it, a) }
        }
    }
}
