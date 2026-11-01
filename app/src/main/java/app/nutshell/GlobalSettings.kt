package app.nutshell

import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings that apply to every lite app: sandboxes, block lists and the user-script library. */
object GlobalSettings {

    fun show(act: AppCompatActivity) {
        MaterialAlertDialogBuilder(act).setTitle("Global settings")
            .setItems(arrayOf("Sandboxes", "Content Blocker", "User scripts")) { _, i ->
                when (i) {
                    0 -> sandboxes(act)
                    1 -> blockLists(act)
                    else -> userScripts(act)
                }
            }.show()
    }

    fun sandboxes(act: AppCompatActivity) = act.startActivity(SettingsActivity.intent(act, null, "sandboxes"))
    fun blockLists(act: AppCompatActivity) = act.startActivity(SettingsActivity.intent(act, null, "blocker"))

    fun userScripts(act: AppCompatActivity) {
        val scripts = Store.userScripts()
        val b = MaterialAlertDialogBuilder(act).setTitle("User scripts")
        if (scripts.isEmpty()) {
            b.setMessage("No scripts yet. Add one by pasting its source or a link to a .user.js file. " +
                "Then tick it in a lite app’s Quick Settings to use it there.")
        } else {
            b.setItems(scripts.map { UserScript(it).name }.toTypedArray()) { _, i ->
                MaterialAlertDialogBuilder(act).setMessage("Remove “${UserScript(scripts[i]).name}”?")
                    .setPositiveButton("Remove") { _, _ -> scripts.removeAt(i); Store.saveUserScripts(scripts) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        b.setPositiveButton("Add…") { _, _ -> addScript(act) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun addScript(act: AppCompatActivity) {
        val et = EditText(act).apply {
            hint = "Paste script source, or a https:// link"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            setPadding(act.dp(20), act.dp(12), act.dp(20), act.dp(12))
        }
        MaterialAlertDialogBuilder(act).setTitle("Add user script").setView(et)
            .setPositiveButton("Add") { _, _ ->
                val text = et.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                act.lifecycleScope.launch {
                    val src = if (text.lines().size == 1 && Patterns.WEB_URL.matcher(text).matches()) {
                        try { withContext(Dispatchers.IO) { Net.get(AppEditor.normalizeUrl(text), 2_000_000) } }
                        catch (e: Exception) { act.toast("Couldn’t download the script"); return@launch }
                    } else text
                    Store.saveUserScripts(Store.userScripts().also { it.add(src) })
                    act.toast("Added “${UserScript(src).name}”")
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
