package app.nutshell

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.toast(s: CharSequence) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Int.isLightColor(): Boolean = ColorUtils.calculateLuminance(this) > 0.5
fun Int.onColor(): Int = if (isLightColor()) Color.BLACK else Color.WHITE

/** Pads a root view by the system bars / cutout / keyboard (needed for edge-to-edge on API 35+). */
fun View.applySystemBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val b = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        v.setPadding(b.left, b.top, b.right, b.bottom)
        insets
    }
}

/** Rough "same site" check: compares registrable domains (handles common co.uk-style suffixes). */
fun baseDomain(host: String): String {
    val p = host.lowercase().trimEnd('.').split('.')
    if (p.size <= 2) return p.joinToString(".")
    val n = if (p.last().length == 2 && p[p.size - 2].length <= 3) 3 else 2
    return p.takeLast(n).joinToString(".")
}

private val AUTH_HOSTS = setOf(
    "accounts.google.com", "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
    "github.com", "www.facebook.com", "m.facebook.com", "twitter.com", "x.com", "api.twitter.com"
)

/** Login providers stay inside the lite app so OAuth flows keep working. */
fun isAuthHost(host: String): Boolean =
    host in AUTH_HOSTS || host.endsWith(".auth0.com") || host.endsWith(".okta.com")

fun letterIcon(label: String, color: Int, size: Int = 192): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = color
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = color.onColor()
    p.textSize = size * 0.5f
    p.textAlign = Paint.Align.CENTER
    p.typeface = Typeface.DEFAULT_BOLD
    val y = size / 2f - (p.descent() + p.ascent()) / 2f
    c.drawText(label.take(1).uppercase().ifEmpty { "?" }, size / 2f, y, p)
    return bmp
}

fun pinShortcut(ctx: Context, app: LiteApp) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
        ctx.toast("This launcher can’t pin shortcuts"); return
    }
    val info = ShortcutInfoCompat.Builder(ctx, "app_${app.id}")
        .setShortLabel(app.name)
        .setIcon(IconCompat.createWithBitmap(letterIcon(app.name, app.color)))
        .setIntent(BrowserActivity.launchIntent(ctx, app.id))
        .build()
    ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}

object Net {
    fun get(url: String, maxBytes: Int = 15_000_000): String =
        String(getBytes(url, maxBytes), Charsets.UTF_8)

    fun getBytes(url: String, maxBytes: Int = 15_000_000): ByteArray {
        val u = URL(url)
        require(u.protocol == "https" || u.protocol == "http") { "Unsupported scheme" }
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Nutshell/1.0")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { ins ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("Response too large")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }
}
