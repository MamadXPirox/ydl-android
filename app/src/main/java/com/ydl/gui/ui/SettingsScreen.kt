package com.ydl.gui.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.YoutubeDL
import com.ydl.gui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = vm.settings
    val s by store.state.collectAsState()
    var cookiesPresent by remember { mutableStateOf(store.cookiesPath() != null) }
    var version by remember { mutableStateOf("…") }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { (ctx.applicationContext as com.ydl.gui.YdlApp).awaitEngine() }
        version = withContext(Dispatchers.IO) { runCatching { YoutubeDL.getInstance().versionName(ctx) }.getOrNull() ?: "?" }
    }

    val pickCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { i -> store.cookiesFile.outputStream().use { o -> i.copyTo(o) } }
            }
            cookiesPresent = store.cookiesPath() != null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SectionCard(stringResource(R.string.set_appearance)) {
            ChoiceChips(listOf("dark", "light"), if (s.theme == "system") "" else s.theme,
                { v -> store.update { it.copy(theme = v.ifEmpty { "system" }) } }, offLabel = stringResource(R.string.theme_system))
        }

        SectionCard(stringResource(R.string.set_download)) {
            Text("${stringResource(R.string.set_parallel)}: ${s.maxParallel}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.maxParallel.toFloat(), onValueChange = { v -> store.update { it.copy(maxParallel = v.roundToInt()) } }, valueRange = 1f..5f, steps = 3)
            Text("${stringResource(R.string.set_fragments)}: ${s.concurrentFragments}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = s.concurrentFragments.toFloat(), onValueChange = { v -> store.update { it.copy(concurrentFragments = v.roundToInt()) } }, valueRange = 1f..16f, steps = 14, enabled = !s.useAria2c)
            SwitchRow(stringResource(R.string.set_aria2c), s.useAria2c) { v -> store.update { it.copy(useAria2c = v) } }
            SwitchRow(stringResource(R.string.set_no_overwrites), s.noOverwrites) { v -> store.update { it.copy(noOverwrites = v) } }
            LabeledField(stringResource(R.string.set_template), s.outputTemplate, onChange = { v -> store.update { it.copy(outputTemplate = v) } })
            Text(stringResource(R.string.saved_to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(stringResource(R.string.set_network)) {
            LabeledField(stringResource(R.string.set_proxy), s.proxy, onChange = { v -> store.update { it.copy(proxy = v) } })
            Text(stringResource(R.string.set_cookies), style = MaterialTheme.typography.labelMedium)
            Text(if (cookiesPresent) stringResource(R.string.cookies_loaded) else stringResource(R.string.cookies_none),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickCookies.launch(arrayOf("text/plain", "*/*")) }) { Text(stringResource(R.string.cookies_import)) }
                if (cookiesPresent) OutlinedButton(onClick = { store.cookiesFile.delete(); cookiesPresent = false }) { Text(stringResource(R.string.cookies_clear)) }
            }
        }

        SectionCard(stringResource(R.string.set_defaults)) {
            ExtraOptionsEditor(s.defaultExtra, { e -> store.update { it.copy(defaultExtra = e) } }, full = false)
        }

        SectionCard(stringResource(R.string.set_engine)) {
            Text(stringResource(R.string.engine_version, version))
            ChoiceChips(listOf("nightly", "master"), if (s.updateChannel == "stable") "" else s.updateChannel,
                { v -> store.update { it.copy(updateChannel = v.ifEmpty { "stable" }) } }, offLabel = stringResource(R.string.channel_stable))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !updating, onClick = {
                    updating = true; updateMsg = ""
                    val channel = when (s.updateChannel) {
                        "nightly" -> YoutubeDL.UpdateChannel.NIGHTLY
                        "master" -> YoutubeDL.UpdateChannel.MASTER
                        else -> YoutubeDL.UpdateChannel.STABLE
                    }
                    scope.launch {
                        val done = ctx.getString(R.string.engine_updated)
                        val same = ctx.getString(R.string.engine_uptodate)
                        updateMsg = withContext(Dispatchers.IO) {
                            try {
                                when (YoutubeDL.getInstance().updateYoutubeDL(ctx, channel)) {
                                    YoutubeDL.UpdateStatus.DONE -> done
                                    else -> same
                                }
                            } catch (e: Throwable) {
                                ctx.getString(R.string.engine_failed, e.message ?: "")
                            }
                        }
                        version = withContext(Dispatchers.IO) { runCatching { YoutubeDL.getInstance().versionName(ctx) }.getOrNull() ?: "?" }
                        updating = false
                    }
                }) { Text(if (updating) stringResource(R.string.engine_updating) else stringResource(R.string.engine_update)) }
                if (updating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            if (updateMsg.isNotEmpty()) Text(updateMsg, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.engine_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
