package app.nutshell

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val PALETTE = intArrayOf(
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
    0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF039BE5.toInt(), 0xFF00ACC1.toInt(),
    0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFF7CB342.toInt(), 0xFFFDD835.toInt(),
    0xFFFB8C00.toInt(), 0xFFF4511E.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt()
)

/** One "lite app": a website with its own look, settings and (optionally) isolated storage. */
data class LiteApp(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    var name: String = "",
    var url: String = "https://",
    var color: Int = PALETTE[8],
    var javascript: Boolean = true,
    var desktopMode: Boolean = false,
    var blockContent: Boolean = true,
    var blockThirdPartyCookies: Boolean = true,
    var allowLocation: Boolean = false,
    var allowMedia: Boolean = false,
    var sandbox: Boolean = true,
    var scopeToSite: Boolean = true,
    var forceDark: Boolean = false,
    var textZoom: Int = 100,
    var feedUrl: String = "",
    var notify: Boolean = false,
    var blockPopups: Boolean = true,
    var frameless: Boolean = false,
    var fullscreen: Boolean = false,
    var enabledScripts: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("color", color)
        put("javascript", javascript); put("desktopMode", desktopMode)
        put("blockContent", blockContent); put("blockThirdPartyCookies", blockThirdPartyCookies)
        put("allowLocation", allowLocation); put("allowMedia", allowMedia)
        put("sandbox", sandbox); put("scopeToSite", scopeToSite); put("forceDark", forceDark)
        put("textZoom", textZoom); put("feedUrl", feedUrl); put("notify", notify)
        put("blockPopups", blockPopups); put("frameless", frameless); put("fullscreen", fullscreen)
        put("enabledScripts", JSONArray(enabledScripts))
    }

    companion object {
        fun fromJson(o: JSONObject) = LiteApp(
            id = o.getString("id"),
            name = o.optString("name"),
            url = o.optString("url", "https://"),
            color = o.optInt("color", PALETTE[8]),
            javascript = o.optBoolean("javascript", true),
            desktopMode = o.optBoolean("desktopMode", false),
            blockContent = o.optBoolean("blockContent", true),
            blockThirdPartyCookies = o.optBoolean("blockThirdPartyCookies", true),
            allowLocation = o.optBoolean("allowLocation", false),
            allowMedia = o.optBoolean("allowMedia", false),
            sandbox = o.optBoolean("sandbox", true),
            scopeToSite = o.optBoolean("scopeToSite", true),
            forceDark = o.optBoolean("forceDark", false),
            textZoom = o.optInt("textZoom", 100),
            feedUrl = o.optString("feedUrl", ""),
            notify = o.optBoolean("notify", false),
            blockPopups = o.optBoolean("blockPopups", true),
            frameless = o.optBoolean("frameless", false),
            fullscreen = o.optBoolean("fullscreen", false),
            enabledScripts = o.optJSONArray("enabledScripts")?.let { a -> List(a.length()) { a.getString(it) } }
                ?: emptyList()
        )
    }
}

object Store {
    const val DEFAULT_LISTS = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("nutshell", Context.MODE_PRIVATE)
    }

    @Synchronized fun apps(): MutableList<LiteApp> {
        val arr = JSONArray(sp.getString("apps", "[]"))
        return MutableList(arr.length()) { LiteApp.fromJson(arr.getJSONObject(it)) }
    }

    @Synchronized private fun saveApps(list: List<LiteApp>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("apps", arr.toString()).apply()
    }

    fun get(id: String): LiteApp? = apps().firstOrNull { it.id == id }

    fun upsert(app: LiteApp) {
        val l = apps()
        val i = l.indexOfFirst { it.id == app.id }
        if (i >= 0) l[i] = app else l.add(app)
        saveApps(l)
    }

    fun delete(id: String) = saveApps(apps().filter { it.id != id })

    var blockListUrls: List<String>
        get() = sp.getString("blocklists", DEFAULT_LISTS)!!.lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(v) { sp.edit().putString("blocklists", v.joinToString("\n")).apply() }

    fun userScripts(): MutableList<String> {
        val arr = JSONArray(sp.getString("userscripts", "[]"))
        return MutableList(arr.length()) { arr.getString(it) }
    }

    fun saveUserScripts(list: List<String>) {
        sp.edit().putString("userscripts", JSONArray(list).toString()).apply()
    }

    var gridView: Boolean
        get() = sp.getBoolean("grid", false)
        set(v) { sp.edit().putBoolean("grid", v).apply() }

    fun seen(id: String): List<String> =
        sp.getString("seen_$id", "")!!.lines().filter { it.isNotEmpty() }

    fun setSeen(id: String, v: List<String>) {
        sp.edit().putString("seen_$id", v.joinToString("\n")).apply()
    }
}
