package app.nutshell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Share target: pick which lite app (or share target inside a lite app) should open the shared text.
 * A share target's URL may use %s (full text), %u (URL only) and %t (title only).
 */
class ShareActivity : AppCompatActivity() {

    private class Choice(val label: String, val appId: String, val target: Link?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val m = Patterns.WEB_URL.matcher(text)
        val url = if (m.find()) AppEditor.normalizeUrl(m.group()) else null
        val title = (if (url != null) text.replace(m.group(), "") else text).trim()
        if (text.isBlank()) { toast("Nothing to share"); finish(); return }

        val choices = ArrayList<Choice>()
        for (app in Store.apps()) {
            if (app.shares.isEmpty()) { if (url != null) choices.add(Choice(app.name, app.id, null)) }
            else app.shares.forEach { choices.add(Choice("${app.name} · ${it.title}", app.id, it)) }
        }
        if (url != null) choices.add(Choice(getString(R.string.incognito), BrowserActivity.INCOGNITO, null))
        if (choices.isEmpty()) { toast("No lite app can open this"); finish(); return }

        MaterialAlertDialogBuilder(this)
            .setTitle("Open in…")
            .setItems(choices.map { it.label }.toTypedArray()) { _, i ->
                val c = choices[i]
                val target = c.target
                val dest = if (target != null) {
                    target.url.replace("%s", Uri.encode(text)).replace("%u", Uri.encode(url.orEmpty())).replace("%t", Uri.encode(title))
                } else url
                startActivity(BrowserActivity.launchIntent(this, c.appId, dest))
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
