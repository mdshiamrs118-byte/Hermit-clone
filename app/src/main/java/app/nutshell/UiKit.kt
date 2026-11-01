package app.nutshell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.R as M
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

const val ERR_EMPTY = "Cannot be empty"
const val ERR_URL = "URL appears to be missing or invalid"
const val ERR_SEARCH = "Use “%s” where the search query should appear. E.g. https://google.com/search?q=%s"
const val ERR_SHARE = "Use placeholders where the shared text should appear: “%s”: full text, “%u”: URL only, “%t”: title only."
const val ERR_TEST_FEED = "Test the feed before saving"
const val ERR_TEST_MONITOR = "Test the monitor before saving"

fun requireText(s: String): String? = if (s.isBlank()) ERR_EMPTY else null

fun urlError(s: String): String? {
    if (s.isBlank()) return ERR_URL
    val host = Uri.parse(AppEditor.normalizeUrl(s)).host
    return if (host.isNullOrBlank() || !(host.contains('.') || host == "localhost")) ERR_URL else null
}

fun searchUrlError(s: String): String? = urlError(s) ?: if ("%s" in s) null else ERR_SEARCH
fun shareUrlError(s: String): String? = urlError(s) ?: if ("%s" in s || "%u" in s || "%t" in s) null else ERR_SHARE

/** Colors, rows and dialogs shared by the settings screens. Follows the phone's light/dark theme. */
class Ui(val act: AppCompatActivity) {
    private fun c(attr: Int, fallback: Int) = MaterialColors.getColor(act, attr, fallback)

    val cText = c(M.attr.colorOnSurface, Color.WHITE)
    val cSub = c(M.attr.colorOnSurfaceVariant, Color.LTGRAY)
    val cAccent = c(M.attr.colorPrimary, Color.CYAN)
    val cSurface = c(M.attr.colorSurface, Color.BLACK)
    val cCard = c(M.attr.colorSurfaceContainerHigh, Color.DKGRAY)
    val cLine = c(M.attr.colorOutlineVariant, Color.GRAY)
    val cActive = c(M.attr.colorPrimaryContainer, Color.CYAN)
    val cOnActive = c(M.attr.colorOnPrimaryContainer, Color.BLACK)

    fun dp(v: Int) = act.dp(v)
    fun tinted(res: Int, tint: Int = cText): Drawable = androidx.core.content.ContextCompat.getDrawable(act, res)!!.mutate().apply { setTint(tint) }
    fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun medium() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun ripple(borderless: Boolean = false): Drawable? {
        val attr = if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground
        val ta = act.obtainStyledAttributes(intArrayOf(attr))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d
    }

    // ---- page scaffold ---------------------------------------------------------------------

    class Page(val root: FrameLayout, val body: LinearLayout)

