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

/** A titled link: used for bookmarks, feeds, search engines and share targets. */
data class Link(val title: String, val url: String)
data class Monitor(val title: String, val url: String, val selector: String)
/** hosts == -1 means the list has never been downloaded. */
data class BlockList(val id: String, val name: String, val url: String, val enabled: Boolean, val hosts: Int)

/** One "lite app": a website with its own look, settings and storage container. */
data class LiteApp(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    var name: String = "",
    var url: String = "https://",
    var color: Int = PALETTE[8],
    // Behavior
    var fullscreen: Boolean = false,
    var frameless: Boolean = false,
    var pullToRefresh: Boolean = false,
    var loadImages: Boolean = true,
    var desktopMode: Boolean = false,
    var dataSaver: Boolean = false,
    var javascript: Boolean = true,
    /** "" = default, "desktop", or a custom user-agent string. */
    var userAgent: String = "",
    var textZoom: Int = 100,
    // Privacy
    var blockContent: Boolean = true,
    var blockPopups: Boolean = true,
    var blockThirdPartyCookies: Boolean = false,
    var openLinksInApp: Boolean = true,
    var launchNativeApps: Boolean = true,
    var installNativeApps: Boolean = true,
    var allowLocation: Boolean = false,
    var allowFiles: Boolean = false,
    var allowMedia: Boolean = false,
    // Container and theme
    var sandbox: String = "default",
    /** "system", "light" or "dark". */
    var themeMode: String = "system",
    var iconCustom: Boolean = false,
    // Content
    var enabledScripts: List<String> = emptyList(),
    var tags: List<String> = emptyList(),
    var bookmarks: List<Link> = emptyList(),
    var feeds: List<Link> = emptyList(),
    var monitors: List<Monitor> = emptyList(),
    var searches: List<Link> = emptyList(),
    var shares: List<Link> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("color", color)
        put("fullscreen", fullscreen); put("frameless", frameless); put("pullToRefresh", pullToRefresh)
        put("loadImages", loadImages); put("desktopMode", desktopMode); put("dataSaver", dataSaver)
        put("javascript", javascript); put("userAgent", userAgent); put("textZoom", textZoom)
        put("blockContent", blockContent); put("blockPopups", blockPopups)
        put("blockThirdPartyCookies", blockThirdPartyCookies); put("openLinksInApp", openLinksInApp)
        put("launchNativeApps", launchNativeApps); put("installNativeApps", installNativeApps)
        put("allowLocation", allowLocation); put("allowFiles", allowFiles); put("allowMedia", allowMedia)
        put("sandbox", sandbox); put("themeMode", themeMode); put("iconCustom", iconCustom)
        put("enabledScripts", JSONArray(enabledScripts)); put("tags", JSONArray(tags))
        put("bookmarks", linksToJson(bookmarks)); put("feeds", linksToJson(feeds))
        put("searches", linksToJson(searches)); put("shares", linksToJson(shares))
        put("monitors", JSONArray().also { arr ->
            monitors.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url).put("selector", it.selector)) }
        })
    }

    companion object {
        private fun linksToJson(l: List<Link>) = JSONArray().also { arr ->
            l.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        }

        private fun jsonToLinks(a: JSONArray?): List<Link> =
            if (a == null) emptyList() else List(a.length()) { Link(a.getJSONObject(it).optString("title"), a.getJSONObject(it).optString("url")) }

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { a.getString(it) }

        fun fromJson(o: JSONObject) = LiteApp(
            id = o.getString("id"),
            name = o.optString("name"),
            url = o.optString("url", "https://"),
            color = o.optInt("color", PALETTE[8]),
            fullscreen = o.optBoolean("fullscreen", false),
            frameless = o.optBoolean("frameless", false),
            pullToRefresh = o.optBoolean("pullToRefresh", false),
            loadImages = o.optBoolean("loadImages", true),
            desktopMode = o.optBoolean("desktopMode", false),
            dataSaver = o.optBoolean("dataSaver", false),
            javascript = o.optBoolean("javascript", true),
            userAgent = o.optString("userAgent", ""),
            textZoom = o.optInt("textZoom", 100),
            blockContent = o.optBoolean("blockContent", true),
            blockPopups = o.optBoolean("blockPopups", true),
            blockThirdPartyCookies = o.optBoolean("blockThirdPartyCookies", false),
            openLinksInApp = o.optBoolean("openLinksInApp", true),
            launchNativeApps = o.optBoolean("launchNativeApps", true),
            installNativeApps = o.optBoolean("installNativeApps", true),
            allowLocation = o.optBoolean("allowLocation", false),
            allowFiles = o.optBoolean("allowFiles", false),
            allowMedia = o.optBoolean("allowMedia", false),
            sandbox = (o.opt("sandbox") as? String) ?: "default",
            themeMode = o.optString("themeMode", "system"),
            iconCustom = o.optBoolean("iconCustom", false),
            enabledScripts = strings(o.optJSONArray("enabledScripts")),
            tags = strings(o.optJSONArray("tags")),
            bookmarks = jsonToLinks(o.optJSONArray("bookmarks")),
            feeds = jsonToLinks(o.optJSONArray("feeds")),
            searches = jsonToLinks(o.optJSONArray("searches")),
            shares = jsonToLinks(o.optJSONArray("shares")),
            monitors = o.optJSONArray("monitors")?.let { a ->
                List(a.length()) {
                    val m = a.getJSONObject(it)
                    Monitor(m.optString("title"), m.optString("url"), m.optString("selector"))
                }
            } ?: emptyList()
        )
    }
}

