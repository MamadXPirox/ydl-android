package com.ydl.gui.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydl.gui.R
import com.ydl.gui.core.TaskStatus

/** Run arbitrary yt-dlp commands (ffmpeg is bundled, so every post-processor works). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(vm: MainViewModel) {
    var cmd by remember { mutableStateOf("") }
    val tasks by vm.downloads.tasks.collectAsState()
    val last = tasks.firstOrNull { it.params.rawCommand != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.tools_title)) {
            Text(stringResource(R.string.tools_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = cmd, onValueChange = { cmd = it },
                placeholder = { Text(stringResource(R.string.tools_hint)) },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, shape = RoundedCornerShape(14.dp),
            )
            Text(stringResource(R.string.tools_presets), style = MaterialTheme.typography.labelMedium)
            val presets = listOf(
                R.string.preset_formats to "-F ",
                R.string.preset_subs to "--skip-download --write-subs --write-auto-subs --sub-langs all ",
                R.string.preset_thumb to "--skip-download --write-thumbnail ",
                R.string.preset_info to "--skip-download --write-info-json ",
                R.string.preset_comments to "--skip-download --write-comments --write-info-json ",
                R.string.preset_livechat to "--skip-download --write-subs --sub-langs live_chat ",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, text) ->
                    AssistChip(onClick = { cmd = text + cmd.removePrefix(text) }, label = { Text(stringResource(label)) })
                }
            }
            Button(onClick = { vm.runRaw(cmd) }, enabled = cmd.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.tools_run))
            }
        }

        if (last != null) {
            SectionCard(stringResource(R.string.logs) + " — " + statusText(last.status)) {
                if (last.status == TaskStatus.DOWNLOADING || last.status == TaskStatus.PREPARING || last.status == TaskStatus.POSTPROCESSING)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    last.logs.joinToString("\n").ifEmpty { "…" },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                )
                if (last.error.isNotEmpty()) Text(last.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (last.outputName.isNotEmpty()) Text(stringResource(R.string.saved_to) + "\n" + last.outputName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
