package app.nutshell

/** Minimal Tampermonkey-style user script: parses the metadata block and wraps the code for injection. */
class UserScript(val source: String) {
    val name: String
    val code: String
    private val patterns: List<Regex>
    val hasPatterns: Boolean get() = patterns.isNotEmpty()

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

// ---------------------------------------------------------------------------------------
// Built-in scripts shown in the Quick Settings panel. Each lite app opts in to the ones it wants.
// ---------------------------------------------------------------------------------------

data class BuiltinScript(val key: String, val name: String, val icon: Int, val js: String?)

private const val JS_NO_AUTOPLAY = """
var last=0;['click','touchstart','keydown'].forEach(function(e){addEventListener(e,function(){last=Date.now()},true)});
addEventListener('play',function(e){var t=e.target;if(t&&t.pause&&Date.now()-last>1500){t.pause()}},true);
document.querySelectorAll('video[autoplay],audio[autoplay]').forEach(function(v){v.removeAttribute('autoplay');v.pause()});"""

private const val JS_FORCE_COPY = """
var s=document.createElement('style');
s.textContent='*{-webkit-user-select:text!important;user-select:text!important;-webkit-touch-callout:default!important}';
(document.head||document.documentElement).appendChild(s);
['copy','cut','paste','selectstart','contextmenu','dragstart'].forEach(function(e){
document.addEventListener(e,function(ev){ev.stopPropagation()},true)});"""

private const val JS_FORCE_ZOOM = """
var m=document.querySelector('meta[name=viewport]');
if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}
m.content='width=device-width, initial-scale=1, minimum-scale=0.5, maximum-scale=10, user-scalable=yes';"""

private const val JS_SNOW = """
if(!window.__nsSnow){window.__nsSnow=true;
var c=document.createElement('canvas');
c.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483647';
document.documentElement.appendChild(c);var x=c.getContext('2d');var f=[];
function size(){c.width=innerWidth;c.height=innerHeight}size();addEventListener('resize',size);
for(var i=0;i<90;i++){f.push({x:Math.random()*c.width,y:Math.random()*c.height,r:1+Math.random()*3,s:0.5+Math.random()*1.5,d:Math.random()*6.28})}
function tick(){x.clearRect(0,0,c.width,c.height);x.fillStyle='rgba(255,255,255,0.9)';x.beginPath();
f.forEach(function(p){p.y+=p.s;p.d+=0.01;p.x+=Math.sin(p.d);
if(p.y>c.height){p.y=-5;p.x=Math.random()*c.width}x.moveTo(p.x,p.y);x.arc(p.x,p.y,p.r,0,6.28)});
x.fill();requestAnimationFrame(tick)}tick()}"""

private const val JS_NO_FOOTERS = """
var s=document.createElement('style');
s.textContent='footer,[role=contentinfo],#footer,.footer,.site-footer{display:none!important}';
(document.head||document.documentElement).appendChild(s);"""

object Scripts {
    val builtins = listOf(
        BuiltinScript("builtin:noautoplay", "Disable Video AutoPlay", R.drawable.ic_block, JS_NO_AUTOPLAY),
        BuiltinScript("builtin:copy", "Force Enable Text Copy", R.drawable.ic_content_copy, JS_FORCE_COPY),
        BuiltinScript("builtin:zoom", "Force Enable Zoom", R.drawable.ic_zoom_in, JS_FORCE_ZOOM),
        BuiltinScript("builtin:translate", "Google Translate", R.drawable.ic_translate, null), // action, not a toggle
        BuiltinScript("builtin:snow", "Let It Snow!", R.drawable.ic_snow, JS_SNOW),
        BuiltinScript("builtin:footers", "Remove Footers", R.drawable.ic_extension, JS_NO_FOOTERS)
    )

    fun userKey(source: String) = "user:" + source.hashCode()

    private fun wrap(js: String) = "(function(){try{$js}catch(e){}})();"

    /** Scripts to run when [url] finishes loading in [app]. */
    fun forPage(app: LiteApp, url: String): List<String> {
        if (app.enabledScripts.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        builtins.forEach { b -> if (b.js != null && b.key in app.enabledScripts) out.add(wrap(b.js)) }
        Store.userScripts().filter { userKey(it) in app.enabledScripts }.map { UserScript(it) }
            .filter { !it.hasPatterns || it.appliesTo(url) }.forEach { out.add(it.wrapped()) }
        return out
    }

    /** The script for a single key, used to apply it immediately when the checkbox is ticked. */
    fun forKey(key: String, url: String): String? {
        builtins.firstOrNull { it.key == key }?.let { return it.js?.let { js -> wrap(js) } }
        val src = Store.userScripts().firstOrNull { userKey(it) == key } ?: return null
        val s = UserScript(src)
        return if (!s.hasPatterns || s.appliesTo(url)) s.wrapped() else null
    }
}
