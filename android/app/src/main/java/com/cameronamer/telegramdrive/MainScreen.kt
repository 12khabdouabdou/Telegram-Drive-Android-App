package com.cameronamer.telegramdrive

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Files : BottomNavItem("files", "Files", Icons.Default.Folder)
    object Uploads : BottomNavItem("uploads", "Uploads", Icons.Default.Upload)
    object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(onLoggedOut: () -> Unit) {
    var selectedItem by rememberSaveable { mutableStateOf(0) }
    val items = listOf(BottomNavItem.Files, BottomNavItem.Uploads, BottomNavItem.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedItem) {
            0 -> FileListScreen(modifier = Modifier.padding(paddingValues))
            1 -> UploadsScreen(modifier = Modifier.padding(paddingValues))
            2 -> SettingsScreen(
                onBack = {},
                onLoggedOut = onLoggedOut
            )
        }
    }
}
