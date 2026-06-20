package com.telegramdrive.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.FolderEntity
import com.telegramdrive.app.ui.theme.TelegramDriveTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Lightweight activity that handles ACTION_SEND / ACTION_SEND_MULTIPLE intents
 * from the system share sheet. Lets the user pick a target folder and enqueue
 * uploads without leaving the share flow.
 */
@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = collectSharedUris(intent)
        setContent {
            TelegramDriveTheme {
                ShareTargetScreen(uris = uris, onClose = { finish() })
            }
        }
    }

    private fun collectSharedUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return emptyList()
                listOf(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            }
            else -> emptyList()
        }
    }
}

@Composable
private fun ShareTargetScreen(
    uris: List<Uri>,
    onClose: () -> Unit,
    vm: ShareTargetViewModel = hiltViewModel()
) {
    val folders by vm.folders.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_to_drive)) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("${uris.size} item(s) selected", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.share_choose_folder), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Root") },
                        modifier = Modifier.clickable { vm.uploadTo(null, uris, onClose) }
                    )
                    HorizontalDivider()
                }
                items(folders) { folder ->
                    FolderPickerRow(folder, onClick = { vm.uploadTo(folder.id, uris, onClose) })
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(folder: FolderEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
