package app.nutshell

import android.Manifest
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val items = ArrayList<LiteApp>()
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        cleanupIncognitoProfiles()

        val toolbar = MaterialToolbar(this).apply { title = getString(R.string.app_name) }
        toolbar.menu.add(0, 1, 0, "New incognito tab")
        toolbar.menu.add(0, 2, 1, "Block lists…")
        toolbar.menu.add(0, 3, 2, "User scripts…")
        toolbar.menu.add(0, 4, 3, "About")
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(BrowserActivity.launchIntent(this, BrowserActivity.INCOGNITO))
                2 -> GlobalSettings.blockLists(this)
                3 -> GlobalSettings.userScripts(this)
                4 -> MaterialAlertDialogBuilder(this).setTitle("Nutshell 1.0")
                    .setMessage("An independent, open source lite-apps browser. Not affiliated with or derived from Hermit or Chimbori.")
                    .setPositiveButton(android.R.string.ok, null).show()
            }
            true
        }

        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppAdapter()
            clipToPadding = false
            setPadding(0, 0, 0, dp(88))
        }
        empty = TextView(this).apply {
            text = "No lite apps yet.\nTap + to turn any website into one."
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
        }
        val body = FrameLayout(this).apply {
            addView(list, MATCH, MATCH)
            addView(empty, MATCH, MATCH)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, MATCH, WRAP)
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        val fab = FloatingActionButton(this).apply {
            setImageResource(R.drawable.ic_add)
            setOnClickListener { edit(LiteApp(color = PALETTE.random()), true) }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            addView(column, MATCH, MATCH)
            addView(fab, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(dp(16), dp(16), dp(16), dp(16))
            })
        }
        setContentView(root)
        root.applySystemBarPadding()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(Store.apps())
        list.adapter?.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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

    private fun edit(a: LiteApp, isNew: Boolean) = AppEditor.show(this, a, isNew) { saved ->
        Store.upsert(saved)
        if (saved.notify && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        reload()
    }

    private fun open(a: LiteApp) = startActivity(BrowserActivity.launchIntent(this, a.id))

    private fun actions(a: LiteApp) {
        MaterialAlertDialogBuilder(this).setTitle(a.name)
            .setItems(arrayOf("Open", "Edit", "Add to home screen", "Delete")) { _, which ->
                when (which) {
                    0 -> open(a)
                    1 -> edit(a, false)
                    2 -> pinShortcut(this, a)
                    3 -> MaterialAlertDialogBuilder(this).setMessage("Delete “${a.name}”?")
                        .setPositiveButton("Delete") { _, _ -> Store.delete(a.id); reload() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }.show()
    }

    // ---- List --------------------------------------------------------------------------

    private class VH(val row: LinearLayout, val icon: TextView, val name: TextView, val url: TextView) :
        RecyclerView.ViewHolder(row)

    private fun selectable(ctx: Context): Drawable? {
        val ta: TypedArray = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    private inner class AppAdapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val icon = TextView(ctx).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val name = TextView(ctx).apply { textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            val url = TextView(ctx).apply { textSize = 13f; alpha = 0.65f; maxLines = 1 }
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(name); addView(url)
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                isClickable = true
                isFocusable = true
                background = selectable(ctx)
                addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(16) })
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            return VH(row, icon, name, url)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val a = items[position]
            h.icon.text = a.name.take(1).uppercase()
            h.icon.setTextColor(a.color.onColor())
            h.icon.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(a.color) }
            h.name.text = a.name
            h.url.text = a.url.removePrefix("https://").removePrefix("http://")
            h.row.setOnClickListener { open(a) }
            h.row.setOnLongClickListener { actions(a); true }
        }
    }
}
