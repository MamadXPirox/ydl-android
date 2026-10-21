package com.ydl.gui.core

/**
 * Port of the desktop app's `build_download_args` (src-tauri/.../download/arguments.rs).
 * Returns yt-dlp arguments WITHOUT the URL and WITHOUT -P (the manager adds those).
 */
object ArgsBuilder {

    private const val DEFAULT_TEMPLATE = "%(title).200s.%(ext)s"

    fun formatSelector(p: DownloadParams): List<String> {
        p.preset?.takeIf { it.isNotEmpty() }?.let { return listOf("-f", it) }
        val vf = p.videoFormat?.takeIf { it.isNotEmpty() }
        val af = p.audioFormat?.takeIf { it.isNotEmpty() }
        val noMerge = p.extra.noMerge
        return when (p.mode) {
            "video" -> listOf("-f", vf ?: "bv/b")
            "audio" -> listOf("-f", af ?: "ba/b")
            else -> when {
                vf != null && af != null ->
                    if (noMerge || p.videoHasAudio) listOf("-f", vf) else listOf("-f", "$vf+$af")
                vf != null ->
                    if (noMerge || p.videoHasAudio) listOf("-f", vf) else listOf("-f", "$vf+bestaudio/$vf")
                af != null ->
                    if (noMerge) listOf("-f", af) else listOf("-f", "bestvideo+$af/$af")
                else -> emptyList()
            }
        }
    }

    fun build(p: DownloadParams, s: Settings, cookiesPath: String?): List<String> {
        // Raw command from the Tools tab: only inject proxy / cookies if the user didn't.
        p.rawCommand?.let { raw ->
            val user = splitArgs(raw)
            val out = mutableListOf<String>()
            out += "--ignore-config"
            if (s.proxy.isNotBlank() && "--proxy" !in user) out += listOf("--proxy", s.proxy.trim())
            if (cookiesPath != null && "--cookies" !in user) out += listOf("--cookies", cookiesPath)
            out += user
            return out
        }

        val a = mutableListOf("--ignore-config", "--newline", "--color", "never")
        a += formatSelector(p)

        if (s.proxy.isNotBlank()) a += listOf("--proxy", s.proxy.trim())

        a += listOf("-o", s.outputTemplate.ifBlank { DEFAULT_TEMPLATE })
        a += "--windows-filenames"      // safe file names on shared storage
        if (s.noOverwrites) a += "--no-overwrites"

        if (s.useAria2c) {
            a += listOf("--downloader", "libaria2c.so")
            a += listOf("--external-downloader-args", "aria2c:-x 16 -s 16 -k 1M")
        } else if (s.concurrentFragments > 1) {
            a += listOf("--concurrent-fragments", s.concurrentFragments.toString())
        }

        if (cookiesPath != null) a += listOf("--cookies", cookiesPath)

        val e = p.extra
        if (e.embedSubs) a += "--embed-subs"
        if (e.embedThumbnail) a += "--embed-thumbnail"
        if (e.writeThumbnail) a += "--write-thumbnail"
        if (e.writeDescription) a += "--write-description"
        if (e.embedMetadata) a += "--embed-metadata"
        if (e.embedChapters) a += "--embed-chapters"
        if (e.sponsorblockRemove) a += listOf("--sponsorblock-remove", "all")
        if (e.extractAudio || p.mode == "audio" && e.audioConvertFormat.isNotEmpty()) {
            a += "-x"
            if (e.audioConvertFormat.isNotEmpty()) a += listOf("--audio-format", e.audioConvertFormat)
        }
        if (e.recodeFormat.isNotEmpty()) a += listOf("--recode-video", e.recodeFormat)
        if (e.remuxFormat.isNotEmpty() && e.remuxFormat != "auto") {
            a += listOf("--remux-video", e.remuxFormat, "--merge-output-format", e.remuxFormat)
        }
        if (e.limitRate.isNotBlank()) a += listOf("-r", e.limitRate.trim())
        if (e.ffmpegArgs.isNotBlank()) a += listOf("--postprocessor-args", "FFmpeg:${e.ffmpegArgs.trim()}")
        if (e.customArgs.isNotBlank()) a += splitArgs(e.customArgs)

        appendSubtitleArgs(a, p.subtitles)

        val hasStart = (p.startSec ?: 0.0) > 0.0
        val hasEnd = p.endSec != null
        if (hasStart || hasEnd) {
            val start = p.startSec ?: 0.0
            val end = p.endSec?.let { trimZeros(it) } ?: "inf"
            a += listOf("--download-sections", "*${trimZeros(start)}-$end")
        }

        if (p.noPlaylist) a += "--no-playlist"
        else p.playlistItems?.takeIf { it.isNotEmpty() }?.let { a += listOf("--playlist-items", it) }

        if (p.liveFromStart) a += "--live-from-start"
        return a
    }

    private fun trimZeros(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** "sub:en" / "auto:en" -> --write-subs / --write-auto-subs / --sub-langs en */
    private fun appendSubtitleArgs(args: MutableList<String>, selections: List<String>) {
        var manual = false
        var auto = false
        val langs = LinkedHashSet<String>()
        for (sel in selections) {
            val lang = when {
                sel.startsWith("sub:") -> { manual = true; sel.removePrefix("sub:") }
                sel.startsWith("auto:") -> { auto = true; sel.removePrefix("auto:") }
                else -> { manual = true; sel }
            }
            if (lang.isNotEmpty()) langs += lang
        }
        if (langs.isEmpty()) return
        if (manual) args += "--write-subs"
        if (auto) args += "--write-auto-subs"
        args += listOf("--sub-langs", langs.joinToString(","))
    }

    /** Shell-like splitting that understands "double" and 'single' quotes. */
    fun splitArgs(input: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var has = false
        for (c in input) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> { quote = c; has = true }
                c.isWhitespace() -> if (has || cur.isNotEmpty()) { out += cur.toString(); cur.clear(); has = false }
                else -> cur.append(c)
            }
        }
        if (has || cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** Quality presets shown on the result screen. */
    val presets: List<Pair<String, String?>> = listOf(
        "Best" to null,
        "2160p" to "bv*[height<=2160]+ba/b[height<=2160]/b",
        "1440p" to "bv*[height<=1440]+ba/b[height<=1440]/b",
        "1080p" to "bv*[height<=1080]+ba/b[height<=1080]/b",
        "720p" to "bv*[height<=720]+ba/b[height<=720]/b",
        "480p" to "bv*[height<=480]+ba/b[height<=480]/b",
        "360p" to "bv*[height<=360]+ba/b[height<=360]/b",
    )
}
