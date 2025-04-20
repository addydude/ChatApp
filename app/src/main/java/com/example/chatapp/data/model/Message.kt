package com.example.chatapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    CONTACT,
    SYSTEM
}

@IgnoreExtraProperties
data class Message(
    @DocumentId
    val id: String = "",
    
    @get:PropertyName("chat_id")
    @set:PropertyName("chat_id")
    var chatId: String = "",
    
    @get:PropertyName("thread_id")
    @set:PropertyName("thread_id")
    var threadId: String? = null,
    
    @get:PropertyName("sender_id")
    @set:PropertyName("sender_id")
    var senderId: String = "",
    
    val type: MessageType = MessageType.TEXT,
    var content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    
    @get:PropertyName("is_edited")
    @set:PropertyName("is_edited")
    var isEdited: Boolean = false,
    
    @get:PropertyName("edit_timestamp")
    @set:PropertyName("edit_timestamp") 
    var editTimestamp: Timestamp? = null,
    
    @get:PropertyName("is_deleted")
    @set:PropertyName("is_deleted")
    var isDeleted: Boolean = false,
    
    @get:PropertyName("is_pinned")
    @set:PropertyName("is_pinned")
    var isPinned: Boolean = false,
    
    @get:PropertyName("read_by")
    @set:PropertyName("read_by")
    var readBy: List<String> = emptyList(),
    
    var reactions: Map<String, String> = emptyMap(), // User ID to emoji
    
    @get:PropertyName("is_encrypted")
    @set:PropertyName("is_encrypted")
    var isEncrypted: Boolean = false,
    
    @get:PropertyName("reply_to_message_id")
    @set:PropertyName("reply_to_message_id")
    var replyToMessageId: String? = null,
    
    @get:PropertyName("forwarded_from")
    @set:PropertyName("forwarded_from")
    var forwardedFrom: String? = null, // User ID or chat ID
    
    // For file messages
    @get:PropertyName("file_url")
    @set:PropertyName("file_url")
    var fileUrl: String? = null,
    
    @get:PropertyName("file_name")
    @set:PropertyName("file_name")
    var fileName: String? = null,
    
    @get:PropertyName("file_size")
    @set:PropertyName("file_size")
    var fileSize: Long? = null,
    
    @get:PropertyName("file_type")
    @set:PropertyName("file_type")
    var fileType: String? = null,
    
    // For location messages
    var latitude: Double? = null,
    var longitude: Double? = null,
    
    var metadata: Map<String, Any> = emptyMap() // For extensibility
) {
    // Helper methods
    
    fun isOwnMessage(currentUserId: String): Boolean {
        return senderId == currentUserId
    }
    
    fun isRead(): Boolean {
        return readBy.isNotEmpty()
    }
    
    fun isReadBy(userId: String): Boolean {
        return readBy.contains(userId)
    }
    
    fun getReactionCount(): Int {
        return reactions.size
    }
    
    fun getReactionsGrouped(): Map<String, Int> {
        return reactions.values
            .groupingBy { it }
            .eachCount()
    }
    
    fun getUserReaction(userId: String): String? {
        return reactions[userId]
    }
    
    fun getFormattedTime(): String {
        // Format timestamp to readable time
        val date = timestamp.toDate()
        val hours = date.hours.toString().padStart(2, '0')
        val minutes = date.minutes.toString().padStart(2, '0')
        return "$hours:$minutes"
    }
    
    fun isImage(): Boolean = type == MessageType.IMAGE
    fun isVideo(): Boolean = type == MessageType.VIDEO
    fun isAudio(): Boolean = type == MessageType.AUDIO
    fun isFile(): Boolean = type == MessageType.FILE
    fun isLocation(): Boolean = type == MessageType.LOCATION
    fun isSystem(): Boolean = type == MessageType.SYSTEM
    
    fun hasThread(): Boolean = threadId != null
    
    fun isReply(): Boolean = replyToMessageId != null
    
    fun isForwarded(): Boolean = forwardedFrom != null
}

data class TypingStatus(
    @get:PropertyName("user_id")
    @set:PropertyName("user_id")
    var userId: String = "",
    
    @get:PropertyName("chat_id")
    @set:PropertyName("chat_id")
    var chatId: String = "",
    
    @get:PropertyName("is_typing")
    @set:PropertyName("is_typing")
    var isTyping: Boolean = false,
    
    var timestamp: Timestamp = Timestamp.now()
)

data class Reaction(
    val messageId: String,
    val userId: String,
    val emoji: String,
    val timestamp: Timestamp = Timestamp.now()
)

data class ReadReceipt(
    val messageId: String,
    val userId: String,
    val timestamp: Timestamp = Timestamp.now()
)