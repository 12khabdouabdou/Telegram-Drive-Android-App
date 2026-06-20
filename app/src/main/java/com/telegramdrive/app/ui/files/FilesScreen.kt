package com.telegramdrive.app.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.local.entity.FolderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(vm: FilesViewModel = hiltViewModel()) {
    val folders by vm.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val files by vm.files.collectAsStateWithLifecycle(initialValue = emptyList())
    val showCreateFolder by vm.showCreateFolderDialog.collectAsStateWithLifecycle()
    var showUploadMenu by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showUploadMenu = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_upload)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (folders.isEmpty() && files.isEmpty()) {
                EmptyFilesState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder -> FolderRow(folder, onClick = { vm.openFolder(folder.id) }) }
                    items(files) { file -> FileRow(file, onClick = { vm.openFile(file) }) }
                }
            }
        }
    }

    if (showUploadMenu) {
        ModalBottomSheet(onDismissRequest = { showUploadMenu = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_upload)) },
                    leadingContent = { Icon(Icons.Filled.UploadFile, null) },
                    modifier = Modifier.clickable { showUploadMenu = false; vm.pickFiles() }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.files_new_folder)) },
                    leadingContent = { Icon(Icons.Filled.CreateNewFolder, null) },
                    modifier = Modifier.clickable { showUploadMenu = false; vm.showCreateFolder() }
                )
            }
        }
    }

    if (showCreateFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.hideCreateFolder() },
            title = { Text(stringResource(R.string.files_new_folder)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.createFolder(name); vm.hideCreateFolder() }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.hideCreateFolder() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EmptyFilesState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.files_empty), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.files_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name) },
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun FileRow(file: FileEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { Text(humanReadableSize(file.sizeBytes)) },
        leadingContent = { Icon(iconForFile(file), contentDescription = null) },
        trailingContent = {
            if (file.encrypted) Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

private fun iconForFile(file: FileEntity) = when {
    file.mimeType.startsWith("image/") -> Icons.Filled.Image
    file.mimeType.startsWith("video/") -> Icons.Filled.VideoFile
    file.mimeType.startsWith("audio/") -> Icons.Filled.AudioFile
    file.mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.InsertDriveFile
}

private fun humanReadableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = bytes.toDouble() / 1024
    var i = 0
    while (size >= 1024 && i < units.lastIndex) { size /= 1024; i++ }
    return "%.1f %s".format(size, units[i])
}
