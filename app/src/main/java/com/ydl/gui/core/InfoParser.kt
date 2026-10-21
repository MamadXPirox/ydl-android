package com.ydl.gui.core

import org.json.JSONArray
import org.json.JSONObject

/** Parses the JSON produced by `yt-dlp -J --flat-playlist`. */
object InfoParser {

    fun parse(inputUrl: String, json: String): MediaInfo {
        // yt-dlp may print warnings before the JSON; take from the first '{'
        val start = json.indexOf('{')
        require(start >= 0) { "Empty response from yt-dlp" }
        val o = JSONObject(json.substring(start))
        val isPlaylist = o.optString("_type") == "playlist"
        return if (isPlaylist) parsePlaylist(inputUrl, o) else parseVideo(inputUrl, o)
    }

    private fun parsePlaylist(url: String, o: JSONObject): MediaInfo {
        val entries = mutableListOf<PlaylistEntry>()
        val arr = o.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val u = e.optString("url").ifEmpty { e.optString("webpage_url") }.ifEmpty { e.optString("id") }
            entries += PlaylistEntry(
                index = i + 1,
                title = e.optString("title").ifEmpty { u },
                url = u,
                duration = e.optLong("duration", 0),
            )
        }
        return MediaInfo(
            url = url,
            title = o.optString("title").ifEmpty { "Playlist" },
            uploader = o.optString("uploader").ifEmpty { o.optString("channel") },
            duration = 0,
            thumbnail = bestThumb(o),
            isPlaylist = true,
            videoFormats = emptyList(),
            audioFormats = emptyList(),
            subtitles = emptyList(),
            entries = entries,
            isLive = false,
        )
    }

    private fun parseVideo(url: String, o: JSONObject): MediaInfo {
        val video = mutableListOf<FormatItem>()
        val audio = mutableListOf<FormatItem>()
        val fmts = o.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until fmts.length()) {
            val f = fmts.optJSONObject(i) ?: continue
            val ext = f.optString("ext")
            val id = f.optString("format_id")
            val note = f.optString("format_note")
            if (ext == "mhtml" || id.startsWith("sb") || note.contains("storyboard", true)) continue
            val vc = f.optString("vcodec", "none").ifEmpty { "none" }
            val ac = f.optString("acodec", "none").ifEmpty { "none" }
            if (vc == "none" && ac == "none") continue
            val size = f.optLong("filesize", 0).takeIf { it > 0 } ?: f.optLong("filesize_approx", 0)
            val item = FormatItem(
                id = id, ext = ext,
                resolution = f.optString("resolution"),
                height = f.optInt("height", 0),
                fps = f.optDouble("fps", 0.0).toInt(),
                vcodec = vc, acodec = ac,
                size = size,
                sizeApprox = f.optLong("filesize", 0) <= 0,
                note = note,
                abr = f.optDouble("abr", 0.0).toInt(),
                tbr = f.optDouble("tbr", 0.0).toInt(),
                language = f.optString("language").takeIf { it != "null" } ?: "",
            )
            if (item.hasVideo) video += item else audio += item
        }
        video.sortWith(compareByDescending<FormatItem> { it.height }.thenByDescending { it.fps }.thenByDescending { it.tbr })
        audio.sortByDescending { it.abr }

        val subs = mutableListOf<SubtitleOption>()
        o.optJSONObject("subtitles")?.keys()?.forEach { subs += SubtitleOption("sub:$it", it, false) }
        o.optJSONObject("automatic_captions")?.keys()?.forEach { subs += SubtitleOption("auto:$it", it, true) }

        return MediaInfo(
            url = o.optString("webpage_url").ifEmpty { url },
            title = o.optString("title").ifEmpty { url },
            uploader = o.optString("uploader").ifEmpty { o.optString("channel") },
            duration = o.optLong("duration", 0),
            thumbnail = bestThumb(o),
            isPlaylist = false,
            videoFormats = video,
            audioFormats = audio,
            subtitles = subs,
            entries = emptyList(),
            isLive = o.optBoolean("is_live", false),
        )
    }

    private fun bestThumb(o: JSONObject): String {
        o.optString("thumbnail").takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
        val t = o.optJSONArray("thumbnails") ?: return ""
        for (i in t.length() - 1 downTo 0) {
            val u = t.optJSONObject(i)?.optString("url").orEmpty()
            if (u.startsWith("http")) return u
        }
        return ""
    }
}
