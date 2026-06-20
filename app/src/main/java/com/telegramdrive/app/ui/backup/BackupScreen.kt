package com.telegramdrive.app.ui.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.BackupRuleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabled by vm.autoBackupEnabled.collectAsStateWithLifecycle(initialValue = false)
    val stats by vm.storageStats.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.createDefaultRule() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.backup_rule_new)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master toggle card
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.backup_status_idle), style = MaterialTheme.typography.titleMedium)
                                Text(if (enabled) "On" else stringResource(R.string.backup_status_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = enabled, onCheckedChange = vm::setAutoBackup)
                        }
                    }
                }
            }
            // Storage insights
            item {
                stats?.let {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (it.totalSize.toFloat() / (50L * 1024 * 1024 * 1024)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${humanSize(it.totalSize)} · ${it.fileCount} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Rules
            item {
                Text(stringResource(R.string.backup_rules_title), style = MaterialTheme.typography.titleMedium)
            }
            items(rules, key = { it.id }) { rule -> RuleRow(rule, onToggle = { vm.toggleRule(rule.id, !rule.enabled) }) }
        }
    }
}

@Composable
private fun RuleRow(rule: BackupRuleEntity, onToggle: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                    Text("${rule.includedFolders.size} folders · ${rule.mimeTypes.size} types",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule.requireWifi) AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.backup_condition_wifi)) },
                    leadingIcon = { Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(16.dp)) }
                )
                if (rule.requireCharging) AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.backup_condition_charging)) },
                    leadingIcon = { Icon(Icons.Filled.BatteryFull, null, modifier = Modifier.size(16.dp)) }
                )
                if (rule.dedupEnabled) AssistChip(onClick = {}, label = { Text(stringResource(R.string.backup_dedup)) })
                if (rule.onlyOriginalQuality) AssistChip(onClick = {}, label = { Text(stringResource(R.string.backup_original_quality)) })
            }
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = bytes.toDouble() / 1024
    var i = 0
    while (size >= 1024 && i < units.lastIndex) { size /= 1024; i++ }
    return "%.1f %s".format(size, units[i])
}
