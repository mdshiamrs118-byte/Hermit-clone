package app.nutshell

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * "Ready-made Lite Apps": a searchable gallery of popular sites in 50+ categories.
 * Tap an icon to add it to your lite apps.
 */
class GalleryActivity : AppCompatActivity() {

    private companion object {
        const val HEADER = 0
        const val APPS = 1
        const val FOOTER = 2
        const val SWITCH = 3
        const val EMPTY = 4
        const val COLUMNS = 5
    }

    private class Row(val kind: Int, val title: String = "", val count: Int = 0, val apps: List<CatalogApp> = emptyList())
    private class Cell(val root: LinearLayout, val icon: ImageView, val badge: ImageView, val label: TextView)
    private class SimpleVH(v: View) : RecyclerView.ViewHolder(v)
    private class HeaderVH(v: View, val name: TextView, val count: TextView) : RecyclerView.ViewHolder(v)
    private class RowVH(v: View, val cells: List<Cell>) : RecyclerView.ViewHolder(v)

    private lateinit var ui: Ui
    private lateinit var list: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var rows: List<Row> = emptyList()
    private val have = HashSet<String>()
    private var showIcons = false
    private val gate = Semaphore(6)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        ui = Ui(this)
        showIcons = Store.galleryIcons
        refreshHave()

