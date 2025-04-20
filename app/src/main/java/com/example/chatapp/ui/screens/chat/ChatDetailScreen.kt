package com.example.chatapp.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.MessageType
import com.example.chatapp.data.model.TypingStatus
import com.example.chatapp.data.model.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel
) {
    val currentUserId by viewModel.currentUserId.collectAsState()
    val chat by viewModel.chat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val chatUsers by viewModel.chatUsers.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    val isEncrypted by viewModel.isEncrypted.collectAsState()
    val pinnedMessages by viewModel.pinnedMessages.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Determine chat name
    val chatName = remember(chat, chatUsers) {
        when {
            chat == null -> "Chat"
            chat?.type == com.example.chatapp.data.model.ChatType.PRIVATE -> {
                // Find the other user in a private chat
                val otherUserId = chat?.participants?.find { it != currentUserId } ?: ""
                chatUsers[otherUserId]?.username ?: "Chat"
            }
            else -> "Group Chat" // This would come from the actual group name
        }
    }
    
    // Load chat data
    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Show edit message dialog if a message is being edited
    if (editingMessage != null) {
        EditMessageDialog(
            message = editingMessage!!,
            onDismiss = { editingMessage = null },
            onConfirm = { newContent ->
                viewModel.editMessage(editingMessage!!.id, newContent)
                editingMessage = null
            }
        )
    }
    
    Scaffold(
        topBar = {
            ChatTopBar(
                title = chatName,
                onBackClick = onNavigateBack,
                onProfileClick = {
                    // Navigate to profile of the other user in a private chat
                    val otherUserId = chat?.participants?.find { it != currentUserId } ?: return@ChatTopBar
                    onNavigateToProfile(otherUserId)
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                messageText = messageText,
                onMessageTextChange = viewModel::setMessageText,
                onSendClick = viewModel::sendMessage,
                replyToMessage = replyToMessage,
                onCancelReply = { viewModel.setReplyToMessage(null) },
                isEncrypted = isEncrypted,
                onToggleEncryption = viewModel::toggleEncryption,
                typingUsers = typingUsers,
                chatUsers = chatUsers
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pinned messages
            AnimatedVisibility(
                visible = pinnedMessages.isNotEmpty(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PinnedMessagesBar(
                    pinnedMessages = pinnedMessages,
                    onUnpinClick = viewModel::unpinMessage
                )
            }
            
            // Messages list
            if (messages.isEmpty()) {
                EmptyChat()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (pinnedMessages.isNotEmpty()) 64.dp else 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        val sender = chatUsers[message.senderId]
                        
                        MessageItem(
                            message = message,
                            sender = sender,
                            isFromCurrentUser = message.senderId == currentUserId,
                            onReactionClick = { viewModel.addReaction(message.id, "👍") },
                            onReactionLongClick = { /* Show emoji picker */ },
                            onReplyClick = { viewModel.setReplyToMessage(message) },
                            onThreadClick = {
                                message.threadId?.let { threadId ->
                                    onNavigateToThread(threadId)
                                } ?: run {
                                    coroutineScope.launch {
                                        viewModel.createThread(message.id).collect { threadId ->
                                            onNavigateToThread(threadId)
                                        }
                                    }
                                }
                            },
                            onPinClick = {
                                if (message.isPinned) {
                                    viewModel.unpinMessage(message.id)
                                } else {
                                    viewModel.pinMessage(message.id)
                                }
                            },
                            onDeleteClick = { viewModel.deleteMessage(message.id) },
                            onEditClick = { editingMessage = message },
                            onUserClick = { sender?.let { onNavigateToProfile(it.id) } },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.Person, contentDescription = "View Profile")
            }
            
            IconButton(onClick = { /* Open call options */ }) {
                Icon(Icons.Default.Call, contentDescription = "Call")
            }
            
            IconButton(onClick = { /* Open menu */ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
        }
    )
}

@Composable
fun PinnedMessagesBar(
    pinnedMessages: List<Message>,
    onUnpinClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pinnedMessages) { message ->
                PinnedMessageChip(
                    message = message,
                    onUnpinClick = { onUnpinClick(message.id) }
                )
            }
        }
    }
}

@Composable
fun PinnedMessageChip(
    message: Message,
    onUnpinClick: () -> Unit
) {
    Card(
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onUnpinClick,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Unpin",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun MessageInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    replyToMessage: Message?,
    onCancelReply: () -> Unit,
    isEncrypted: Boolean,
    onToggleEncryption: () -> Unit,
    typingUsers: List<TypingStatus>,
    chatUsers: Map<String, User>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Reply preview
        AnimatedVisibility(visible = replyToMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reply to ${chatUsers[replyToMessage?.senderId]?.username ?: "User"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = replyToMessage?.content ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(onClick = onCancelReply) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Reply"
                        )
                    }
                }
            }
        }
        
        // Typing indicator
        AnimatedVisibility(visible = typingUsers.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val typingText = remember(typingUsers) {
                    val names = typingUsers.mapNotNull { status ->
                        chatUsers[status.userId]?.username
                    }
                    
                    when {
                        names.isEmpty() -> ""
                        names.size == 1 -> "${names[0]} is typing..."
                        names.size == 2 -> "${names[0]} and ${names[1]} are typing..."
                        else -> "${names[0]}, ${names[1]}, and others are typing..."
                    }
                }
                
                Text(
                    text = typingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Message input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment button
                IconButton(onClick = { /* Show attachment options */ }) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File"
                    )
                }
                
                // Message text field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    placeholder = { Text("Type a message") },
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
}

