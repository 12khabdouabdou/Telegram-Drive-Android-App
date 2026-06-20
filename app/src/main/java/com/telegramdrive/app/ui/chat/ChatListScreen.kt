package com.telegramdrive.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegramdrive.app.R
import com.telegramdrive.app.data.local.entity.ChatEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: ChatViewModel = hiltViewModel()) {
    val chats by vm.chats.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = "",
            onValueChange = { /* TODO: search */ },
            placeholder = { Text(stringResource(R.string.chat_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(chats, key = { it.id }) { chat -> ChatRow(chat) }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatEntity) {
    ListItem(
        headlineContent = { Text(chat.title) },
        supportingContent = chat.lastMessageText?.let { { Text(it, maxLines = 1) } },
        trailingContent = {
            if (chat.unreadCount > 0) {
                Badge { Text(chat.unreadCount.toString()) }
            }
        },
        leadingContent = {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(chat.title.take(1).uppercase())
                }
            }
        }
    )
    HorizontalDivider()
}
