package com.ydl.gui.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ydl.gui.R
import com.ydl.gui.core.AppUpdater
import kotlinx.coroutines.launch

private sealed interface UpdState {
    data object Idle : UpdState
    data object Checking : UpdState
    data object UpToDate : UpdState
    data object NeedPermission : UpdState
    data class Downloading(val percent: Int) : UpdState
    data class Failed(val message: String) : UpdState
}

/** "Check for updates" card for the Settings screen. */
@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdState>(UpdState.Idle) }
    var info by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    val current = remember { AppUpdater.currentVersion(ctx) }
    val busy = state is UpdState.Checking || state is UpdState.Downloading

    fun errorText(e: Throwable): String = when (e) {
        is AppUpdater.NoApkException -> ctx.getString(R.string.update_no_apk)
        is AppUpdater.NotConfiguredException -> ctx.getString(R.string.update_no_repo)
        else -> e.message ?: e.javaClass.simpleName
    }

    SectionCard(stringResource(R.string.update_title)) {
        Text(stringResource(R.string.update_current, current), style = MaterialTheme.typography.bodyMedium)

        when (val st = state) {
            UpdState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodySmall)
            }
            UpdState.UpToDate -> Text(stringResource(R.string.update_latest), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            UpdState.NeedPermission -> Text(stringResource(R.string.update_permission), style = MaterialTheme.typography.bodySmall)
            is UpdState.Downloading -> {
                LinearProgressIndicator(progress = { st.percent / 100f }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_downloading, st.percent), style = MaterialTheme.typography.bodySmall)
            }
            is UpdState.Failed -> Text(
                stringResource(R.string.update_failed, st.message),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
            UpdState.Idle -> {}
        }

        info?.let { i ->
            Text(stringResource(R.string.update_available, i.version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (i.notes.isNotBlank())
                Text(i.notes.trim().take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        if (!AppUpdater.canInstall(ctx)) {
                            state = UpdState.NeedPermission
                            AppUpdater.openInstallPermissionSettings(ctx)
                            return@launch
                        }
                        state = UpdState.Downloading(0)
                        try {
                            val apk = AppUpdater.download(ctx, i) { p -> state = UpdState.Downloading(p) }
                            state = UpdState.Idle
                            AppUpdater.install(ctx, apk)
                        } catch (e: Throwable) {
                            state = UpdState.Failed(errorText(e))
                        }
                    }
                },
            ) { Text(stringResource(R.string.update_download)) }
        }

        FilledTonalButton(
            enabled = !busy,
            onClick = {
                state = UpdState.Checking; info = null
                scope.launch {
                    try {
                        val r = AppUpdater.check(ctx)
                        if (r == null) state = UpdState.UpToDate else { info = r; state = UpdState.Idle }
                    } catch (e: Throwable) {
                        state = UpdState.Failed(errorText(e))
                    }
                }
            },
        ) { Text(stringResource(R.string.update_check)) }
    }
}