@Composable
fun MessageItem(
    message: Message,
    sender: User?,
    isFromCurrentUser: Boolean,
    onReactionClick: () -> Unit,
    onReactionLongClick: () -> Unit,
    onReplyClick: () -> Unit,
    onThreadClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val messageTime = remember(message) { dateFormatter.format(message.timestamp.toDate()) }
    
    // Determine content color based on whether this message is encrypted
    val contentColor = if (message.isEncrypted) {
        MaterialTheme.colorScheme.tertiary
    } else {
        if (isFromCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        // Avatar for other users' messages
        if (!isFromCurrentUser) {
            Card(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Bottom)
                    .clickable { onUserClick() },
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sender?.username?.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // Message content
        Column(
            horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
        ) {
            // Username for group chats (not shown for 1-on-1 chats)
            if (!isFromCurrentUser) {
                Text(
                    text = sender?.username ?: "Unknown User",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }
            
            // Message bubble
            Surface(
                color = if (isFromCurrentUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (isFromCurrentUser) 16.dp else 4.dp,
                    topEnd = if (isFromCurrentUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Reply preview
                    if (message.replyToMessageId != null) {
                        // This would show the replied-to message
                        // For now, a simple indication
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Replied to a message",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    
                    // Message content
                    when (message.type) {
                        MessageType.TEXT -> {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                        }
                        MessageType.IMAGE -> {
                            // Image preview
                            Text("Image") // Placeholder
                        }
                        MessageType.FILE -> {
                            // File attachment
                            Text("File: ${message.fileName}") // Placeholder
                        }
                        else -> {
                            Text(message.content)
                        }
                    }
                    
                    // Message metadata
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edited indicator
                        if (message.isEdited) {
                            Text(
                                text = "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Encrypted indicator
                        if (message.isEncrypted) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        
                        // Time
                        Text(
                            text = messageTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Read receipt
                        if (isFromCurrentUser && message.readBy.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Reactions
            if (message.reactions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(message.reactions.entries.toList()) { (userId, emoji) ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Message actions
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // These icons could be in a context menu instead for a cleaner UI
                IconButton(
                    onClick = onReplyClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onReactionClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "React",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onThreadClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Thread",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Only show these for current user's messages
                if (isFromCurrentUser) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onPinClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (message.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                        contentDescription = if (message.isPinned) "Unpin" else "Pin",
                        tint = if (message.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChat() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Messages Yet",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Start the conversation by sending a message",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EditMessageDialog(
    message: Message,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var editedText by remember { mutableStateOf(message.content) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Message") },
        text = {
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Edit your message") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editedText) },
                enabled = editedText.isNotBlank() && editedText != message.content
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}