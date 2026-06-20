package com.telegramdrive.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.telegramdrive.app.ui.backup.BackupScreen
import com.telegramdrive.app.ui.chat.ChatListScreen
import com.telegramdrive.app.ui.files.FilesScreen
import com.telegramdrive.app.ui.navigation.Screen
import com.telegramdrive.app.ui.navigation.bottomNavScreens
import com.telegramdrive.app.ui.onboarding.OnboardingFlow
import com.telegramdrive.app.ui.photos.PhotosScreen
import com.telegramdrive.app.ui.settings.SettingsScreen
import com.telegramdrive.app.ui.vault.VaultScreen

@Composable
fun AppRoot() {
    val vm: AppRootViewModel = hiltViewModel()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle(initialValue = true)
    val hasAccount by vm.hasAccount.collectAsStateWithLifecycle(initialValue = true)
    val hasCredentials by vm.hasCredentials.collectAsStateWithLifecycle(initialValue = true)

    when {
        // First launch — full onboarding flow
        !onboardingDone -> OnboardingFlow(onFinished = vm::markOnboardingDone)
        // Returning user but credentials were cleared (or never set) — force credentials step then login
        !hasCredentials -> OnboardingFlow(forceCredentials = true, onFinished = vm::markOnboardingDone)
        // Credentials present but no account signed in — just login
        !hasAccount -> OnboardingFlow(forceLogin = true, onFinished = vm::markOnboardingDone)
        // All set — show the main app
        else -> MainApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Screen.Files.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentScreenTitle(currentRoute))) },
                actions = {
                    IconButton(onClick = { navController.navigate("accounts") }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Accounts")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.labelResId)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Files.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Files.route) { FilesScreen() }
            composable(Screen.Photos.route) { PhotosScreen() }
            composable(Screen.Backup.route) { BackupScreen() }
            composable(Screen.Vault.route) { VaultScreen() }
            composable(Screen.Chat.route) { ChatListScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

private fun currentScreenTitle(route: String): Int = when (route) {
    Screen.Files.route -> com.telegramdrive.app.R.string.nav_files
    Screen.Photos.route -> com.telegramdrive.app.R.string.nav_photos
    Screen.Backup.route -> com.telegramdrive.app.R.string.nav_backup
    Screen.Vault.route -> com.telegramdrive.app.R.string.nav_vault
    Screen.Chat.route -> com.telegramdrive.app.R.string.nav_chat
    Screen.Settings.route -> com.telegramdrive.app.R.string.nav_settings
    else -> com.telegramdrive.app.R.string.app_name
}
