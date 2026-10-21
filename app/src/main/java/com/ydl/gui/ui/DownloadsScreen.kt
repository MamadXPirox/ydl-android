package com.ydl.gui.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ydl.gui.R
import com.ydl.gui.core.DownloadTask
import com.ydl.gui.core.TaskStatus

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val tasks by vm.downloads.tasks.collectAsState()
    val visible = tasks.filter { it.params.rawCommand == null }
    var logTask by remember { mutableStateOf<DownloadTask?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.downloads.clearFinished() }) { Text(stringResource(R.string.clear_finished)) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.id }) { t -> TaskCard(t, vm, onLogs = { logTask = t }) }
            }
        }
    }
    logTask?.let { t ->
        // show live logs for the selected task
        val live = tasks.firstOrNull { it.id == t.id } ?: t
        LogDialog(live) { logTask = null }
    }
}

@Composable
fun statusText(s: TaskStatus) = stringResource(
    when (s) {
        TaskStatus.QUEUED -> R.string.status_queued
        TaskStatus.PREPARING -> R.string.status_preparing
        TaskStatus.DOWNLOADING -> R.string.status_downloading
        TaskStatus.POSTPROCESSING -> R.string.status_post
        TaskStatus.COMPLETED -> R.string.status_done
        TaskStatus.ERROR -> R.string.status_error
        TaskStatus.CANCELLED -> R.string.status_cancelled
    }
)

@Composable
fun TaskCard(t: DownloadTask, vm: MainViewModel, onLogs: () -> Unit) {
    val ctx = LocalContext.current
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (t.thumbnail.isNotEmpty()) {
                AsyncImage(
                    t.thumbnail, null,
                    Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            }
        }
        // status line
        val color = when (t.status) {
            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
            TaskStatus.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            buildString {
                append(t.label.takeIf { it.isNotEmpty() }?.let { "$it · " } ?: "")
                if (t.status == TaskStatus.DOWNLOADING) {
                    append("%.0f%%".format(t.percent))
                    if (t.speed.isNotEmpty()) append(" · ").append(t.speed)
                    if (t.eta.isNotEmpty()) append(" · ETA ").append(t.eta)
                }
            }.trimEnd(' ', '·'),
            style = MaterialTheme.typography.bodySmall, color = color,
        )
        Text(statusText(t.status), style = MaterialTheme.typography.labelMedium, color = color)
        if (t.status == TaskStatus.DOWNLOADING) LinearProgressIndicator(progress = { t.percent / 100f }, Modifier.fillMaxWidth())
        if (t.status == TaskStatus.PREPARING || t.status == TaskStatus.POSTPROCESSING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (t.status == TaskStatus.ERROR && t.error.isNotEmpty())
            Text(t.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (t.status == TaskStatus.COMPLETED && t.outputName.isNotEmpty())
            Text(t.outputName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            when (t.status) {
                TaskStatus.COMPLETED -> if (t.outputUri.isNotEmpty()) {
                    val uri = Uri.parse(t.outputUri)
                    val mime = ctx.contentResolver.getType(uri) ?: "*/*"
                    IconButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.open)) }
                    IconButton(onClick = {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                }
                TaskStatus.ERROR, TaskStatus.CANCELLED ->
                    IconButton(onClick = { vm.downloads.retry(t.id) }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.retry)) }
                else ->
                    IconButton(onClick = { vm.downloads.cancel(t.id) }) { Icon(Icons.Rounded.Stop, stringResource(R.string.cancel)) }
            }
            IconButton(onClick = onLogs) { Icon(Icons.Rounded.Terminal, stringResource(R.string.logs)) }
            Spacer(Modifier.weight(1f))
            if (!t.isActive) IconButton(onClick = { vm.downloads.remove(t.id) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove)) }
        }
    }
}

@Composable
fun LogDialog(t: DownloadTask, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.logs)) },
        text = {
            Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(t.logs.joinToString("\n").ifEmpty { "—" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        },
    )
}
