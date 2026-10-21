package com.ydl.gui.core

import kotlinx.serialization.Serializable

/** One stream/format reported by yt-dlp. */
data class FormatItem(
    val id: String,
    val ext: String,
    val resolution: String,
    val height: Int,
    val fps: Int,
    val vcodec: String,
    val acodec: String,
    val size: Long,
    val sizeApprox: Boolean,
    val note: String,
    val abr: Int,
    val tbr: Int,
    val language: String,
) {
    val hasVideo get() = vcodec.isNotEmpty() && vcodec != "none"
    val hasAudio get() = acodec.isNotEmpty() && acodec != "none"

    fun label(): String = buildString {
        if (hasVideo) {
            append(if (height > 0) "${height}p" else resolution.ifEmpty { id })
            if (fps > 30) append(fps)
            append(" · ").append(ext)
            append(" · ").append(shortCodec(vcodec))
            if (hasAudio) append(" · +audio")
        } else {
            append(if (abr > 0) "${abr}k" else id)
            append(" · ").append(ext)
            append(" · ").append(shortCodec(acodec))
            if (language.isNotEmpty()) append(" · ").append(language)
        }
        if (size > 0) append(" · ").append(if (sizeApprox) "~" else "").append(formatBytes(size))
    }
}

fun shortCodec(c: String) = c.substringBefore('.').ifEmpty { "?" }

data class PlaylistEntry(val index: Int, val title: String, val url: String, val duration: Long)

/** "sub:en" (uploaded) or "auto:en" (auto-generated). */
data class SubtitleOption(val key: String, val lang: String, val auto: Boolean)

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String,
    val isPlaylist: Boolean,
    val videoFormats: List<FormatItem>,
    val audioFormats: List<FormatItem>,
    val subtitles: List<SubtitleOption>,
    val entries: List<PlaylistEntry>,
    val isLive: Boolean,
)

@Serializable
data class ExtraOptions(
    val embedSubs: Boolean = false,
    val embedThumbnail: Boolean = false,
    val writeThumbnail: Boolean = false,
    val writeDescription: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedChapters: Boolean = false,
    val sponsorblockRemove: Boolean = false,
    val extractAudio: Boolean = false,
    val audioConvertFormat: String = "",
    val noMerge: Boolean = false,
    val recodeFormat: String = "",
    val remuxFormat: String = "",
    val limitRate: String = "",
    val ffmpegArgs: String = "",
    val customArgs: String = "",
)

@Serializable
data class DownloadParams(
    val url: String,
    /** default | video | audio */
    val mode: String = "default",
    /** Ready-made -f selector (quality presets). Wins over manual format ids. */
    val preset: String? = null,
    val videoFormat: String? = null,
    val audioFormat: String? = null,
    val videoHasAudio: Boolean = false,
    val subtitles: List<String> = emptyList(),
    val startSec: Double? = null,
    val endSec: Double? = null,
    val noPlaylist: Boolean = true,
    val playlistItems: String? = null,
    val liveFromStart: Boolean = false,
    val extra: ExtraOptions = ExtraOptions(),
    /** When set, the task is a raw yt-dlp command (Tools tab); everything else is ignored. */
    val rawCommand: String? = null,
)

@Serializable
enum class TaskStatus { PREPARING, QUEUED, DOWNLOADING, POSTPROCESSING, COMPLETED, ERROR, CANCELLED }

@Serializable
data class DownloadTask(
    val id: String,
    val title: String,
    val thumbnail: String = "",
    val label: String = "",
    val params: DownloadParams,
    val status: TaskStatus = TaskStatus.QUEUED,
    val percent: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val error: String = "",
    val outputName: String = "",
    val outputUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val logs: List<String> = emptyList(),
) {
    val isActive get() = status == TaskStatus.QUEUED || status == TaskStatus.PREPARING ||
        status == TaskStatus.DOWNLOADING || status == TaskStatus.POSTPROCESSING
}

fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${v.toInt()} ${units[i]}" else "%.1f %s".format(v, units[i])
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1:23:45" | "83:20" | "90" -> seconds, null when blank/invalid. */
fun parseTimeToSeconds(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(":").map { it.trim().toDoubleOrNull() ?: return null }
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}
