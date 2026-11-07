package app.nutshell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File

/** Keeps each lite app's site icon on disk so the home screen can show it. */
object IconStore {
    private val cache = LruCache<String, Bitmap>(48)

    private fun file(ctx: Context, id: String): File {
        val dir = File(ctx.filesDir, "icons").also { it.mkdirs() }
        return File(dir, "$id.png")
    }

    fun has(ctx: Context, id: String) = file(ctx, id).exists()

    fun load(ctx: Context, id: String): Bitmap? {
        cache.get(id)?.let { return it }
        val f = file(ctx, id)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { cache.put(id, it) }
    }

    /** Icons the page itself reports are better than a guessed /favicon.ico, but don't rewrite them constantly. */
    fun savePageIcon(ctx: Context, id: String, bmp: Bitmap) {
        val f = file(ctx, id)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 10 * 60_000L) return
        write(f, id, bmp)
    }

    /** A user-chosen icon (picked, drawn or typed). Written immediately and never overwritten by the site's icon. */
    fun setCustom(ctx: Context, id: String, bmp: Bitmap) = write(file(ctx, id), id, bmp)

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        cache.remove(id)
    }

    private const val ICON_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun decode(bytes: ByteArray): Bitmap? =
        try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }

    private fun declaredSize(e: Element): Int = e.attr("sizes").lowercase().substringBefore('x').toIntOrNull() ?: 0

    /**
     * Finds a site's icon the way a browser does: read the <link rel="icon"> tags in its page
     * (home-screen "apple-touch" icons first, then the largest), and fall back to /favicon.ico.
     * Call off the main thread.
     */
    fun discover(pageUrl: String): Bitmap? {
        val host = Uri.parse(pageUrl).host ?: return null
        val candidates = ArrayList<String>()
        try {
            val html = String(Net.getBytes(pageUrl, 400_000, ICON_UA, truncate = true), Charsets.UTF_8)
            Jsoup.parse(html, pageUrl).select("link[rel][href]")
                .filter { it.attr("rel").lowercase().contains("icon") }
                .sortedWith(compareByDescending<Element> { it.attr("rel").lowercase().contains("apple") }
                    .thenByDescending { declaredSize(it) })
                .map { it.absUrl("href") }
                .filter { it.startsWith("http") && !it.substringBefore('?').lowercase().endsWith(".svg") }
                .let { candidates.addAll(it) }
        } catch (_: Exception) { /* fall through to the default locations */ }
        candidates.add("https://$host/apple-touch-icon.png")
        candidates.add("https://$host/favicon.ico")
        for (c in candidates.distinct().take(6)) {
            try {
                decode(Net.getBytes(c, 800_000, ICON_UA))?.let { if (it.width >= 16) return it }
            } catch (_: Exception) { /* try the next one */ }
        }
        return null
    }

    /** Fetches and stores a lite app's icon. Call off the main thread. */
    fun fetch(ctx: Context, app: LiteApp): Boolean {
        val bmp = discover(app.url) ?: return false
        write(file(ctx, app.id), app.id, bmp)
        return true
    }

    /** Stores an icon the gallery already downloaded as the icon of a newly added lite app. */
    fun adopt(ctx: Context, id: String, bmp: Bitmap) = write(file(ctx, id), id, bmp)

    // ---- gallery icons (loaded as you browse the ready-made list; cached on disk) -----------

    private val galleryMemory = LruCache<String, Bitmap>(200)
    private val galleryMisses = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun galleryFile(ctx: Context, host: String): File {
        val dir = File(ctx.cacheDir, "gallery_icons").also { it.mkdirs() }
        return File(dir, host.replace(Regex("[^A-Za-z0-9.-]"), "_") + ".png")
    }

    fun galleryCached(ctx: Context, host: String): Bitmap? {
        galleryMemory.get(host)?.let { return it }
        val f = galleryFile(ctx, host)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { galleryMemory.put(host, it) }
    }

    /** Finds and caches a site's icon once; returns null if it has none. Call off the main thread. */
    fun galleryFetch(ctx: Context, host: String, pageUrl: String): Bitmap? {
        galleryCached(ctx, host)?.let { return it }
        if (host in galleryMisses) return null
        val bmp = try { discover(pageUrl) } catch (_: Exception) { null }
        if (bmp == null) { galleryMisses.add(host); return null }
        val small = if (bmp.width > 128) Bitmap.createScaledBitmap(bmp, 128, (128f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true) else bmp
        try { galleryFile(ctx, host).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { }
        galleryMemory.put(host, small)
        return small
    }

    private fun write(f: File, id: String, bmp: Bitmap) {
        val small = if (bmp.width > 192) Bitmap.createScaledBitmap(bmp, 192, (192f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true) else bmp
        f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.put(id, small)
    }
}
