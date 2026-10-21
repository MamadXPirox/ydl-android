package com.ydl.gui.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.ydl.gui.YdlApp
import com.ydl.gui.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ParseState {
    data object Idle : ParseState
    data object Loading : ParseState
    data class Error(val message: String) : ParseState
    data class Ready(val info: MediaInfo, val singleFromPlaylist: Boolean) : ParseState
}

/** Everything the user can pick on the result screen. */
data class Selection(
    val mode: String = "default",           // default | video | audio
    val preset: String? = null,             // quality preset selector
    val manual: Boolean = false,
    val videoId: String? = null,
    val audioId: String? = null,
    val subs: Set<String> = emptySet(),
    val playlist: Set<Int> = emptySet(),
    val trimOn: Boolean = false,
    val start: String = "",
    val end: String = "",
    val liveFromStart: Boolean = false,
    val extra: ExtraOptions = ExtraOptions(),
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val ydl get() = app as YdlApp
    val settings get() = ydl.settings
    val downloads get() = ydl.downloads

    var tab by androidx.compose.runtime.mutableIntStateOf(0)
    var url by mutableStateOf("")
    var parse by mutableStateOf<ParseState>(ParseState.Idle)
        private set
    var selection by mutableStateOf(Selection())
    private var job: Job? = null

    fun select(f: (Selection) -> Selection) { selection = f(selection) }

    fun parseUrl(forcePlaylist: Boolean = false) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            parse = ParseState.Error("invalid_url"); return
        }
        job?.cancel()
        parse = ParseState.Loading
        job = viewModelScope.launch {
            try {
                val hasBoth = u.contains("list=") && (u.contains("v=") || u.contains("youtu.be/"))
                val single = hasBoth && !forcePlaylist
                val info = withContext(Dispatchers.IO) { fetch(u, single) }
                selection = Selection(
                    extra = settings.state.value.defaultExtra,
                    playlist = info.entries.map { it.index }.toSet(),
                )
                parse = ParseState.Ready(info, single)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                parse = ParseState.Error(shortError(e))
            }
        }
    }

    fun reset() { job?.cancel(); parse = ParseState.Idle }

    private suspend fun fetch(u: String, noPlaylist: Boolean): MediaInfo {
        ydl.awaitEngine()
        val s = settings.state.value
        val req = YoutubeDLRequest(u)
        req.addOption("-J")
        req.addOption("--flat-playlist")
        req.addOption("--no-warnings")
        req.addOption("--ignore-config")
        if (noPlaylist) req.addOption("--no-playlist")
        if (s.proxy.isNotBlank()) req.addOption("--proxy", s.proxy.trim())
        settings.cookiesPath()?.let { req.addOption("--cookies", it) }
        val resp = YoutubeDL.getInstance().execute(req, null, null)
        return InfoParser.parse(u, resp.out)
    }

    /** Builds params from the current selection and queues the download. */
    fun startDownload() {
        val ready = parse as? ParseState.Ready ?: return
        val info = ready.info
        val sel = selection
        val manualOn = sel.manual
        val vf = info.videoFormats.firstOrNull { it.id == sel.videoId }
        val af = info.audioFormats.firstOrNull { it.id == sel.audioId }

        var items: String? = null
        if (info.isPlaylist) {
            items = compressRanges(sel.playlist.sorted())
            if (items.isEmpty()) return
        }
        val params = DownloadParams(
            url = info.url,
            mode = sel.mode,
            preset = if (sel.mode == "default" && !manualOn) sel.preset else null,
            videoFormat = if (manualOn && sel.mode != "audio") vf?.id else null,
            audioFormat = if (manualOn && sel.mode != "video") af?.id else null,
            videoHasAudio = vf?.hasAudio == true,
            subtitles = sel.subs.toList(),
            startSec = if (sel.trimOn) parseTimeToSeconds(sel.start) else null,
            endSec = if (sel.trimOn) parseTimeToSeconds(sel.end) else null,
            noPlaylist = !info.isPlaylist,
            playlistItems = items,
            liveFromStart = sel.liveFromStart,
            extra = sel.extra,
        )
        val label = when {
            sel.mode == "audio" -> "Audio"
            sel.mode == "video" -> "Video"
            sel.preset != null && !manualOn -> sel.preset.substringAfter("height<=").substringBefore("]") + "p"
            else -> "Best"
        }
        downloads.enqueue(params, info.title, info.thumbnail, label)
    }

    fun runRaw(command: String) {
        if (command.isBlank()) return
        downloads.enqueue(
            DownloadParams(url = "", rawCommand = command.trim()),
            title = "yt-dlp " + command.trim().take(60), thumbnail = "", label = "CMD",
        )
    }

    /** [1,2,3,5,7,8] -> "1-3,5,7-8" */
    private fun compressRanges(l: List<Int>): String {
        if (l.isEmpty()) return ""
        val out = mutableListOf<String>()
        var s = l[0]; var p = l[0]
        for (i in 1 until l.size) {
            if (l[i] == p + 1) { p = l[i]; continue }
            out += if (s == p) "$s" else "$s-$p"; s = l[i]; p = l[i]
        }
        out += if (s == p) "$s" else "$s-$p"
        return out.joinToString(",")
    }

    private fun shortError(e: Throwable): String {
        val raw = e.message ?: e.javaClass.simpleName
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: raw).take(400)
    }
}
