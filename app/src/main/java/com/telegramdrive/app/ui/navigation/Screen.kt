package com.telegramdrive.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelResId: Int, val icon: ImageVector) {
    object Files : Screen("files", com.telegramdrive.app.R.string.nav_files, Icons.Filled.Folder)
    object Photos : Screen("photos", com.telegramdrive.app.R.string.nav_photos, Icons.Filled.PhotoLibrary)
    object Backup : Screen("backup", com.telegramdrive.app.R.string.nav_backup, Icons.Filled.Backup)
    object Vault : Screen("vault", com.telegramdrive.app.R.string.nav_vault, Icons.Filled.Lock)
    object Chat : Screen("chat", com.telegramdrive.app.R.string.nav_chat, Icons.Filled.Chat)
    object Settings : Screen("settings", com.telegramdrive.app.R.string.nav_settings, Icons.Filled.Settings)
}

val bottomNavScreens = listOf(
    Screen.Files, Screen.Photos, Screen.Backup, Screen.Vault, Screen.Chat, Screen.Settings
)
