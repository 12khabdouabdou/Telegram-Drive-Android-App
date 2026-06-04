package com.cameronamer.telegramdrive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DriveFile(val id: Long, val name: String, val size: Long, val isFolder: Boolean)

@Composable
fun FileListScreen() {
    // Mock data replacing the old TouchFileList.tsx
    var files by remember {
        mutableStateOf(
            listOf(
                DriveFile(1, "Photos", 0, true),
                DriveFile(2, "Documents", 0, true),
                DriveFile(3, "video.mp4", 1024 * 1024 * 50, false),
                DriveFile(4, "report.pdf", 1024 * 500, false)
            )
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                // TODO: Trigger native Android ACTION_OPEN_DOCUMENT file picker -> upload
            }) {
                Icon(Icons.Default.Add, contentDescription = "Upload File")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Text(
                text = "My Drive",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(files) { file ->
                    FileItem(file = file, onDownloadClick = {
                        // TODO: Call UniFFI telegram_drive.download_file() via ForegroundService
                    })
                }
            }
        }
    }
}

@Composable
fun FileItem(file: DriveFile, onDownloadClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                if (file.isFolder) {
                    // TODO: Re-fetch folder contents via UniFFI
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                if (!file.isFolder) {
                    Text(
                        text = "${file.size / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!file.isFolder) {
                Button(onClick = onDownloadClick) {
                    Text("Download")
                }
            }
        }
    }
}