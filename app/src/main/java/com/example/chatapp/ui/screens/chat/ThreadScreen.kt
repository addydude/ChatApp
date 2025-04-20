package com.example.chatapp.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(
    threadId: String,
    onNavigateToProfile: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel
) {
    val currentUserId by viewModel.currentUserId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val chatUsers by viewModel.chatUsers.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val isEncrypted by viewModel.isEncrypted.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Load thread data
    LaunchedEffect(threadId) {
        viewModel.loadThread(threadId)
    }
    
    // Find parent message
    val parentMessage = remember(messages) {
        messages.firstOrNull()
    }
    
    // Get sender of parent message
    val parentSender = remember(parentMessage, chatUsers) {
        parentMessage?.let { chatUsers[it.senderId] }
    }
    
    Scaffold(
        topBar = {
            ThreadTopBar(
                title = parentSender?.username ?: "Thread",
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            ThreadInputBar(
                messageText = messageText,
                onMessageTextChange = viewModel::setMessageText,
                onSendClick = viewModel::sendMessage,
                isEncrypted = isEncrypted,
                onToggleEncryption = viewModel::toggleEncryption
            )
        }
    ) { paddingValues -> 
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                EmptyThread()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Parent message
                    parentMessage?.let { message ->
                        ThreadParentMessage(
                            message = message,
                            sender = parentSender,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Thread replies
                    val threadReplies = messages.drop(1) // Skip parent message
                    
                    if (threadReplies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No replies yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = threadReplies,
                                key = { it.id }
                            ) { message ->
                                val sender = chatUsers[message.senderId]
                                
                                MessageItem(
                                    message = message,
                                    sender = sender,
                                    isFromCurrentUser = message.senderId == currentUserId,
                                    onReactionClick = { viewModel.addReaction(message.id, "👍") },
                                    onReactionLongClick = { /* Show emoji picker */ },
                                    onReplyClick = { /* Threads don't support nested replies */ },
                                    onThreadClick = { /* Threads don't support nested threads */ },
                                    onPinClick = { /* Pin functionality */ },
                                    onDeleteClick = { viewModel.deleteMessage(message.id) },
                                    onEditClick = { /* Show edit dialog */ },
                                    onUserClick = { sender?.let { onNavigateToProfile(it.id) } },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Thread",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = { /* Open menu */ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
        }
    )
}

@Composable
fun ThreadParentMessage(
    message: Message,
    sender: User?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Sender info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sender?.username ?: "Unknown User",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                // Time (could be formatted)
                Text(
                    text = "Original message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Message content
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ThreadInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isEncrypted: Boolean,
    onToggleEncryption: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Message text field
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = { Text("Reply to thread") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                maxLines = 5,
                trailingIcon = {
                    IconButton(onClick = onToggleEncryption) {
                        Icon(
                            imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isEncrypted) "Encrypted" else "Not Encrypted",
                            tint = if (isEncrypted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send button
            IconButton(
                onClick = onSendClick,
                enabled = messageText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyThread() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Thread Not Found",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This thread may have been deleted or is unavailable",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}