package com.ydl.gui.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer version, downloads the matching APK and hands it to the
 * system installer. (Android always shows its own "Install" confirmation — silent install is
 * not possible without root.)
 */
object AppUpdater {

    /** TODO: put your GitHub "owner/repository" here, e.g. "john/ydl-android". */
    const val REPO = "OWNER/REPO"

    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val pageUrl: String)

    class NoApkException : Exception()
    class NotConfiguredException : Exception()

    @Suppress("DEPRECATION")
    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Returns the new release, or null if you are already up to date. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (REPO.startsWith("OWNER/")) throw NotConfiguredException()
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "YDL-Android-Updater")
        }
        try {
            if (conn.responseCode == 404) throw IllegalStateException("No release has been published yet")
            if (conn.responseCode !in 200..299) throw IllegalStateException("GitHub error ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (!isNewer(tag, currentVersion(ctx))) return@withContext null

            val assets = json.optJSONArray("assets")
            val apks = buildList {
                for (i in 0 until (assets?.length() ?: 0)) {
                    val a = assets!!.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) add(a)
                }
            }
            val pick = pickAsset(apks) ?: throw NoApkException()
            UpdateInfo(
                version = tag.removePrefix("v"),
                notes = json.optString("body"),
                apkUrl = pick.getString("browser_download_url"),
                sizeBytes = pick.optLong("size", 0),
                pageUrl = json.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Prefer the APK built for this phone's CPU, then the universal one. */
    private fun pickAsset(apks: List<JSONObject>): JSONObject? {
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull { it.optString("name").contains(abi, true) }?.let { return it }
        }
        return apks.firstOrNull { it.optString("name").contains("universal", true) } ?: apks.firstOrNull()
    }

    private fun numbers(v: String): List<Int> =
        Regex("""\d+""").findAll(v.substringBefore('-')).map { it.value.toIntOrNull() ?: 0 }.toList()

    fun isNewer(remote: String, local: String): Boolean {
        val r = numbers(remote); val l = numbers(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "YDL-Android-Updater")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" screen for this app. */
    fun openInstallPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