object Store {
    private val DEFAULT_TAGS = listOf("Help", "Technology", "Communication", "Music & Videos", "Programming", "AI - Artificial Intelligence")

    private val DEFAULT_BLOCK_LISTS = listOf(
        BlockList("adaway", "AdAway", "https://adaway.org/hosts.txt", true, -1),
        BlockList("mvps", "MVPS Hosts", "https://winhelp2002.mvps.org/hosts.txt", false, -1),
        BlockList("urlhaus", "Abuse.ch URLhaus", "https://urlhaus.abuse.ch/downloads/hostfile/", true, -1),
        BlockList("notracking", "No Tracking", "https://raw.githubusercontent.com/notracking/hosts-blocklists/master/hostnames.txt", false, -1),
        BlockList("danpollock", "SomeoneWhoCares - Dan Pollock", "https://someonewhocares.org/hosts/zero/hosts", false, -1),
        BlockList("yoyo", "pgl.yoyo.org", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext", true, -1),
        BlockList("nocoin", "NoCoin Filter List", "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt", true, -1),
        BlockList("sb-hosts", "StevenBlack/hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", true, -1),
        BlockList("sb-fakenews", "StevenBlack/fakenews", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts", false, -1),
        BlockList("sb-gambling", "StevenBlack/gambling", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling/hosts", false, -1),
        BlockList("sb-porn", "StevenBlack/porn", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts", false, -1),
        BlockList("sb-social", "StevenBlack/social", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts", false, -1)
    )

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("nutshell", Context.MODE_PRIVATE)
    }

    // ---- lite apps
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

    var gridView: Boolean
        get() = sp.getBoolean("grid", false)
        set(v) { sp.edit().putBoolean("grid", v).apply() }

    var galleryIcons: Boolean
        get() = sp.getBoolean("gallery_icons", true)
        set(v) { sp.edit().putBoolean("gallery_icons", v).apply() }

    // ---- tags (shared by all lite apps)
    fun tags(): List<String> {
        val s = sp.getString("tags", null) ?: return DEFAULT_TAGS
        val a = JSONArray(s)
        return List(a.length()) { a.getString(it) }
    }

    fun saveTags(t: List<String>) { sp.edit().putString("tags", JSONArray(t).toString()).apply() }

    // ---- sandboxes (custom names)
    fun sandboxName(id: String): String? = sp.getString("sbname_$id", null)
    fun setSandboxName(id: String, name: String) { sp.edit().putString("sbname_$id", name).apply() }

    // ---- content blocker lists
    fun blockLists(): List<BlockList> {
        val s = sp.getString("blocklists2", null) ?: return DEFAULT_BLOCK_LISTS
        val a = JSONArray(s)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            BlockList(o.getString("id"), o.getString("name"), o.getString("url"), o.optBoolean("enabled", true), o.optInt("hosts", -1))
        }
    }

    fun saveBlockLists(l: List<BlockList>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("enabled", it.enabled).put("hosts", it.hosts)) }
        sp.edit().putString("blocklists2", a.toString()).apply()
    }

    var blockListsUpdatedAt: Long
        get() = sp.getLong("blocklists_updated", 0L)
        set(v) { sp.edit().putLong("blocklists_updated", v).apply() }

    // ---- user scripts
    fun userScripts(): MutableList<String> {
        val arr = JSONArray(sp.getString("userscripts", "[]"))
        return MutableList(arr.length()) { arr.getString(it) }
    }

    fun saveUserScripts(list: List<String>) {
        sp.edit().putString("userscripts", JSONArray(list).toString()).apply()
    }

    // ---- feed / monitor bookkeeping
    fun seen(key: String): List<String> = sp.getString("seen_$key", "")!!.lines().filter { it.isNotEmpty() }
    fun setSeen(key: String, v: List<String>) { sp.edit().putString("seen_$key", v.joinToString("\n")).apply() }
}
