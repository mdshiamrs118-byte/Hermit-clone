package app.nutshell

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity

/** The small "new lite app" form. Everything else lives in SettingsActivity. */
object AppEditor {

    fun normalizeUrl(s: String): String {
        val t = s.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    fun show(act: AppCompatActivity, initial: LiteApp, @Suppress("UNUSED_PARAMETER") isNew: Boolean, onSave: (LiteApp) -> Unit) {
        Ui(act).form(
            listOf(
                Ui.Field("Title", initial.name, validate = { null }),
                Ui.Field("URL", if (initial.url == "https://") "" else initial.url, uri = true, validate = ::urlError)
            ),
            saveLabel = "Create"
        ) { v ->
            val url = normalizeUrl(v[1])
            val name = v[0].ifEmpty { Uri.parse(url).host.orEmpty().removePrefix("www.") }
            onSave(initial.copy(name = name, url = url))
        }
    }
}
