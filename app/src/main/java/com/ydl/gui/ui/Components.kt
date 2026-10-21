package com.ydl.gui.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Row of chips; "" (first) means "off / default". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit, offLabel: String = "—") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text(offLabel) })
        options.forEach { o ->
            FilterChip(selected = selected == o, onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}

@Composable
fun LabeledField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = singleLine, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
    )
}

/** All the yt-dlp / ffmpeg post-processing toggles (shared by result screen and settings). */
@Composable
fun ExtraOptionsEditor(
    e: com.ydl.gui.core.ExtraOptions,
    onChange: (com.ydl.gui.core.ExtraOptions) -> Unit,
    full: Boolean,
) {
    val r = com.ydl.gui.R.string
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SwitchRow(stringResource(r.opt_embed_metadata), e.embedMetadata) { onChange(e.copy(embedMetadata = it)) }
        SwitchRow(stringResource(r.opt_embed_thumbnail), e.embedThumbnail) { onChange(e.copy(embedThumbnail = it)) }
        SwitchRow(stringResource(r.opt_embed_subs), e.embedSubs) { onChange(e.copy(embedSubs = it)) }
        SwitchRow(stringResource(r.opt_embed_chapters), e.embedChapters) { onChange(e.copy(embedChapters = it)) }
        SwitchRow(stringResource(r.opt_sponsorblock), e.sponsorblockRemove) { onChange(e.copy(sponsorblockRemove = it)) }
        SwitchRow(stringResource(r.opt_write_thumbnail), e.writeThumbnail) { onChange(e.copy(writeThumbnail = it)) }
        SwitchRow(stringResource(r.opt_write_description), e.writeDescription) { onChange(e.copy(writeDescription = it)) }
        if (full) {
            SwitchRow(stringResource(r.opt_extract_audio), e.extractAudio) { onChange(e.copy(extractAudio = it)) }
            if (e.extractAudio) {
                Text(stringResource(r.opt_audio_format), style = MaterialTheme.typography.labelMedium)
                ChoiceChips(listOf("mp3", "m4a", "opus", "aac", "flac", "wav", "vorbis"), e.audioConvertFormat,
                    { onChange(e.copy(audioConvertFormat = it)) }, offLabel = stringResource(r.auto))
            }
            SwitchRow(stringResource(r.opt_no_merge), e.noMerge) { onChange(e.copy(noMerge = it)) }
            Text(stringResource(r.opt_recode), style = MaterialTheme.typography.labelMedium)
            ChoiceChips(listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "gif"), e.recodeFormat) { onChange(e.copy(recodeFormat = it)) }
            Text(stringResource(r.opt_remux), style = MaterialTheme.typography.labelMedium)
            ChoiceChips(listOf("mp4", "mkv", "webm", "mov", "avi"), e.remuxFormat) { onChange(e.copy(remuxFormat = it)) }
            LabeledField(stringResource(r.opt_limit_rate), e.limitRate) { onChange(e.copy(limitRate = it)) }
            LabeledField(stringResource(r.opt_ffmpeg_args), e.ffmpegArgs) { onChange(e.copy(ffmpegArgs = it)) }
            LabeledField(stringResource(r.opt_custom_args), e.customArgs, singleLine = false) { onChange(e.copy(customArgs = it)) }
        }
    }
}
