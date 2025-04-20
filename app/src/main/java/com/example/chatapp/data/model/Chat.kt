package com.example.chatapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Date

enum class ChatType {
    PRIVATE,
    GROUP
}

@IgnoreExtraProperties
data class Chat(
    @DocumentId
    val id: String = "",
    val type: ChatType = ChatType.PRIVATE,
    val name: String = "", // Only used for group chats
    val participants: List<String> = emptyList(),
    
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now(),
    
    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    var updatedAt: Timestamp = Timestamp.now(),
    
    @get:PropertyName("last_message_id")
    @set:PropertyName("last_message_id")
    var lastMessageId: String = "",
    
    @get:PropertyName("last_message_text")
    @set:PropertyName("last_message_text")
    var lastMessageText: String = "",
    
    @get:PropertyName("last_message_timestamp")
    @set:PropertyName("last_message_timestamp")
    var lastMessageTimestamp: Timestamp = Timestamp.now(),
    
    @get:PropertyName("last_message_sender_id")
    @set:PropertyName("last_message_sender_id")
    var lastMessageSenderId: String = "",
    
    @get:PropertyName("unread_count")
    @set:PropertyName("unread_count")
    var unreadCount: Map<String, Int> = emptyMap(), // Map of user ID to unread count
    
    @get:PropertyName("pinned_message_ids")
    @set:PropertyName("pinned_message_ids")
    var pinnedMessageIds: List<String> = emptyList(),
    
    @get:PropertyName("is_muted")
    @set:PropertyName("is_muted")
    var isMuted: Map<String, Boolean> = emptyMap(), // Map of user ID to mute status
    
    val metadata: Map<String, Any> = emptyMap() // For extensibility (e.g., group chat image URL)
) {
    // Helper methods
    
    fun isPrivate(): Boolean = type == ChatType.PRIVATE
    
    fun isGroup(): Boolean = type == ChatType.GROUP
    
    fun getDisplayName(currentUserId: String, users: Map<String, User>): String {
        return when {
            isGroup() -> name
            isPrivate() -> {
                val otherParticipantId = participants.firstOrNull { it != currentUserId } ?: return "Unknown"
                val otherUser = users[otherParticipantId]
                otherUser?.displayName ?: "Unknown User"
            }
            else -> "Unknown Chat"
        }
    }
    
    fun getAvatarUrl(currentUserId: String, users: Map<String, User>): String? {
        return when {
            isGroup() -> metadata["imageUrl"] as? String
            isPrivate() -> {
                val otherParticipantId = participants.firstOrNull { it != currentUserId } ?: return null
                users[otherParticipantId]?.photoUrl
            }
            else -> null
        }
    }
    
    fun getUnreadCount(userId: String): Int {
        return unreadCount[userId] ?: 0
    }
    
    fun isMutedForUser(userId: String): Boolean {
        return isMuted[userId] ?: false
    }
}

data class Channel(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val ownerId: String = "",
    val moderatorIds: List<String> = listOf(),
    val memberIds: List<String> = listOf(),
    val isPublic: Boolean = true,
    val createdAt: Timestamp = Timestamp.now(),
    val topics: List<String> = listOf(),
    val pinnedMessageIds: List<String> = listOf()
)

data class Poll(
    @DocumentId
    val id: String = "",
    val question: String = "",
    val options: List<PollOption> = listOf(),
    val creatorId: String = "",
    val chatId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp? = null,
    val isMultiChoice: Boolean = false,
    val isAnonymous: Boolean = false
)

data class PollOption(
    val id: String = "",
    val text: String = "",
    val voters: List<String> = listOf() // User IDs of those who voted for this option
)

data class Thread(
    @DocumentId
    val id: String = "",
    val parentMessageId: String = "",
    val chatId: String = "",
    val participantIds: List<String> = listOf(),
    val lastMessageTimestamp: Timestamp = Timestamp.now(),
    val messageCount: Int = 0
)