    fun page(title: String, onBack: () -> Unit): Page {
        val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(110)) }
        val back = ImageButton(act).apply {
            setImageDrawable(tinted(R.drawable.ic_arrow_back))
            background = ripple(true)
            setOnClickListener { onBack() }
        }
        val titleTv = TextView(act).apply {
            text = title
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText)
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(back, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(8), dp(8), 0, 0) })
            addView(titleTv, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(cSurface)
            addView(ScrollView(act).apply { isFillViewport = true; addView(col) }, MATCH, MATCH)
        }
        return Page(root, body)
    }

    /** Floating button in the page's bottom-right corner; [slot] 0 is the lowest. */
    fun Page.fab(res: Int, slot: Int = 0, mini: Boolean = false, click: () -> Unit) {
        val f = FloatingActionButton(act).apply {
            setImageResource(res)
            if (mini) size = FloatingActionButton.SIZE_MINI
            setOnClickListener { click() }
        }
        root.addView(f, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(16), dp(16), dp(16), dp(16) + slot * dp(68))
        })
    }

    // ---- rows ------------------------------------------------------------------------------

    private fun iconSlot(icon: Int?): View =
        if (icon == null) View(act).apply { layoutParams = LinearLayout.LayoutParams(dp(56), dp(24)) }
        else ImageView(act).apply {
            setImageDrawable(tinted(icon))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { leftMargin = 0; rightMargin = dp(32) }
        }

    private fun baseRow(icon: Int?, clickable: Boolean): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), dp(12), dp(20), dp(12))
        if (clickable) { background = ripple(); isClickable = true; isFocusable = true }
        addView(iconSlot(icon))
    }

    private fun texts(title: String, sub: String?): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(act).apply { text = title; textSize = 18f; typeface = medium(); setTextColor(cText) })
        if (sub != null) addView(TextView(act).apply { text = sub; textSize = 15f; setTextColor(cSub) })
    }

    fun navRow(icon: Int?, title: String, sub: String? = null, onClick: () -> Unit): View =
        baseRow(icon, true).apply {
            addView(texts(title, sub), LinearLayout.LayoutParams(0, WRAP, 1f))
            setOnClickListener { onClick() }
        }

    fun switchRow(icon: Int?, title: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = MaterialSwitch(act).apply { isChecked = checked }
        return baseRow(icon, true).apply {
            addView(texts(title, null), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sw)
            sw.setOnCheckedChangeListener { _, b -> onChange(b) }
            setOnClickListener { sw.toggle() }
        }
    }

    fun subheader(text: String): View = TextView(act).apply {
        this.text = text
        textSize = 15f
        typeface = medium()
        setTextColor(cAccent)
        setPadding(dp(24) + dp(56), dp(16), dp(16), dp(6))
    }

    fun divider(): View = View(act).apply {
        setBackgroundColor(cLine)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(0, dp(8), 0, dp(8)) }
    }

    fun infoRow(text: String): View = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(24), dp(28), dp(20), dp(12))
        addView(ImageView(act).apply { setImageDrawable(tinted(R.drawable.ic_info)) },
            LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(32) })
        addView(TextView(act).apply { this.text = text; textSize = 17f; typeface = medium(); setTextColor(cSub) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    fun emptyCard(icon: Int): View = FrameLayout(act).apply {
        background = rounded(cCard, 14)
        addView(ImageView(act).apply { setImageDrawable(tinted(icon, cSub)) },
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(66)).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }
    }

    /** A boxed list entry with an optional remove button. */
    fun itemCard(title: String, sub: String?, onClick: (() -> Unit)?, onRemove: (() -> Unit)?): View =
        LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cCard, 14)
            setPadding(dp(18), dp(10), dp(6), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }
            addView(texts(title, sub), LinearLayout.LayoutParams(0, WRAP, 1f))
            if (onRemove != null) addView(ImageButton(act).apply {
                setImageDrawable(tinted(R.drawable.ic_remove_circle))
                background = ripple(true)
                setOnClickListener { onRemove() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
        }

    fun pill(label: String, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit): MaterialButton =
        MaterialButton(act, null, M.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            cornerRadius = dp(22)
            setTextColor(cText)
            if (icon != null) { this.icon = tinted(icon); iconTint = ColorStateList.valueOf(cText) }
            isEnabled = enabled
            setOnClickListener { onClick() }
        }

    fun circle(color: Int, sizeDp: Int): View = View(act).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.blendARGB(color, cSurface, 0.35f))
            setStroke(dp(2), color)
        }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun sandboxIcon(id: String, sizeDp: Int): View =
        if (id == Sandboxes.INCOGNITO) ImageView(act).apply {
            setImageDrawable(tinted(R.drawable.ic_incognito))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        } else circle(Sandboxes.get(id).color, sizeDp)

    // ---- form dialog -----------------------------------------------------------------------

    class Field(
        val label: String,
        val initial: String = "",
        val uri: Boolean = false,
        val validate: (String) -> String? = ::requireText
    )

    /**
     * Bottom-sheet form with live validation. Pass [test] to require a successful "Test" before Save
     * is enabled (editing any field resets it), or [help] for a plain Help button.
     */
    fun form(
        fields: List<Field>,
        saveLabel: String = "Save",
        test: ((List<String>, (Boolean, String) -> Unit) -> Unit)? = null,
        testHint: String = "",
        help: (() -> Unit)? = null,
        onSave: (List<String>) -> Unit
    ) {
        val dlg = BottomSheetDialog(act)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
        }
        val tils = ArrayList<TextInputLayout>()
        val ets = ArrayList<TextInputEditText>()
        fields.forEach { f ->
            val til = TextInputLayout(act, null, M.attr.textInputOutlinedStyle).apply { hint = f.label }
            val et = TextInputEditText(til.context).apply {
                setText(f.initial)
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or (if (f.uri) InputType.TYPE_TEXT_VARIATION_URI else 0)
            }
            til.addView(et)
            col.addView(til, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
            tils.add(til); ets.add(et)
        }

        var tested = false
        val left = if (test != null) pill("Test") { } else if (help != null) pill("Help") { help() } else null
        val save = MaterialButton(act).apply { text = saveLabel; isAllCaps = false; cornerRadius = dp(22) }
        val cancel = MaterialButton(act, null, M.attr.materialButtonOutlinedStyle).apply {
            text = "Cancel"; isAllCaps = false; strokeWidth = 0; setTextColor(cAccent)
            setOnClickListener { dlg.dismiss() }
        }

        fun values() = ets.map { it.text.toString().trim() }
        fun refresh() {
            val v = values()
            var ok = true
            fields.forEachIndexed { i, f ->
                val err = f.validate(v[i])
                tils[i].error = err
                if (err != null) ok = false
            }
            if (ok && test != null && !tested) tils.last().error = testHint
            left?.isEnabled = ok
            save.isEnabled = ok && (test == null || tested)
        }
        ets.forEach { it.doAfterTextChanged { tested = false; refresh() } }

        if (test != null && left != null) left.setOnClickListener {
            left.isEnabled = false
            left.text = "Testing…"
            test(values()) { ok, message ->
                act.runOnUiThread {
                    tested = ok
                    left.text = "Test"
                    refresh()
                    act.toast(message)
                }
            }
        }
        save.setOnClickListener { dlg.dismiss(); onSave(values()) }

        col.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (left != null) addView(left)
            addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
            addView(cancel)
            addView(save, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) })
        })
        refresh()
        dlg.setContentView(col)
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.behavior.skipCollapsed = true
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.show()
    }

    /** One-field prompt (tag name, title, user agent…). */
    fun prompt(label: String, initial: String = "", okLabel: String = "OK", uri: Boolean = false,
               validate: (String) -> String? = ::requireText, onOk: (String) -> Unit) =
        form(listOf(Field(label, initial, uri, validate)), saveLabel = okLabel) { onOk(it[0]) }
}

/** Freehand drawing surface for making an icon. */
class DrawView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 18f
    }
    private data class Stroke(val path: Path, val color: Int)
    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null
    var penColor: Int = Color.BLACK
    var canvasColor: Int = Color.WHITE

    fun clear() { strokes.clear(); invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                current = Stroke(Path().apply { moveTo(e.x, e.y); lineTo(e.x + 0.1f, e.y + 0.1f) }, penColor)
                strokes.add(current!!)
            }
            MotionEvent.ACTION_MOVE -> current?.path?.lineTo(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> current = null
        }
        invalidate()
        return true
    }

    private fun paintAll(c: Canvas) {
        c.drawColor(canvasColor)
        strokes.forEach { paint.color = it.color; c.drawPath(it.path, paint) }
    }

    override fun onDraw(canvas: Canvas) = paintAll(canvas)

    /** Renders the drawing to a square bitmap of [size] pixels. */
    fun toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat() / maxOf(width, 1)
        c.scale(s, s)
        paintAll(c)
        return bmp
    }
}
