package com.ydl.gui.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ydl.gui.R
import com.ydl.gui.YdlApp
import com.ydl.gui.core.*

@Composable
fun HomeScreen(vm: MainViewModel) {
    val app = LocalContext.current.applicationContext as YdlApp
    val engineReady by app.engineReady.collectAsState()
    val engineError by app.engineError.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (!engineReady && engineError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        engineError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        when (val st = vm.parse) {
            is ParseState.Ready -> ResultScreen(vm, st)
            else -> LandingScreen(vm, st)
        }
    }
}

@Composable
private fun LandingScreen(vm: MainViewModel, st: ParseState) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.SmartDisplay, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onBackground)
            Text("GUI", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = vm.url, onValueChange = { vm.url = it },
            placeholder = { Text(stringResource(R.string.home_hint)) },
            singleLine = true, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.parseUrl() }),
            trailingIcon = {
                if (vm.url.isNotEmpty()) IconButton(onClick = { vm.url = ""; vm.reset() }) { Icon(Icons.Rounded.Close, null) }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { vm.url = it.trim() }
            }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.home_paste)) }
            Button(onClick = { vm.parseUrl() }, enabled = st !is ParseState.Loading) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.home_parse))
            }
        }
        Spacer(Modifier.height(20.dp))
        when (st) {
            is ParseState.Loading -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_parsing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ParseState.Error -> Text(
                if (st.message == "invalid_url") stringResource(R.string.error_no_url) else st.message,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
            else -> Text(
                stringResource(R.string.home_supports), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(vm: MainViewModel, st: ParseState.Ready) {
    val info = st.info
    val sel = vm.selection
    var showExtra by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- video card ----
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (info.thumbnail.isNotEmpty())
                                AsyncImage(info.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (info.duration > 0)
                                Text(formatDuration(info.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                    .background(androidx.compose.ui.graphics.Color(0xAA000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(info.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            if (info.uploader.isNotEmpty())
                                Text(info.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.reset() }) { Icon(Icons.Rounded.Close, null) }
                    }
                    if (st.singleFromPlaylist) {
                        Text(stringResource(R.string.home_single_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.parseUrl(forcePlaylist = true) }) { Text(stringResource(R.string.home_load_playlist)) }
                    }
                }
            }

            // ---- mode + quality ----
            item {
                SectionCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf("default" to R.string.mode_default, "video" to R.string.mode_video, "audio" to R.string.mode_audio)
                        modes.forEachIndexed { i, (key, res) ->
                            SegmentedButton(
                                selected = sel.mode == key,
                                onClick = { vm.select { it.copy(mode = key, preset = null, videoId = null, audioId = null) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(stringResource(res), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (sel.mode == "default" && !info.isPlaylist && !sel.manual) {
                        Text(stringResource(R.string.quality), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArgsBuilder.presets.forEach { (name, selector) ->
                                FilterChip(
                                    selected = sel.preset == selector,
                                    onClick = { vm.select { it.copy(preset = selector) } },
                                    label = { Text(if (selector == null) stringResource(R.string.quality_best) else name) },
                                )
                            }
                        }
                    }
                    if (sel.mode == "audio" && !sel.manual) {
                        Text(stringResource(R.string.opt_audio_format), style = MaterialTheme.typography.labelMedium)
                        ChoiceChips(listOf("mp3", "m4a", "opus", "flac", "wav"), sel.extra.audioConvertFormat,
                            { f -> vm.select { it.copy(extra = it.extra.copy(audioConvertFormat = f)) } }, offLabel = stringResource(R.string.auto))
                    }
                    if (!info.isPlaylist) {
                        SwitchRow(stringResource(R.string.advanced_formats), sel.manual) { on -> vm.select { it.copy(manual = on) } }
                    }
                }
            }

            // ---- manual formats ----
            if (sel.manual && !info.isPlaylist) {
                if (sel.mode != "audio") item {
                    SectionCard(stringResource(R.string.video_stream)) {
                        FormatList(info.videoFormats, sel.videoId, stringResource(R.string.auto)) { id -> vm.select { it.copy(videoId = id) } }
                    }
                }
                if (sel.mode != "video") item {
                    SectionCard(stringResource(R.string.audio_stream)) {
                        FormatList(info.audioFormats, sel.audioId, stringResource(R.string.auto)) { id -> vm.select { it.copy(audioId = id) } }
                    }
                }
            }

            // ---- playlist ----
            if (info.isPlaylist) {
                item {
                    SectionCard("${stringResource(R.string.playlist_items)} (${sel.playlist.size}/${info.entries.size})") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { vm.select { it.copy(playlist = info.entries.map { e -> e.index }.toSet()) } }, label = { Text(stringResource(R.string.select_all)) })
                            AssistChip(onClick = { vm.select { it.copy(playlist = emptySet()) } }, label = { Text(stringResource(R.string.select_none)) })
                        }
                    }
                }
                items(info.entries, key = { it.index }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            vm.select { s -> s.copy(playlist = if (e.index in s.playlist) s.playlist - e.index else s.playlist + e.index) }
                        }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = e.index in sel.playlist, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("${e.index}. ${e.title}", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (e.duration > 0) Text(formatDuration(e.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- subtitles ----
            if (info.subtitles.isNotEmpty()) item {
                SectionCard(stringResource(R.string.subtitles)) {
                    val manual = info.subtitles.filter { !it.auto }
                    val auto = info.subtitles.filter { it.auto }.take(60)
                    if (manual.isNotEmpty()) {
                        Text(stringResource(R.string.subs_manual), style = MaterialTheme.typography.labelMedium)
                        SubChips(manual, sel.subs) { k -> vm.select { it.copy(subs = it.subs.toggle(k)) } }
                    }
                    if (auto.isNotEmpty()) {
                        Text(stringResource(R.string.subs_auto), style = MaterialTheme.typography.labelMedium)
                        SubChips(auto, sel.subs) { k -> vm.select { it.copy(subs = it.subs.toggle(k)) } }
                    }
                }
            }

            // ---- trim / live ----
            if (!info.isPlaylist) item {
                SectionCard {
                    SwitchRow(stringResource(R.string.trim), sel.trimOn) { on -> vm.select { it.copy(trimOn = on) } }
                    if (sel.trimOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LabeledField(stringResource(R.string.trim_start), sel.start) { v -> vm.select { it.copy(start = v) } } }
                        Box(Modifier.weight(1f)) { LabeledField(stringResource(R.string.trim_end), sel.end) { v -> vm.select { it.copy(end = v) } } }
                    }
                    if (info.isLive) SwitchRow(stringResource(R.string.live_from_start), sel.liveFromStart) { on -> vm.select { it.copy(liveFromStart = on) } }
                }
            }

            // ---- extra options ----
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth().clickable { showExtra = !showExtra }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.extra_options), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Icon(if (showExtra) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (showExtra) ExtraOptionsEditor(sel.extra, { e -> vm.select { it.copy(extra = e) } }, full = true)
                }
            }
        }

        // sticky download button
        Button(
            onClick = { vm.startDownload(); vm.url = ""; vm.reset(); vm.tab = 1 },
            enabled = !info.isPlaylist || sel.playlist.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.download))
        }
    }
}

private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubChips(list: List<SubtitleOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { s -> FilterChip(selected = s.key in selected, onClick = { onToggle(s.key) }, label = { Text(s.lang) }) }
    }
}

@Composable
private fun FormatList(formats: List<FormatItem>, selected: String?, autoLabel: String, onSelect: (String?) -> Unit) {
    Column {
        FormatRow(autoLabel, selected == null) { onSelect(null) }
        formats.forEach { f -> FormatRow(f.label(), selected == f.id) { onSelect(f.id) } }
    }
}

@Composable
private fun FormatRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
