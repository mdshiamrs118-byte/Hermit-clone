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
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Periodically polls each lite app's RSS/Atom feed and posts a notification for new items. */
class FeedWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    data class Item(val id: String, val title: String, val link: String)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Store.init(applicationContext)
        for (app in Store.apps().filter { it.notify && it.feedUrl.isNotBlank() }) {
            try { check(app) } catch (_: Exception) { /* try again next cycle */ }
        }
        Result.success()
    }

    private fun check(app: LiteApp) {
        val items = parse(Net.get(app.feedUrl)).take(30)
        val seen = Store.seen(app.id)
        val firstRun = seen.isEmpty()
        val fresh = items.filter { it.id !in seen }
        Store.setSeen(app.id, (items.map { it.id } + seen).distinct().take(100))
        if (!firstRun) fresh.take(3).forEach { post(app, it) }
    }

    @SuppressLint("MissingPermission")
    private fun post(app: LiteApp, item: Item) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, item.id.hashCode(), BrowserActivity.launchIntent(ctx, app.id, item.link),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.name)
            .setContentText(item.title)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(app.color)
            .build()
        NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n)
    }

    private fun parse(xml: String): List<Item> {
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
