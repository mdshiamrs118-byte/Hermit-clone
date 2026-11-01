package app.nutshell

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

data class Sandbox(val id: String, val defaultName: String, val color: Int)

/**
 * A sandbox is a named storage container (cookies, logins, cache). Lite apps that pick the same
 * sandbox share it; different sandboxes let you stay signed in to several accounts at once.
 */
object Sandboxes {
    const val DEFAULT = "default"
    const val INCOGNITO = "incognito"

    val all: List<Sandbox> = listOf(
        Sandbox(DEFAULT, "Default", 0xFFBDBDBD.toInt()),
        Sandbox(INCOGNITO, "Incognito", 0xFFBDBDBD.toInt()),
        Sandbox("red", "RED", 0xFFFF1744.toInt()), Sandbox("green", "GREEN", 0xFF388E3C.toInt()),
        Sandbox("blue", "BLUE", 0xFF2979FF.toInt()), Sandbox("purple", "PURPLE", 0xFF4A148C.toInt()),
        Sandbox("yellow", "YELLOW", 0xFFFFEB00.toInt()), Sandbox("pink", "PINK", 0xFFFF80AB.toInt()),
        Sandbox("orange", "ORANGE", 0xFFFF9800.toInt()), Sandbox("brown", "BROWN", 0xFF6D4C41.toInt()),
        Sandbox("lime", "LIME", 0xFFC6FF00.toInt()), Sandbox("cyan", "CYAN", 0xFF80FFFF.toInt()),
        Sandbox("crimson", "CRIMSON", 0xFFB71C1C.toInt()), Sandbox("violet", "VIOLET", 0xFF8E24AA.toInt()),
        Sandbox("lavender", "LAVENDER", 0xFF7986CB.toInt()), Sandbox("olive", "OLIVE", 0xFF9E9D24.toInt()),
        Sandbox("indigo", "INDIGO", 0xFF3F51B5.toInt()), Sandbox("teal", "TEAL", 0xFF00695C.toInt()),
        Sandbox("aquamarine", "AQUAMARINE", 0xFF26A69A.toInt()), Sandbox("brick", "BRICK", 0xFFBF360C.toInt()),
        Sandbox("amber", "AMBER", 0xFFFFCA28.toInt()), Sandbox("tangerine", "TANGERINE", 0xFFE65100.toInt())
    )

    fun get(id: String): Sandbox = all.firstOrNull { it.id == id } ?: all[0]
    fun name(id: String): String = Store.sandboxName(id) ?: get(id).defaultName
    fun editable(id: String) = id != DEFAULT && id != INCOGNITO

    /** WebView profile name for a sandbox, or null for the default profile / temporary incognito. */
    fun profileName(id: String): String? = if (id == DEFAULT || id == INCOGNITO) null else "sb_$id"

    fun usedBy(id: String): List<String> = Store.apps().filter { it.sandbox == id }.map { it.name }

    private fun profilesSupported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    // ---- sizes (best effort: reads WebView's on-disk folders, whose layout can vary by WebView version)

    private fun dirSize(f: File): Long = when {
        !f.exists() -> 0L
        f.isFile -> f.length()
        else -> f.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun dirsFor(base: File, id: String): List<File> = when (id) {
        DEFAULT -> listOf(File(base, "Default"))
        INCOGNITO -> File(base, "Profiles").listFiles { f -> f.name.startsWith("incognito_") }?.toList().orEmpty()
        else -> listOf(File(base, "Profiles/sb_$id"))
    }

    fun storageSize(ctx: Context, id: String): Long =
        dirsFor(File(ctx.dataDir, "app_webview"), id).sumOf { dirSize(it) }

    fun cacheSize(ctx: Context, id: String): Long =
        dirsFor(File(ctx.cacheDir, "WebView"), id).sumOf { dirSize(it) }

    // ---- clearing (call on the main thread)

    fun clearCache(ctx: Context, id: String) {
        val w = WebView(ctx)
        try {
            profileName(id)?.let {
                if (profilesSupported()) {
                    ProfileStore.getInstance().getOrCreateProfile(it)
                    WebViewCompat.setProfile(w, it)
                }
            }
            w.clearCache(true)
        } finally {
            w.destroy()
        }
    }

    fun clearStorage(ctx: Context, id: String) {
        when {
            id == INCOGNITO -> if (profilesSupported()) {
                val store = ProfileStore.getInstance()
                store.allProfileNames.filter { it.startsWith("incognito_") }.forEach {
                    try { store.deleteProfile(it) } catch (_: Exception) { }
                }
            }
            profileName(id) != null && profilesSupported() -> {
                val p = ProfileStore.getInstance().getOrCreateProfile(profileName(id)!!)
                p.cookieManager.removeAllCookies(null)
                p.webStorage.deleteAllData()
            }
            id == DEFAULT -> {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            }
        }
    }
}
