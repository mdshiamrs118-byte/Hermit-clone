package app.nutshell

import android.content.Context
import java.io.File

/** Host-based request blocker. Matches a host and all of its parent domains. */
object ContentBlocker {
    private val BUILTIN = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "adservice.google.com", "scorecardresearch.com", "taboola.com",
        "outbrain.com", "adnxs.com", "criteo.com", "criteo.net", "hotjar.com", "amazon-adsystem.com",
        "moatads.com", "quantserve.com", "rubiconproject.com", "pubmatic.com", "openx.net"
    )

    @Volatile private var hosts: HashSet<String> = HashSet(BUILTIN)

    private fun file(ctx: Context) = File(ctx.filesDir, "blocklist.txt")

    fun load(ctx: Context) {
        val s = HashSet<String>(BUILTIN)
        val f = file(ctx)
        if (f.exists()) f.forEachLine { if (it.isNotBlank()) s.add(it) }
        hosts = s
    }

    fun isBlocked(host: String): Boolean {
        val h = hosts
        var x = host.lowercase()
        while (true) {
            if (x in h) return true
            val i = x.indexOf('.')
            if (i < 0) return false
            x = x.substring(i + 1)
        }
    }

    /** Downloads every configured list; returns number of domains stored. Call off the main thread. */
    fun refresh(ctx: Context): Int {
        val out = HashSet<String>()
        for (u in Store.blockListUrls) {
            try {
                Net.get(u).lineSequence().forEach { line -> parse(line)?.let { out.add(it) } }
            } catch (_: Exception) { /* keep going with the other lists */ }
        }
        if (out.isNotEmpty()) file(ctx).writeText(out.joinToString("\n"))
        load(ctx)
        return out.size
    }

    fun refreshIfStale(ctx: Context) {
        val f = file(ctx)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86_400_000L) refresh(ctx)
    }

    /** Understands hosts-file lines ("0.0.0.0 example.com"), bare domains, and "||example.com^" rules. */
    private fun parse(line: String): String? {
        val l = line.substringBefore('#').trim()
        if (l.isEmpty() || l.startsWith("!")) return null
        val h = when {
            l.startsWith("||") && l.endsWith("^") -> l.substring(2, l.length - 1)
            else -> {
                val parts = l.split(Regex("\\s+"))
                when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 -> parts[0]
                    else -> return null
                }
            }
        }
        if (h == "localhost" || h == "0.0.0.0" || !h.contains('.') || h.contains('/') || h.contains('*')) return null
        return h.lowercase()
    }
}
