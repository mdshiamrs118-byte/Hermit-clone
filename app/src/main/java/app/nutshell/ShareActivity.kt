package app.nutshell

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Share target: pick which lite app (or an incognito tab) should open the shared link. */
class ShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val m = Patterns.WEB_URL.matcher(text)
        if (!m.find()) { toast("No link found in the shared text"); finish(); return }
        val url = AppEditor.normalizeUrl(m.group())
        val apps = Store.apps()
        val labels = (apps.map { it.name } + getString(R.string.incognito)).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Open in…")
            .setItems(labels) { _, i ->
                val id = if (i < apps.size) apps[i].id else BrowserActivity.INCOGNITO
                startActivity(BrowserActivity.launchIntent(this, id, url))
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
