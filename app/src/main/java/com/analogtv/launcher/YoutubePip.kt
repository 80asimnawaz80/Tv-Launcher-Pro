package com.analogtv.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * YouTube ko PIP card ke andar chalata hai (official YouTube IFrame player, WebView me).
 * Link / video ID / playlist link se. Jin videos ka "embed" band hota hai wo yahan nahi chalte.
 */
class YoutubePip(private val ctx: Context, private val container: FrameLayout) {

    class Ref(val video: String?, val list: String?)

    companion object {
        private val idRe = Regex("^[A-Za-z0-9_-]{11}$")
        private val vidRe = Regex("(?:youtu\\.be/|[?&]v=|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})")
        private val listRe = Regex("[?&]list=([A-Za-z0-9_-]{10,})")
        private val listOnlyRe = Regex("^(PL|UU|FL|RD|OL|LL)[A-Za-z0-9_-]{8,}$")

        fun parse(input: String): Ref? {
            val t = input.trim()
            if (t.isEmpty()) return null
            val video = vidRe.find(t)?.groupValues?.get(1)
            val list = listRe.find(t)?.groupValues?.get(1)
            return when {
                video != null || list != null -> Ref(video, list)
                idRe.matches(t) -> Ref(t, null)
                listOnlyRe.matches(t) -> Ref(null, t)
                else -> null
            }
        }
    }

    var ref: Ref? = null
        private set

    val active: Boolean get() = ref != null

    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(): WebView? {
        web?.let { return it }
        return try {
            val w = WebView(ctx)
            w.setBackgroundColor(Color.BLACK)
            w.isFocusable = false
            w.isFocusableInTouchMode = false
            w.overScrollMode = View.OVER_SCROLL_NEVER
            w.isVerticalScrollBarEnabled = false
            w.isHorizontalScrollBarEnabled = false
            val s = w.settings
            s.javaScriptEnabled = true
            s.domStorageEnabled = true
            s.mediaPlaybackRequiresUserGesture = false
            s.cacheMode = WebSettings.LOAD_DEFAULT
            w.webChromeClient = WebChromeClient()
            w.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame
            }
            // index 1: PlayerView ke upar, badge / controls ke neeche
            container.addView(
                w, 1,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            web = w
            w
        } catch (e: Exception) {
            null
        }
    }

    private fun html(r: Ref, muted: Boolean, vol: Int): String {
        val video = if (r.video != null) "videoId:'${r.video}'," else ""
        val list = if (r.list != null) "list:'${r.list}',listType:'playlist'," else ""
        return """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var pl;
function onYouTubeIframeAPIReady(){
  pl=new YT.Player('p',{width:'100%',height:'100%',$video
    playerVars:{$list autoplay:1,playsinline:1,rel:0,fs:0,modestbranding:1,iv_load_policy:3,controls:1},
    events:{onReady:function(e){
      if($muted){e.target.mute();}else{e.target.unMute();e.target.setVolume($vol);}
      e.target.playVideo();
    }}});
}
function jsPlay(){if(pl)pl.playVideo();}
function jsPause(){if(pl)pl.pauseVideo();}
function jsMute(m,v){if(!pl)return;if(m){pl.mute();}else{pl.unMute();pl.setVolume(v);}}
function jsNext(){if(pl)pl.nextVideo();}
function jsPrev(){if(pl)pl.previousVideo();}
</script></body></html>"""
    }

    /** false = WebView nahi bana (device me WebView install nahi). */
    fun start(r: Ref, muted: Boolean, volume: Int): Boolean {
        val w = ensureWeb() ?: return false
        ref = r
        w.visibility = View.VISIBLE
        w.loadDataWithBaseURL("https://www.youtube.com", html(r, muted, volume), "text/html", "utf-8", null)
        return true
    }

    fun stop() {
        ref = null
        web?.let {
            it.loadUrl("about:blank")
            it.visibility = View.GONE
        }
    }

    /** App background me jaye to video band (ref yaad rehta hai). */
    fun suspend() {
        web?.loadUrl("about:blank")
    }

    private fun js(code: String) {
        web?.evaluateJavascript(code, null)
    }

    fun play() = js("jsPlay()")
    fun pause() = js("jsPause()")
    fun next() = js("jsNext()")
    fun prev() = js("jsPrev()")
    fun setSound(on: Boolean, volume: Int) = js("jsMute(${!on},$volume)")

    fun watchUrl(): String {
        val r = ref ?: return "https://www.youtube.com"
        return if (r.video != null) {
            "https://www.youtube.com/watch?v=${r.video}" + (if (r.list != null) "&list=${r.list}" else "")
        } else {
            "https://www.youtube.com/playlist?list=${r.list}"
        }
    }
}
