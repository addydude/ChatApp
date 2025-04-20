package com.example.chatapp.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.*
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText
    
    private val _isEncrypted = MutableStateFlow(false)
    val isEncrypted: StateFlow<Boolean> = _isEncrypted
    
    private val _replyToMessage = MutableStateFlow<Message?>(null)
    val replyToMessage: StateFlow<Message?> = _replyToMessage
    
    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping
    
    // Active chat data
    private val _chatId = MutableStateFlow<String?>(null)
    private val _chat = MutableStateFlow<Chat?>(null)
    val chat: StateFlow<Chat?> = _chat
    
    // Messages in the current chat
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    // Users in the current chat
    private val _chatUsers = MutableStateFlow<Map<String, User>>(emptyMap())
    val chatUsers: StateFlow<Map<String, User>> = _chatUsers
    
    // Typing indicators
    private val _typingUsers = MutableStateFlow<List<TypingStatus>>(emptyList())
    val typingUsers: StateFlow<List<TypingStatus>> = _typingUsers
    
    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUserId.value = user.id
            }
        }
    }
    
    fun loadChat(chatId: String) {
        _chatId.value = chatId
        
        // Reset state
        _messages.value = emptyList()
        _chatUsers.value = emptyMap()
        _replyToMessage.value = null
        _isEncrypted.value = false
        
        viewModelScope.launch {
            // Reset unread count
            chatRepository.resetUnreadCount(chatId)
            
            // Load chat data
            chatRepository.getChatById(chatId).collect { chat ->
                _chat.value = chat
                
                // Load users in this chat
                chat?.participants?.let { userIds ->
                    userRepository.getUsersById(userIds).collect { users ->
                        _chatUsers.value = users.associateBy { it.id }
                    }
                }
            }
            
            // Load messages for this chat
            messageRepository.getMessages(chatId).collect { messagesList ->
                _messages.value = messagesList
                
                // Mark messages as read
                messagesList.forEach { message ->
                    if (message.senderId != _currentUserId.value && !message.readBy.contains(_currentUserId.value)) {
                        messageRepository.markMessageAsRead(message.id)
                    }
                }
            }
            
            // Load pinned messages
            val pinnedMessageIds = chatRepository.getPinnedMessages(chatId)
            val pinnedMsgs = _messages.value.filter { pinnedMessageIds.contains(it.id) }
            _pinnedMessages.value = pinnedMsgs
            
            // Monitor typing indicators
            messageRepository.getTypingStatuses(chatId).collect { typingStatuses ->
                _typingUsers.value = typingStatuses.filter { it.userId != _currentUserId.value }
            }
        }
    }
    
    fun loadThread(threadId: String) {
        viewModelScope.launch {
            messageRepository.getThreadMessages(threadId).collect { threadMessages ->
                _messages.value = threadMessages
                
                // Mark messages as read
                threadMessages.forEach { message ->
                    if (message.senderId != _currentUserId.value && !message.readBy.contains(_currentUserId.value)) {
                        messageRepository.markMessageAsRead(message.id)
                    }
                }
            }
        }
    }
    
    fun setMessageText(text: String) {
        _messageText.value = text
        
        // Update typing indicator
        val chatId = _chatId.value ?: return
        val currentTyping = text.isNotEmpty()
        
        if (currentTyping != _isTyping.value) {
            _isTyping.value = currentTyping
            viewModelScope.launch {
                messageRepository.updateTypingStatus(chatId, currentTyping)
            }
        }
    }
    
    fun toggleEncryption() {
        _isEncrypted.value = !_isEncrypted.value
    }
    
    fun sendMessage() {
        val text = _messageText.value.trim()
        val chatId = _chatId.value ?: return
        
        if (text.isEmpty()) return
        
        viewModelScope.launch {
            val message = Message(
                chatId = chatId,
                content = text,
                isEncrypted = _isEncrypted.value,
                replyToMessageId = _replyToMessage.value?.id
            )
            
            val messageId = messageRepository.sendMessage(message)
            
            // Update last message in chat
            val sentMessage = _messages.value.find { it.id == messageId } ?: message.copy(id = messageId)
            chatRepository.updateLastMessage(chatId, sentMessage)
            
            // Increment unread count for other participants
            _currentUserId.value?.let { userId ->
                chatRepository.incrementUnreadCount(chatId, userId)
            }
            
            // Reset message text and typing indicator
            _messageText.value = ""
            _replyToMessage.value = null
            _isTyping.value = false
            messageRepository.updateTypingStatus(chatId, false)
        }
    }
    
    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            messageRepository.addReaction(messageId, emoji)
        }
    }
    
    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            messageRepository.removeReaction(messageId)
        }
    }
    
    fun setReplyToMessage(message: Message?) {
        _replyToMessage.value = message
    }
    
    fun pinMessage(messageId: String) {
        val chatId = _chatId.value ?: return
        
        viewModelScope.launch {
            messageRepository.pinMessage(messageId, chatId)
            
            // Update pinned messages
            val pinnedMessageIds = chatRepository.getPinnedMessages(chatId)
            val pinnedMsgs = _messages.value.filter { pinnedMessageIds.contains(it.id) }
            _pinnedMessages.value = pinnedMsgs
        }
    }
    
    fun unpinMessage(messageId: String) {
        val chatId = _chatId.value ?: return
        
        viewModelScope.launch {
            messageRepository.unpinMessage(messageId, chatId)
            
            // Update pinned messages
            val pinnedMessageIds = chatRepository.getPinnedMessages(chatId)
            val pinnedMsgs = _messages.value.filter { pinnedMessageIds.contains(it.id) }
            _pinnedMessages.value = pinnedMsgs
        }
    }
    
    fun createThread(parentMessageId: String): Flow<String> = flow {
        val chatId = _chatId.value ?: return@flow
        
        val threadId = messageRepository.createThread(parentMessageId, chatId)
        emit(threadId)
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }
    
    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            messageRepository.updateMessage(messageId, newContent)
        }
    }
}