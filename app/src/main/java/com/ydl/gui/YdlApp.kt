package com.ydl.gui

import android.app.Application
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.ydl.gui.core.DownloadManager
import com.ydl.gui.core.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class YdlApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var downloads: DownloadManager
        private set

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady
    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        downloads = DownloadManager(this, settings) { awaitEngine() }

        // First launch unpacks python + yt-dlp + ffmpeg (takes a few seconds), so do it off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@YdlApp)
                FFmpeg.init(this@YdlApp)
                Aria2c.init(this@YdlApp)
                _engineReady.value = true
            } catch (e: Throwable) {
                _engineError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    suspend fun awaitEngine() {
        engineReady.first { it || engineError.value != null }
        engineError.value?.let { throw IllegalStateException("Engine init failed: $it") }
    }
}
