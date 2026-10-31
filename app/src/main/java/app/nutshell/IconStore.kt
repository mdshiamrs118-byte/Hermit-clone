package app.nutshell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
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

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        cache.remove(id)
    }

    /** Tries the site's /favicon.ico. Call off the main thread. */
    fun fetch(ctx: Context, app: LiteApp): Boolean {
        val host = Uri.parse(app.url).host ?: return false
        val bytes = Net.getBytes("https://$host/favicon.ico", 1_000_000)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        write(file(ctx, app.id), app.id, bmp)
        return true
    }

    private fun write(f: File, id: String, bmp: Bitmap) {
        val small = if (bmp.width > 192) Bitmap.createScaledBitmap(bmp, 192, (192f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true) else bmp
        f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.put(id, small)
    }
}
