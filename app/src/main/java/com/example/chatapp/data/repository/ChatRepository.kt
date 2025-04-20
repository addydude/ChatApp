package com.example.chatapp.data.repository

import com.example.chatapp.data.model.Chat
import com.example.chatapp.data.model.ChatType
import com.example.chatapp.data.model.Message
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val chatsCollection = firestore.collection("chats")
    
    suspend fun createPrivateChat(otherUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        
        // Check if chat already exists
        val existingChatId = findExistingPrivateChat(currentUserId, otherUserId)
        if (existingChatId != null) {
            return existingChatId
        }
        
        // Create new chat
        val participants = listOf(currentUserId, otherUserId).sorted()
        val chat = Chat(
            type = ChatType.PRIVATE,
            participants = participants,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now(),
            lastMessageTimestamp = Timestamp.now()
        )
        
        val chatRef = chatsCollection.document()
        chatRef.set(chat).await()
        
        return chatRef.id
    }
    
    suspend fun createGroupChat(
        name: String,
        participants: List<String>,
        imageUrl: String? = null
    ): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        
        // Ensure creator is in the participants list
        val allParticipants = (participants + currentUserId).distinct()
        
        val chat = Chat(
            type = ChatType.GROUP,
            name = name,
            participants = allParticipants,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now(),
            lastMessageTimestamp = Timestamp.now(),
            metadata = if (imageUrl != null) mapOf("imageUrl" to imageUrl) else emptyMap()
        )
        
        val chatRef = chatsCollection.document()
        chatRef.set(chat).await()
        
        return chatRef.id
    }
    
    private suspend fun findExistingPrivateChat(userId1: String, userId2: String): String? {
        val participants = listOf(userId1, userId2).sorted()
        
        val result = chatsCollection
            .whereEqualTo("type", ChatType.PRIVATE.name)
            .whereArrayContains("participants", userId1)
            .get()
            .await()
        
        return result.documents.find { doc ->
            val chat = doc.toObject(Chat::class.java)
            chat?.participants?.containsAll(participants) == true && chat.participants.size == 2
        }?.id
    }
    
    fun getUserChats(): Flow<List<Chat>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = chatsCollection
            .whereArrayContains("participants", currentUserId)
            .orderBy("last_message_timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(chats)
            }
        
        awaitClose { listener.remove() }
    }
    
    fun getChatById(chatId: String): Flow<Chat?> = callbackFlow {
        val listener = chatsCollection.document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val chat = snapshot?.toObject(Chat::class.java)?.copy(id = snapshot.id)
                trySend(chat)
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun addParticipantToChat(chatId: String, userId: String): Boolean {
        return try {
            chatsCollection.document(chatId).update(
                "participants", FieldValue.arrayUnion(userId)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun removeParticipantFromChat(chatId: String, userId: String): Boolean {
        return try {
            chatsCollection.document(chatId).update(
                "participants", FieldValue.arrayRemove(userId)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun leaveChat(chatId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        return removeParticipantFromChat(chatId, currentUserId)
    }
    
    suspend fun updateLastMessage(chatId: String, message: Message): Boolean {
        val updates = mapOf(
            "last_message_id" to message.id,
            "last_message_timestamp" to message.timestamp,
            "last_message_sender_id" to message.senderId,
            "last_message_text" to message.content,
            "updated_at" to Timestamp.now()
        )
        
        return try {
            chatsCollection.document(chatId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun incrementUnreadCount(chatId: String, excludeUserId: String): Boolean {
        val chat = chatsCollection.document(chatId).get().await().toObject(Chat::class.java) ?: return false
        
        val updates = mutableMapOf<String, Any>()
        
        // Increment unread count for all participants except the sender
        chat.participants.filter { it != excludeUserId }.forEach { userId ->
            val currentCount = chat.unreadCount[userId] ?: 0
            updates["unread_count.$userId"] = currentCount + 1
        }
        
        return try {
            chatsCollection.document(chatId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun resetUnreadCount(chatId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            chatsCollection.document(chatId).update(
                "unread_count.$currentUserId", 0
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateChatName(chatId: String, newName: String): Boolean {
        return try {
            chatsCollection.document(chatId).update("name", newName).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun deleteChat(chatId: String): Boolean {
        return try {
            // This would typically also delete all messages in the chat
            // For that, we'd need a transaction or a Cloud Function
            chatsCollection.document(chatId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getPinnedMessages(chatId: String): List<String> {
        return try {
            val chat = chatsCollection.document(chatId).get().await().toObject(Chat::class.java)
            chat?.pinnedMessageIds ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun pinMessage(messageId: String, chatId: String): Boolean {
        return try {
            chatsCollection.document(chatId).update(
                "pinned_message_ids", FieldValue.arrayUnion(messageId)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun unpinMessage(messageId: String, chatId: String): Boolean {
        return try {
            chatsCollection.document(chatId).update(
                "pinned_message_ids", FieldValue.arrayRemove(messageId)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}