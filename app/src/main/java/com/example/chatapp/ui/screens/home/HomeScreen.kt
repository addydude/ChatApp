package com.example.chatapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.Chat
import com.example.chatapp.data.model.ChatType
import com.example.chatapp.data.model.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState(initial = emptyList())
    val currentUser by viewModel.currentUser.collectAsState()
    val chatUsers by viewModel.chatUsers.collectAsState(initial = emptyMap())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Fetch latest data
    LaunchedEffect(key1 = Unit) {
        viewModel.loadChats()
        viewModel.loadCurrentUser()
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                
                // User info in drawer
                currentUser?.let { user ->
                    UserProfileHeader(
                        user = user,
                        onProfileClick = { onNavigateToProfile(user.id) }
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                // Drawer items
                NavigationDrawerItem(
                    label = { Text("Chats") },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } }
                )
                
                NavigationDrawerItem(
                    label = { Text("Channels") },
                    icon = { Icon(Icons.Default.Forum, contentDescription = "Channels") },
                    selected = false,
                    onClick = { 
                        scope.launch { 
                            drawerState.close()
                            // For now, we'll just toggle search to find users to create group chats
                            viewModel.toggleSearch()
                        }
                    }
                )
                
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    selected = false,
                    onClick = { 
                        scope.launch { 
                            drawerState.close() 
                            // Navigate to current user's profile which contains settings
                            currentUser?.let { user ->
                                onNavigateToProfile(user.id)
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Logout
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Logout") },
                    selected = false,
                    onClick = onLogout
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    title = "ChatApp",
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    isSearchActive = isSearchActive,
                    onToggleSearch = viewModel::toggleSearch,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onCreateTestUsers = { viewModel.createTestUsers() }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { 
                        // Toggle search to start new chat
                        if (!isSearchActive) {
                            viewModel.toggleSearch()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isSearchActive) {
                    // Debug the search state
                    LaunchedEffect(searchQuery, searchResults) {
                        android.util.Log.d("HomeScreen", "Search active: $isSearchActive, Query: '$searchQuery', Results: ${searchResults.size}")
                    }
                    
                    // Always show either results or empty state when search is active
                    if (searchQuery.isNotEmpty()) {
                        if (searchResults.isEmpty()) {
                            EmptySearchResults()
                        } else {
                            UserSearchResults(
                                users = searchResults,
                                onUserClick = { user ->
                                    android.util.Log.d("HomeScreen", "User clicked: ${user.username}")
                                    viewModel.createOrOpenChat(user.id) { chatId ->
                                        onNavigateToChat(chatId)
                                    }
                                }
                            )
                        }
                    } else {
                        // Show a prompt when search is active but query is empty
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enter a username to search",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Show chat list
                    if (chats.isEmpty()) {
                        EmptyChats()
                    } else {
                        ChatsList(
                            chats = chats,
                            chatUsers = chatUsers,
                            currentUserId = currentUser?.id ?: "",
                            onChatClick = { chat ->
                                onNavigateToChat(chat.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileHeader(user: User, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User avatar
        Card(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.titleLarge
            )
            
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (user.isOnline) Icons.Default.Circle else Icons.Outlined.Circle,
                    contentDescription = if (user.isOnline) "Online" else "Offline",
                    tint = if (user.isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = if (user.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = "Edit Profile"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onMenuClick: () -> Unit,
    onCreateTestUsers: () -> Unit = {}
) {
    if (isSearchActive) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = { /* Search executed */ },
            active = true,
            onActiveChange = { if (!it) onToggleSearch() },
            placeholder = { Text("Search users...") },
            leadingIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search suggestions could go here
        }
    } else {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                // Add test users button
                IconButton(onClick = onCreateTestUsers) {
                    Icon(Icons.Default.Add, contentDescription = "Add Test Users")
                }
                
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        )
    }
}

@Composable
fun ChatsList(
    chats: List<Chat>,
    chatUsers: Map<String, User>,
    currentUserId: String,
    onChatClick: (Chat) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(chats) { chat ->
            ChatListItem(
                chat = chat,
                chatUsers = chatUsers,
                currentUserId = currentUserId,
                onClick = { onChatClick(chat) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListItem(
    chat: Chat,
    chatUsers: Map<String, User>,
    currentUserId: String,
    onClick: () -> Unit
) {
    val otherUser = if (chat.type == ChatType.PRIVATE) {
        val otherUserId = chat.participants.find { it != currentUserId } ?: ""
        chatUsers[otherUserId]
    } else null
    
    val chatName = when {
        otherUser != null -> otherUser.username
        chat.type == ChatType.GROUP -> "Group Chat" // This would come from the actual group name
        else -> "Chat"
    }
    
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val lastMessageTime = chat.lastMessageTimestamp.toDate()
    val formattedTime = formatter.format(lastMessageTime)
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Card(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chatName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessageText.ifEmpty { "No messages yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Unread count badge
                    val unreadCount = chat.unreadCount[currentUserId] ?: 0
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResults(
    users: List<User>,
    onUserClick: (User) -> Unit
) {
    // Add logging to see if this composable is being called correctly
    LaunchedEffect(users) {
        android.util.Log.d("HomeUI", "UserSearchResults composable called with ${users.size} users")
        users.forEach { user ->
            android.util.Log.d("HomeUI", "User in results list: ${user.username}, ID: ${user.id}")
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(users) { user ->
            UserSearchItem(
                user = user,
                onClick = { onUserClick(user) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchItem(
    user: User,
    onClick: () -> Unit
) {
    // Debug rendering of this item
    SideEffect {
        android.util.Log.d("UserItem", "Rendering user item for: ${user.username}")
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with more distinct styling
            Card(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // More visible online indicator
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (user.isOnline) 
                               MaterialTheme.colorScheme.secondary 
                            else 
                               MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                ) {}
            }
        }
    }
}

@Composable
fun EmptyChats() {
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
                text = "No Chats Yet",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Start a new conversation by tapping the + button",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptySearchResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Users Found",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}