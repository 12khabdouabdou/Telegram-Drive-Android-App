package com.cameronamer.telegramdrive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Drive") },
                actions = {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearch = { isSearchActive = false },
                        active = isSearchActive,
                        onActiveChange = { isSearchActive = it },
                        placeholder = { Text("Search files...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    ) {}
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: Open file picker */ }) {
                Icon(Icons.Default.Add, contentDescription = "Upload")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is FilesViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is FilesViewModel.UiState.Error -> {
                    val message = (uiState as FilesViewModel.UiState.Error).message
                    ErrorState(message = message, onRetry = { viewModel.loadFiles() })
                }
                is FilesViewModel.UiState.Success -> {
                    val files = (uiState as FilesViewModel.UiState.Success).files
                    if (files.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files) { file ->
                                Text(file, modifier = Modifier.padding(16.dp))
                            }
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("No files yet", style = MaterialTheme.typography.titleMedium)
        Text("Upload your first file to get started", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Error", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
