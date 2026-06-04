package com.cameronamer.telegramdrive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.telegram_drive.listFiles

data class DriveFile(val id: Long, val name: String, val size: Long, val isFolder: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(onOpenSettings: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        isLoading = true
        errorMessage = null
        try {
            val raw = withContext(Dispatchers.IO) { listFiles(null) }
            // The Rust core currently returns Vec<String> as a placeholder; in the
            // meantime, only populate from it once it carries real entries.
            files = if (raw.isEmpty()) emptyList() else raw.mapIndexed { idx, name ->
                DriveFile(idx.toLong(), name, 0L, false)
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: e::class.java.simpleName
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Drive") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                coroutineScope.launch { refresh() }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Refresh files")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                files.isEmpty() -> {
                    EmptyState()
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files) { file ->
                            FileItem(file = file, onDownloadClick = {
                                // Download via the core's existing path will be
                                // added once listFiles returns real metadata.
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No files yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Files you upload will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FileItem(file: DriveFile, onDownloadClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { /* No-op until folder drill-down is implemented */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                if (file.size > 0) {
                    Text(
                        text = "${file.size / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onDownloadClick) {
                Text("Download")
            }
        }
    }
}