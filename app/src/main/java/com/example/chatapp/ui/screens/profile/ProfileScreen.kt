package com.example.chatapp.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.User
import com.example.chatapp.data.model.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val currentUserId by viewModel.currentUserId.collectAsState()
    val user by viewModel.user.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val isCurrentUser = userId == null || userId == currentUserId
    
    // Load user data
    LaunchedEffect(userId) {
        if (userId != null) {
            viewModel.loadUser(userId)
        } else {
            viewModel.loadCurrentUser()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCurrentUser) "My Profile" else "${user?.username}'s Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isCurrentUser) {
                        IconButton(onClick = { /* Open edit profile dialog */ }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            ProfileContent(
                user = user!!,
                userSettings = userSettings,
                isCurrentUser = isCurrentUser,
                onSettingsChange = viewModel::updateSettings,
                onNavigateToChat = onNavigateToChat,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
fun ProfileContent(
    user: User,
    userSettings: UserSettings?,
    isCurrentUser: Boolean,
    onSettingsChange: (UserSettings) -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Image
        Card(
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // User Name
        Text(
            text = user.username,
            style = MaterialTheme.typography.headlineMedium
        )
        
        // User Email
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Settings section (only for current user)
        if (isCurrentUser && userSettings != null) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 16.dp)
            )
            
            // Privacy Settings
            SettingsCategory(title = "Privacy") {
                SettingsSwitch(
                    title = "Enable Encryption",
                    subtitle = "Enable end-to-end encryption for messages",
                    checked = userSettings.enableEncryption,
                    onCheckedChange = { 
                        onSettingsChange(userSettings.copy(enableEncryption = it))
                    },
                    icon = Icons.Default.Security
                )
                
                SettingsSwitch(
                    title = "Show Online Status",
                    subtitle = "Allow others to see when you're online",
                    checked = userSettings.showOnlineStatus,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(showOnlineStatus = it))
                    },
                    icon = Icons.Default.Visibility
                )
                
                SettingsSwitch(
                    title = "Read Receipts",
                    subtitle = "Let others know when you've read their messages",
                    checked = userSettings.readReceipts,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(readReceipts = it))
                    },
                    icon = Icons.Default.DoneAll
                )
                
                SettingsSwitch(
                    title = "Typing Indicators",
                    subtitle = "Show when you're typing a message",
                    checked = userSettings.typingIndicators,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(typingIndicators = it))
                    },
                    icon = Icons.Default.Keyboard
                )
            }
            
            // Notification Settings
            SettingsCategory(title = "Notifications") {
                SettingsSwitch(
                    title = "Enable Notifications",
                    subtitle = "Receive notifications for new messages",
                    checked = userSettings.notificationsEnabled,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(notificationsEnabled = it))
                    },
                    icon = Icons.Default.Notifications
                )
                
                SettingsSwitch(
                    title = "Sound",
                    subtitle = "Play sound for notifications",
                    checked = userSettings.soundEnabled,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(soundEnabled = it))
                    },
                    icon = Icons.Default.VolumeUp,
                    enabled = userSettings.notificationsEnabled
                )
                
                SettingsSwitch(
                    title = "Vibration",
                    subtitle = "Vibrate for notifications",
                    checked = userSettings.vibrationEnabled,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(vibrationEnabled = it))
                    },
                    icon = Icons.Default.Vibration,
                    enabled = userSettings.notificationsEnabled
                )
            }
            
            // Media Settings
            SettingsCategory(title = "Media") {
                SettingsSwitch(
                    title = "Auto-download Media",
                    subtitle = "Automatically download photos and videos",
                    checked = userSettings.mediaAutoDownload,
                    onCheckedChange = {
                        onSettingsChange(userSettings.copy(mediaAutoDownload = it))
                    },
                    icon = Icons.Default.Download
                )
            }
        } else {
            // Actions for viewing other users' profiles
            OutlinedButton(
                onClick = { 
                    val userId = user.id
                    // Navigate to chat with this user
                    viewModel.createOrOpenChat(userId) { chatId ->
                        onNavigateToChat(chatId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Send Message")
            }
            
            OutlinedButton(
                onClick = { /* Make call */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Call")
            }
            
            OutlinedButton(
                onClick = { /* Block user */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Block User")
            }
        }
    }
}

@Composable
fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}