        val search = EditText(this).apply {
            hint = "Search"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setTextColor(ui.cText)
            setHintTextColor(ui.cSub)
            setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rounded(ui.cCard, 26)
            setPadding(ui.dp(14), 0, ui.dp(4), 0)
            addView(ImageView(this@GalleryActivity).apply { setImageDrawable(ui.tinted(R.drawable.ic_search, ui.cSub)) },
                LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            addView(search, LinearLayout.LayoutParams(0, MATCH, 1f).apply { leftMargin = ui.dp(10) })
            addView(iconButton(R.drawable.ic_arrow_forward) { hideKeyboard(); list.scrollToPosition(0) },
                LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6))
            addView(iconButton(R.drawable.ic_arrow_back) { finish() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(pill, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { setMargins(ui.dp(4), 0, ui.dp(4), 0) })
            addView(iconButton(R.drawable.ic_expand_more) { jump(1) }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)))
            addView(iconButton(R.drawable.ic_expand_less) { jump(-1) }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)))
        }

        layoutManager = LinearLayoutManager(this)
        list = RecyclerView(this).apply {
            layoutManager = this@GalleryActivity.layoutManager
            adapter = GalleryAdapter()
            clipToPadding = false
            setPadding(0, 0, 0, ui.dp(24))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.cSurface)
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        setContentView(root)
        root.applySystemBarPadding()

        search.doAfterTextChanged { rebuild(it.toString()) }
        rebuild("")
    }

    override fun onResume() {
        super.onResume()
        refreshHave()
        list.adapter?.notifyDataSetChanged()
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun iconButton(res: Int, click: () -> Unit) = ImageButton(this).apply {
        setImageDrawable(ui.tinted(res))
        background = ui.ripple(true)
        setOnClickListener { click() }
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
    }

    private fun key(url: String) = url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private fun refreshHave() {
        have.clear()
        Store.apps().forEach { have.add(key(it.url)) }
    }

    private fun colorFor(name: String) = PALETTE[(name.hashCode() and Int.MAX_VALUE) % PALETTE.size]

    private fun rebuild(q: String) {
        val query = q.trim().lowercase()
        val out = ArrayList<Row>()
        if (query.isEmpty()) out.add(Row(SWITCH))
        Catalog.categories.forEach { c ->
            val apps = if (query.isEmpty()) c.apps else c.apps.filter { it.name.lowercase().contains(query) || it.url.contains(query) }
            if (apps.isNotEmpty()) {
                out.add(Row(HEADER, c.name, apps.size))
                apps.chunked(COLUMNS).forEach { out.add(Row(APPS, apps = it)) }
            }
        }
        if (out.none { it.kind == APPS }) out.add(Row(EMPTY))
        out.add(Row(FOOTER))
        rows = out
        list.adapter?.notifyDataSetChanged()
    }

    /** Jumps to the next (dir = 1) or previous (dir = -1) category heading. */
    private fun jump(dir: Int) {
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val headers = rows.indices.filter { rows[it].kind == HEADER }
        val target = if (dir > 0) headers.firstOrNull { it > first } else headers.lastOrNull { it < first } ?: 0
        if (target != null) layoutManager.scrollToPositionWithOffset(target, 0)
    }

    private fun add(app: CatalogApp) {
        if (!have.add(key(app.url))) { toast("“${app.name}” is already in your list"); return }
        val lite = LiteApp(name = app.name, url = app.url, color = colorFor(app.name))
        Store.upsert(lite)
        toast("Added “${app.name}”")
        list.adapter?.notifyDataSetChanged()
        val ctx = applicationContext
        val cached = Uri.parse(app.url).host?.let { IconStore.galleryCached(ctx, it) }
        if (cached != null) IconStore.adopt(ctx, lite.id, cached)
        else lifecycleScope.launch(Dispatchers.IO) {
            try { IconStore.fetch(ctx, lite) } catch (_: Exception) { }
        }
    }

    private fun loadIcon(app: CatalogApp, iv: ImageView) {
        val host = Uri.parse(app.url).host ?: return
        val ctx = applicationContext
        IconStore.galleryCached(ctx, host)?.let { iv.setImageBitmap(it); return }
        lifecycleScope.launch {
            val bmp = gate.withPermit { withContext(Dispatchers.IO) { IconStore.galleryFetch(ctx, host, app.url) } }
            if (bmp != null && iv.tag == app.url) iv.setImageBitmap(bmp)
        }
    }

    // ---- adapter ---------------------------------------------------------------------------

    private fun buildCell(ctx: Context): Cell {
        val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val badge = ImageView(ctx).apply {
            setImageDrawable(ui.tinted(R.drawable.ic_check, ui.cSurface))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ui.cAccent) }
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
            visibility = View.GONE
        }
        val frame = FrameLayout(ctx).apply {
            addView(icon, FrameLayout.LayoutParams(ui.dp(56), ui.dp(56)))
            addView(badge, FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.BOTTOM or Gravity.END))
        }
        val label = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ui.cText)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(8))
            background = ui.ripple()
            addView(frame, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
            addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ui.dp(6) })
        }
        return Cell(root, icon, badge, label)
    }

    private inner class GalleryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = rows[position].kind

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            return when (viewType) {
                HEADER -> {
                    val name = TextView(ctx).apply { textSize = 22f; setTextColor(ui.cText) }
                    val count = TextView(ctx).apply { textSize = 18f; setTextColor(ui.cSub) }
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(8))
                        layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                        addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
                        addView(count)
                    }
                    HeaderVH(row, name, count)
                }
                APPS -> {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(ui.dp(8), 0, ui.dp(8), 0)
                        layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                    }
                    val cells = List(COLUMNS) { buildCell(ctx).also { c -> row.addView(c.root, LinearLayout.LayoutParams(0, WRAP, 1f)) } }
                    RowVH(row, cells)
                }
                SWITCH -> SimpleVH(ui.switchRow(R.drawable.ic_image, "Load site icons", showIcons) { on ->
                    showIcons = on
                    Store.galleryIcons = on
                    list.adapter?.notifyDataSetChanged()
                }.apply { layoutParams = RecyclerView.LayoutParams(MATCH, WRAP) })
                else -> SimpleVH(TextView(ctx).apply {
                    textSize = 14f
                    setTextColor(ui.cSub)
                    setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24))
                    layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
                    tag = viewType
                })
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val r = rows[position]
            when (holder) {
                is HeaderVH -> {
                    holder.name.text = r.title
                    holder.count.text = "${r.count} Lite Apps"
                }
                is RowVH -> holder.cells.forEachIndexed { i, cell ->
                    val app = r.apps.getOrNull(i)
                    if (app == null) {
                        cell.root.visibility = View.INVISIBLE
                        cell.root.setOnClickListener(null)
                        return@forEachIndexed
                    }
                    cell.root.visibility = View.VISIBLE
                    cell.label.text = app.name
                    cell.icon.tag = app.url
                    cell.icon.setImageBitmap(letterIcon(app.name, colorFor(app.name)))
                    cell.badge.visibility = if (key(app.url) in have) View.VISIBLE else View.GONE
                    cell.root.setOnClickListener { add(app) }
                    if (showIcons) loadIcon(app, cell.icon)
                }
                is SimpleVH -> (holder.itemView as? TextView)?.let { tv ->
                    tv.text = when (r.kind) {
                        EMPTY -> "No ready-made lite apps match your search."
                        else -> "All product names, icons, and web sites linked from this page are trademarks of their " +
                            "respective owners. Nutshell only provides these links as a courtesy and may not be held " +
                            "accountable for their contents.\n\nThe “FMHY” sections list legitimate sites recommended " +
                            "by the community directory at fmhy.net. Sites that mainly offer pirated material are not included."
                    }
                }
            }
        }
    }
}
