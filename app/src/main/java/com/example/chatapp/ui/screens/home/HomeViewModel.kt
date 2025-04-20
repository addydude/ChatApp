package com.example.chatapp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _chats = MutableStateFlow(emptyList<com.example.chatapp.data.model.Chat>())
    val chats: StateFlow<List<com.example.chatapp.data.model.Chat>> = _chats

    private val _chatUsers = MutableStateFlow<Map<String, User>>(emptyMap())
    val chatUsers: StateFlow<Map<String, User>> = _chatUsers

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: Flow<List<User>> = _searchQuery
        .debounce(300)
        .filter { it.length >= 2 }
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(emptyList())
            } else {
                userRepository.searchUsers(query)
            }
        }
        .onEach { results ->
            android.util.Log.d("HomeViewModel", "Search results received in ViewModel: ${results.size} users")
            results.forEach { user ->
                android.util.Log.d("HomeViewModel", "Search result in ViewModel: ${user.username}, ID: ${user.id}")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadCurrentUser()
        loadChats()
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUser.value = user
                // Update user status to online
                userRepository.updateUserStatus(true)
            }
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            chatRepository.getUserChats().collect { chatsList ->
                _chats.value = chatsList

                // Collect user IDs from all chats
                val userIds = chatsList.flatMap { it.participants }.distinct()
                
                // Load all users in one go
                if (userIds.isNotEmpty()) {
                    userRepository.getUsersById(userIds).collect { users ->
                        _chatUsers.value = users.associateBy { it.id }
                    }
                }
            }
        }
    }

    fun toggleSearch() {
        _isSearchActive.value = !_isSearchActive.value
        if (!_isSearchActive.value) {
            _searchQuery.value = ""
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createOrOpenChat(otherUserId: String, onChatCreated: (String) -> Unit) {
        viewModelScope.launch {
            val chatId = chatRepository.createPrivateChat(otherUserId)
            onChatCreated(chatId)
        }
    }

    // Function to create test users for development purposes
    fun createTestUsers() {
        viewModelScope.launch {
            try {
                android.util.Log.d("HomeViewModel", "Creating test users - button clicked")
                
                // Double check that Firebase is ready
                if (auth.currentUser == null) {
                    android.util.Log.e("HomeViewModel", "Cannot create test users - not logged in")
                    return@launch
                }
                
                val success = userRepository.createTestUsers()
                android.util.Log.d("HomeViewModel", "Test users creation result: $success")
                
                if (success) {
                    // Set search to "test" and make sure search is active
                    _isSearchActive.value = true
                    _searchQuery.value = "test"
                    
                    // Short delay to allow Firestore to update
                    kotlinx.coroutines.delay(500)
                    
                    // Force a fresh search to retrieve test users
                    userRepository.searchUsers("test").collect { users ->
                        android.util.Log.d("HomeViewModel", "After creating test users, found ${users.size} test users")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error creating test users: ${e.message}", e)
            }
        }
    }
}