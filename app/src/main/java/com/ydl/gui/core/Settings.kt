package com.ydl.gui.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    /** system | dark | light */
    val theme: String = "system",
    val maxParallel: Int = 2,
    val concurrentFragments: Int = 4,
    val useAria2c: Boolean = false,
    val noOverwrites: Boolean = false,
    val outputTemplate: String = "%(title).200s.%(ext)s",
    val proxy: String = "",
    /** stable | nightly | master */
    val updateChannel: String = "stable",
    val defaultExtra: ExtraOptions = ExtraOptions(),
)

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

class SettingsStore(private val context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state

    val cookiesFile: File get() = File(context.filesDir, "cookies.txt")
    fun cookiesPath(): String? = cookiesFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath

    fun update(block: (Settings) -> Settings) {
        _state.value = block(_state.value)
        runCatching { file.writeText(AppJson.encodeToString(_state.value)) }
    }

    private fun load(): Settings = runCatching {
        AppJson.decodeFromString<Settings>(file.readText())
    }.getOrDefault(Settings())
}
