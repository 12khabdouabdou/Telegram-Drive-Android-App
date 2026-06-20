package com.telegramdrive.app.ui.photos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.FileEntity

@Composable
fun PhotosScreen(vm: PhotosViewModel = hiltViewModel()) {
    val photos by vm.photos.collectAsStateWithLifecycle(initialValue = emptyList())

    if (photos.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.photos_empty), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(2.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = photo.localThumbnailPath ?: photo.localPath,
                    contentDescription = photo.name,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(1.dp)
                )
            }
        }
    }
}
