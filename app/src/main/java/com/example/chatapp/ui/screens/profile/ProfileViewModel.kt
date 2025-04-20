package com.example.chatapp.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.model.UserSettings
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId
    
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user
    
    private val _userSettings = MutableStateFlow<UserSettings?>(null)
    val userSettings: StateFlow<UserSettings?> = _userSettings
    
    init {
        viewModelScope.launch {
            _currentUserId.value = auth.currentUser?.uid
        }
    }
    
    fun loadCurrentUser() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            loadUser(userId)
        }
    }
    
    fun loadUser(userId: String) {
        viewModelScope.launch {
            userRepository.getUserById(userId)?.let {
                _user.value = it
            }
            
            userRepository.getUserSettings(userId)?.let {
                _userSettings.value = it
            }
        }
    }
    
    fun updateSettings(settings: UserSettings) {
        viewModelScope.launch {
            userRepository.updateUserSettings(settings)
            _userSettings.value = settings
        }
    }
    
    fun updateProfile(displayName: String, bio: String, photoUrl: String?) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: return@launch
            val currentUser = _user.value ?: return@launch
            
            val updatedUser = currentUser.copy(
                displayName = displayName,
                photoUrl = photoUrl ?: currentUser.photoUrl,
                bio = bio
            )
            
            userRepository.updateUser(updatedUser)
            _user.value = updatedUser
        }
    }
    
    fun createOrOpenChat(otherUserId: String, onChatCreated: (String) -> Unit) {
        viewModelScope.launch {
            val chatId = chatRepository.createPrivateChat(otherUserId)
            onChatCreated(chatId)
        }
    }
}