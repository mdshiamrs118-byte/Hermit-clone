package app.nutshell

/** Minimal Tampermonkey-style user script: parses the metadata block and wraps the code for injection. */
class UserScript(val source: String) {
    val name: String
    val code: String
    private val patterns: List<Regex>

    init {
        val m = Regex("// ==UserScript==(.*?)// ==/UserScript==", RegexOption.DOT_MATCHES_ALL).find(source)
        val header = m?.groupValues?.get(1) ?: ""
        val meta = Regex("//\\s*@(\\S+)[ \\t]*(.*)").findAll(header)
            .map { it.groupValues[1] to it.groupValues[2].trim() }.toList()
        name = meta.firstOrNull { it.first == "name" }?.second ?: "Unnamed script"
        patterns = meta.filter { it.first == "match" || it.first == "include" }.map { toRegex(it.second) }
        code = if (m != null) source.removeRange(m.range) else source
    }

    fun appliesTo(url: String) = patterns.any { it.matches(url) }

    fun wrapped(): String =
        "(function(){try{" +
            "var GM_addStyle=function(c){var s=document.createElement('style');s.textContent=c;" +
            "(document.head||document.documentElement).appendChild(s)};" +
            "var GM_log=function(){console.log.apply(console,arguments)};\n" +
            code + "\n}catch(e){console.error('userscript',e)}})();"

    companion object {
        fun toRegex(p: String): Regex {
            if (p == "*") return Regex(".*")
            if (p.length > 2 && p.startsWith("/") && p.endsWith("/")) return Regex(p.substring(1, p.length - 1))
            val sb = StringBuilder()
            p.forEach { c -> if (c == '*') sb.append(".*") else sb.append(Regex.escape(c.toString())) }
            return Regex(sb.toString())
        }
    }
}
