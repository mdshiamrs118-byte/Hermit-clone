package app.nutshell

import android.content.Context
import java.io.File

/** Host-based request blocker built from the enabled block lists. Matches a host and its parent domains. */
object ContentBlocker {
    private val BUILTIN = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "adservice.google.com", "scorecardresearch.com", "taboola.com",
        "outbrain.com", "adnxs.com", "criteo.com", "criteo.net", "hotjar.com", "amazon-adsystem.com"
    )

    @Volatile private var hosts: HashSet<String> = HashSet(BUILTIN)

    fun size(): Int = hosts.size

    private fun dir(ctx: Context) = File(ctx.filesDir, "blocklists").also { it.mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.txt")

    /** Rebuilds the in-memory set from the enabled lists that have been downloaded. */
    fun load(ctx: Context) {
        val s = HashSet<String>(BUILTIN)
        Store.blockLists().filter { it.enabled }.forEach { l ->
            val f = file(ctx, l.id)
            if (f.exists()) f.forEachLine { if (it.isNotBlank()) s.add(it) }
        }
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

    /** Downloads one list and records its host count. Call off the main thread. */
    fun download(ctx: Context, list: BlockList): Int {
        val out = HashSet<String>()
        Net.get(list.url).lineSequence().forEach { line -> parse(line)?.let { out.add(it) } }
        file(ctx, list.id).writeText(out.joinToString("\n"))
        Store.saveBlockLists(Store.blockLists().map { if (it.id == list.id) it.copy(hosts = out.size) else it })
        return out.size
    }

    /** Re-downloads every enabled list, then reloads. Returns the number of blocked hosts. */
    fun refresh(ctx: Context): Int {
        Store.blockLists().filter { it.enabled }.forEach {
            try { download(ctx, it) } catch (_: Exception) { /* keep going with the other lists */ }
        }
        Store.blockListsUpdatedAt = System.currentTimeMillis()
        load(ctx)
        return hosts.size
    }

    fun refreshIfStale(ctx: Context) {
        if (System.currentTimeMillis() - Store.blockListsUpdatedAt > 7 * 86_400_000L) refresh(ctx)
    }

    fun remove(ctx: Context, id: String) {
        file(ctx, id).delete()
        Store.saveBlockLists(Store.blockLists().filter { it.id != id })
        load(ctx)
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
