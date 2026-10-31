package app.nutshell

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Create/edit dialog for a lite app. Works on a copy and hands it back through [onSave]. */
object AppEditor {

    fun normalizeUrl(s: String): String {
        val t = s.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    fun show(act: Activity, initial: LiteApp, isNew: Boolean, onSave: (LiteApp) -> Unit) {
        val a = initial.copy()
        val pad = act.dp(20)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun field(hint: String, value: String, type: Int) = EditText(act).apply {
            this.hint = hint
            setText(value)
            inputType = type
            isSingleLine = true
        }.also { col.addView(it, MATCH, WRAP) }

        val uriType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val name = field("Name", a.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val url = field("Address", if (a.url == "https://") "" else a.url, uriType)
        val feed = field("Feed URL (optional)", a.feedUrl, uriType)

        var selected = a.color
        val dots = ArrayList<View>()
        fun refresh() = dots.forEachIndexed { i, v -> v.alpha = if (PALETTE[i] == selected) 1f else 0.3f }
        val row = LinearLayout(act)
        PALETTE.forEach { c ->
            val v = View(act).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                setOnClickListener { selected = c; refresh() }
            }
            row.addView(v, LinearLayout.LayoutParams(act.dp(36), act.dp(36)).apply {
                setMargins(act.dp(4), act.dp(10), act.dp(4), act.dp(10))
            })
            dots.add(v)
        }
        refresh()
        col.addView(HorizontalScrollView(act).apply { addView(row) }, MATCH, WRAP)

        fun check(label: String, value: Boolean, set: (Boolean) -> Unit) = CheckBox(act).apply {
            text = label
            isChecked = value
            setOnCheckedChangeListener { _, b -> set(b) }
        }.also { col.addView(it, MATCH, WRAP) }

        check("JavaScript", a.javascript) { a.javascript = it }
        check("Request desktop site", a.desktopMode) { a.desktopMode = it }
        check("Block ads and trackers", a.blockContent) { a.blockContent = it }
        check("Block third-party cookies", a.blockThirdPartyCookies) { a.blockThirdPartyCookies = it }
        check("Keep links to other sites outside this app", a.scopeToSite) { a.scopeToSite = it }
        check("Isolated storage (own cookies and logins)", a.sandbox) { a.sandbox = it }
        check("Block pop-ups", a.blockPopups) { a.blockPopups = it }
        check("Allow location", a.allowLocation) { a.allowLocation = it }
        check("Allow camera and microphone", a.allowMedia) { a.allowMedia = it }
        check("Dark mode for pages", a.forceDark) { a.forceDark = it }
        check("Notify me about new feed items", a.notify) { a.notify = it }

        MaterialAlertDialogBuilder(act)
            .setTitle(if (isNew) "New lite app" else "Lite app settings")
            .setView(ScrollView(act).apply { addView(col) })
            .setPositiveButton("Save") { _, _ ->
                val fixed = normalizeUrl(url.text.toString())
                val host = Uri.parse(fixed).host
                if (host.isNullOrBlank()) {
                    act.toast("That address doesn’t look valid"); return@setPositiveButton
                }
                a.url = fixed
                a.name = name.text.toString().trim().ifEmpty { host.removePrefix("www.") }
                a.feedUrl = feed.text.toString().trim().let { if (it.isEmpty()) "" else normalizeUrl(it) }
                a.color = selected
                onSave(a)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
