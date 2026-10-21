package com.ydl.gui.core

import android.app.Application
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Download queue. Each task runs `yt-dlp` (bundled via youtubedl-android) into a private temp
 * folder, then the result is copied to Downloads/YDL GUI through MediaStore.
 */
class DownloadManager(
    private val app: Application,
    private val settings: SettingsStore,
    private val awaitEngine: suspend () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val logBuf = ConcurrentHashMap<String, ArrayDeque<String>>()
    private val lastPush = ConcurrentHashMap<String, Long>()
    private val historyFile = File(app.filesDir, "history.json")

    init {
        _tasks.value = loadHistory()
    }

    // ---------- public API ----------

    fun enqueue(params: DownloadParams, title: String, thumbnail: String, label: String): String {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            title = title, thumbnail = thumbnail, label = label, params = params,
        )
        _tasks.update { listOf(task) + it }
        pump()
        return task.id
    }

    fun cancel(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        cancelled += id
        if (t.status == TaskStatus.QUEUED) {
            update(id) { it.copy(status = TaskStatus.CANCELLED) }
            persist()
        } else {
            YoutubeDL.getInstance().destroyProcessById(id)
        }
    }

    fun retry(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        cancelled -= id
        logBuf.remove(id)
        update(id) { it.copy(status = TaskStatus.QUEUED, percent = 0f, speed = "", eta = "", error = "", logs = emptyList()) }
        pump()
    }

    fun remove(id: String) {
        cancel(id)
        _tasks.update { l -> l.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _tasks.update { l -> l.filter { it.isActive } }
        persist()
    }

    // ---------- scheduling ----------

    @Synchronized
    private fun pump() {
        val max = settings.state.value.maxParallel.coerceIn(1, 5)
        var running = _tasks.value.count {
            it.status == TaskStatus.PREPARING || it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.POSTPROCESSING
        }
        // oldest queued first
        val queued = _tasks.value.filter { it.status == TaskStatus.QUEUED }.sortedBy { it.createdAt }
        for (t in queued) {
            if (running >= max) break
            update(t.id) { it.copy(status = TaskStatus.PREPARING) }
            running++
            launchTask(t.id)
        }
        if (_tasks.value.any { it.isActive }) DownloadService.start(app)
    }

    private fun launchTask(id: String) = scope.launch {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return@launch
        val tmp = File(app.getExternalFilesDir(null) ?: app.cacheDir, "tmp/$id").apply { mkdirs() }
        try {
            awaitEngine()
            if (cancelled.contains(id)) throw YoutubeDL.CanceledException()
            val args = ArgsBuilder.build(task.params, settings.state.value, settings.cookiesPath())
            val urls = if (task.params.rawCommand != null) emptyList() else listOf(task.params.url)
            val req = YoutubeDLRequest(urls)
            req.addOption("-P", tmp.absolutePath)
            req.addCommands(args)
            logBuf[id] = ArrayDeque()
            addLog(id, "yt-dlp " + args.joinToString(" ") + " " + urls.joinToString(" "))

            YoutubeDL.getInstance().execute(req, id, false) { progress, eta, line -> onLine(id, progress, eta, line) }

            finish(id, tmp, task.params.rawCommand != null)
        } catch (e: YoutubeDL.CanceledException) {
            update(id) { it.copy(status = TaskStatus.CANCELLED, logs = snapshot(id)) }
        } catch (e: Throwable) {
            if (cancelled.contains(id)) {
                update(id) { it.copy(status = TaskStatus.CANCELLED, logs = snapshot(id)) }
            } else {
                val msg = cleanError(e)
                addLog(id, msg)
                update(id) { it.copy(status = TaskStatus.ERROR, error = msg, logs = snapshot(id)) }
            }
        } finally {
            tmp.deleteRecursively()
            persist()
            pump()
        }
    }

    private fun finish(id: String, tmp: File, raw: Boolean) {
        update(id) { it.copy(status = TaskStatus.POSTPROCESSING, percent = 100f, speed = "", eta = "") }
        val files = tmp.walkTopDown().filter {
            it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") &&
                !it.name.endsWith(".temp") && !it.name.contains(".part-Frag")
        }.toList()
        if (files.isEmpty() && !raw) throw IllegalStateException("yt-dlp finished but produced no file")

        var mainUri: Uri? = null
        var mainName = ""
        var biggest = -1L
        for (f in files) {
            val uri = MediaExport.save(app, f)
            if (uri != null && f.length() > biggest) { biggest = f.length(); mainUri = uri; mainName = f.name }
        }
        if (files.isNotEmpty() && mainUri == null) throw IllegalStateException("Could not save file to Downloads")
        update(id) {
            it.copy(
                status = TaskStatus.COMPLETED, percent = 100f, outputName = mainName,
                outputUri = mainUri?.toString() ?: "", logs = snapshot(id),
            )
        }
    }

    // ---------- progress parsing ----------

    private val speedRe = Regex("""at\s+(\S+/s)""")
    private val postRe = Regex("""^\[(Merger|ExtractAudio|Video\w+|Embed\w+|Metadata|Fixup\w*|ModifyChapters|SponsorBlock|ThumbnailsConvertor|SubtitlesConvertor|MoveFiles|FFmpeg\w*)\]""")

    private fun onLine(id: String, progress: Float, etaSec: Long, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val isProgressLine = trimmed.startsWith("[download]") && trimmed.contains("%")
        if (!isProgressLine) addLog(id, trimmed)

        val now = System.currentTimeMillis()
        val post = postRe.containsMatchIn(trimmed)
        if (!post && !isProgressLine && now - (lastPush[id] ?: 0) < 400) return
        if (isProgressLine && now - (lastPush[id] ?: 0) < 300) return
        lastPush[id] = now

        update(id) { t ->
            when {
                post -> t.copy(status = TaskStatus.POSTPROCESSING, percent = 100f, speed = "", eta = "", logs = snapshot(id))
                progress >= 0f -> t.copy(
                    status = TaskStatus.DOWNLOADING,
                    percent = progress,
                    speed = speedRe.find(trimmed)?.groupValues?.get(1) ?: t.speed,
                    eta = if (etaSec > 0) formatDuration(etaSec) else "",
                    logs = snapshot(id),
                )
                else -> t.copy(logs = snapshot(id))
            }
        }
    }

    private fun addLog(id: String, line: String) {
        val q = logBuf.getOrPut(id) { ArrayDeque() }
        synchronized(q) {
            q.addLast(line)
            while (q.size > 400) q.removeFirst()
        }
    }

    private fun snapshot(id: String): List<String> {
        val q = logBuf[id] ?: return emptyList()
        return synchronized(q) { q.toList() }
    }

    private fun cleanError(e: Throwable): String {
        val raw = (e as? YoutubeDLException)?.message ?: e.message ?: e.javaClass.simpleName
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val err = lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: raw
        return err.take(500)
    }

    // ---------- state + persistence ----------

    private fun update(id: String, f: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val finished = _tasks.value.filterNot { it.isActive }.take(200).map { it.copy(logs = it.logs.takeLast(60)) }
            historyFile.writeText(AppJson.encodeToString(finished))
        }
    }

    private fun loadHistory(): List<DownloadTask> = runCatching {
        AppJson.decodeFromString<List<DownloadTask>>(historyFile.readText())
    }.getOrDefault(emptyList())
}
