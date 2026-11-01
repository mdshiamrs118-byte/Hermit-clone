package app.nutshell

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object FeedParser {
    data class Item(val id: String, val title: String, val link: String)

    fun parse(xml: String): List<Item> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = ArrayList<Item>()
        var title = ""; var link = ""; var id = ""
        var inItem = false
        var tag = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item" || tag == "entry") {
                        inItem = true; title = ""; link = ""; id = ""
                    } else if (inItem && tag == "link") {
                        val href = p.getAttributeValue(null, "href")
                        val rel = p.getAttributeValue(null, "rel")
                        if (href != null && (link.isEmpty() || rel == null || rel == "alternate")) link = href
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) when (tag) {
                        "title" -> if (title.isEmpty()) title = t
                        "link" -> if (link.isEmpty()) link = t
                        "guid", "id" -> if (id.isEmpty()) id = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" || p.name == "entry") {
                        inItem = false
                        if (link.isNotEmpty()) out.add(Item(id.ifEmpty { link }, title, link))
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return out
    }
}

/** Page-monitor helpers: fetch a page and read the text of the element matching a CSS selector. */
object PageMonitor {
    fun read(url: String, selector: String): String = Jsoup.parse(Net.get(url, 5_000_000), url).select(selector).text()
}

/** Periodically checks every lite app's feeds and web monitors and notifies about what's new. */
class FeedWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Store.init(applicationContext)
        for (app in Store.apps()) {
            app.feeds.forEach { try { checkFeed(app, it) } catch (_: Exception) { /* try again next cycle */ } }
            app.monitors.forEach { try { checkMonitor(app, it) } catch (_: Exception) { } }
        }
        Result.success()
    }

    private fun checkFeed(app: LiteApp, feed: Link) {
        val key = "${app.id}|${feed.url}"
        val items = FeedParser.parse(Net.get(feed.url)).take(30)
        val seen = Store.seen(key)
        val firstRun = seen.isEmpty()
        val fresh = items.filter { it.id !in seen }
        Store.setSeen(key, (items.map { it.id } + seen).distinct().take(100))
        if (!firstRun) fresh.take(3).forEach { post(app, it.id, it.title, it.link) }
    }

    private fun checkMonitor(app: LiteApp, m: Monitor) {
        val key = "${app.id}|mon|${m.url}|${m.selector}"
        val text = PageMonitor.read(m.url, m.selector)
        if (text.isEmpty()) return
        val hash = text.hashCode().toString()
        val seen = Store.seen(key)
        Store.setSeen(key, listOf(hash))
        if (seen.isNotEmpty() && hash !in seen) post(app, hash + m.url, "${m.title} changed", m.url)
    }

    @SuppressLint("MissingPermission")
    private fun post(app: LiteApp, id: String, text: String, link: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, id.hashCode(), BrowserActivity.launchIntent(ctx, app.id, link),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.name)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(app.color)
            .build()
        NotificationManagerCompat.from(ctx).notify(id.hashCode(), n)
    }
}
