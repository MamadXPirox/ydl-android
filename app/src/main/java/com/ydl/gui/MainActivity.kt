package com.ydl.gui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ydl.gui.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by vm.settings.state.collectAsState()
            val tasks by vm.downloads.tasks.collectAsState()
            val activeCount = tasks.count { it.isActive }
            YdlTheme(settings.theme) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val items = listOf(
                                Triple(R.string.tab_home, Icons.Rounded.Home, 0),
                                Triple(R.string.tab_downloads, Icons.Rounded.Download, 1),
                                Triple(R.string.tab_tools, Icons.Rounded.Build, 2),
                                Triple(R.string.tab_settings, Icons.Rounded.Settings, 3),
                            )
                            items.forEach { (label, icon, idx) ->
                                NavigationBarItem(
                                    selected = vm.tab == idx,
                                    onClick = { vm.tab = idx },
                                    icon = {
                                        if (idx == 1 && activeCount > 0) BadgedBox(badge = { Badge { androidx.compose.material3.Text("$activeCount") } }) { Icon(icon, null) }
                                        else Icon(icon, null)
                                    },
                                    label = { androidx.compose.material3.Text(stringResource(label)) },
                                )
                            }
                        }
                    },
                ) { inner ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(inner)) {
                        when (vm.tab) {
                            0 -> HomeScreen(vm)
                            1 -> DownloadsScreen(vm)
                            2 -> ToolsScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Links shared from other apps (Share → YDL GUI) or opened via VIEW. */
    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        vm.url = url
        vm.tab = 0
        vm.parseUrl()
    }
}
