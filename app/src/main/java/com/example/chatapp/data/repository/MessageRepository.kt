package com.example.chatapp.data.repository

import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.MessageType
import com.example.chatapp.data.model.TypingStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val encryptionManager: EncryptionManager
) {
    private val messagesCollection = firestore.collection("messages")
    private val threadsCollection = firestore.collection("threads")
    private val typingCollection = firestore.collection("typing_status")
    
    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo("chat_id", chatId)
            .whereEqualTo("thread_id", null) // Exclude messages in threads
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    
    fun getThreadMessages(threadId: String): Flow<List<Message>> = callbackFlow {
        // First, get the parent message
        val threadDoc = threadsCollection.document(threadId).get().await()
        val parentMessageId = threadDoc.getString("parent_message_id") ?: ""
        
        if (parentMessageId.isNotEmpty()) {
            val parentMessage = messagesCollection.document(parentMessageId).get().await()
                .toObject(Message::class.java)?.copy(id = parentMessageId)
            
            if (parentMessage != null) {
                // Now listen for thread messages
                val listener = messagesCollection
                    .whereEqualTo("thread_id", threadId)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        
                        val threadMessages = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(Message::class.java)?.copy(id = doc.id)
                        } ?: emptyList()
                        
                        // Combine parent message with thread messages
                        trySend(listOf(parentMessage) + threadMessages)
                    }
                
                awaitClose { listener.remove() }
            } else {
                // No parent message found
                trySend(emptyList())
                close()
            }
        } else {
            // No thread found
            trySend(emptyList())
            close()
        }
    }
    
    suspend fun sendMessage(message: Message): String {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in")
        
        // Create the message with sender ID and timestamp
        val newMessage = message.copy(
            senderId = currentUser.uid,
            timestamp = Timestamp.now()
        )
        
        // If encryption is enabled, encrypt the message
        val finalMessage = if (newMessage.isEncrypted) {
            encryptMessage(newMessage)
        } else {
            newMessage
        }
        
        // Save the message to Firestore
        val messageRef = messagesCollection.document()
        messageRef.set(finalMessage.copy(id = messageRef.id)).await()
        
        return messageRef.id
    }
    
    private suspend fun encryptMessage(message: Message): Message {
        val chatId = message.chatId
        val chatDoc = firestore.collection("chats").document(chatId).get().await()
        val participants = chatDoc.get("participants") as? List<String> ?: return message
        
        // Skip current user from encryption keys (they'll use their private key)
        val currentUserId = auth.currentUser?.uid ?: return message
        val otherParticipants = participants.filter { it != currentUserId }
        
        return encryptionManager.encryptMessageForParticipants(message, otherParticipants)
    }
    
    suspend fun deleteMessage(messageId: String): Boolean {
        return try {
            messagesCollection.document(messageId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateMessage(messageId: String, newContent: String): Boolean {
        return try {
            messagesCollection.document(messageId).update(
                mapOf(
                    "content" to newContent,
                    "is_edited" to true,
                    "edit_timestamp" to Timestamp.now()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun markMessageAsRead(messageId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            messagesCollection.document(messageId).update(
                "read_by", FieldValue.arrayUnion(currentUserId)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun addReaction(messageId: String, emoji: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            // Reactions are stored as a map where keys are user IDs and values are emojis
            messagesCollection.document(messageId).update(
                "reactions.$currentUserId", emoji
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun removeReaction(messageId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            // Remove the current user's reaction
            messagesCollection.document(messageId).update(
                "reactions.$currentUserId", FieldValue.delete()
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun pinMessage(messageId: String, chatId: String): Boolean {
        try {
            // Update the message document
            messagesCollection.document(messageId).update("is_pinned", true).await()
            
            // Add the message ID to the chat's pinned messages
            firestore.collection("chats").document(chatId).update(
                "pinned_message_ids", FieldValue.arrayUnion(messageId)
            ).await()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    suspend fun unpinMessage(messageId: String, chatId: String): Boolean {
        try {
            // Update the message document
            messagesCollection.document(messageId).update("is_pinned", false).await()
            
            // Remove the message ID from the chat's pinned messages
            firestore.collection("chats").document(chatId).update(
                "pinned_message_ids", FieldValue.arrayRemove(messageId)
            ).await()
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    suspend fun createThread(parentMessageId: String, chatId: String): String {
        // First, check if a thread already exists for this message
        val message = messagesCollection.document(parentMessageId).get().await()
            .toObject(Message::class.java)
        
        val existingThreadId = message?.threadId
        if (existingThreadId != null) {
            return existingThreadId
        }
        
        // Create a new thread
        val threadRef = threadsCollection.document()
        val threadData = mapOf(
            "parent_message_id" to parentMessageId,
            "chat_id" to chatId,
            "created_at" to Timestamp.now(),
            "participant_ids" to listOf(auth.currentUser?.uid),
            "message_count" to 0
        )
        
        threadRef.set(threadData).await()
        
        // Update the parent message with the thread ID
        messagesCollection.document(parentMessageId).update(
            "thread_id", threadRef.id
        ).await()
        
        return threadRef.id
    }
    
    suspend fun uploadFile(file: File, chatId: String, messageType: MessageType): Message {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val fileName = "${UUID.randomUUID()}_${file.name}"
        val fileRef = storage.reference.child("chats/$chatId/files/$fileName")
        
        // Upload the file
        val uploadTask = fileRef.putFile(android.net.Uri.fromFile(file)).await()
        val downloadUrl = fileRef.downloadUrl.await().toString()
        
        // Create a message with the file information
        return Message(
            chatId = chatId,
            senderId = currentUserId,
            type = messageType,
            content = when (messageType) {
                MessageType.IMAGE -> "Image"
                MessageType.FILE -> "File: ${file.name}"
                MessageType.AUDIO -> "Audio"
                MessageType.VIDEO -> "Video"
                else -> file.name
            },
            fileUrl = downloadUrl,
            fileName = file.name,
            fileSize = file.length(),
            fileType = file.extension
        )
    }
    
    fun getTypingStatuses(chatId: String): Flow<List<TypingStatus>> = callbackFlow {
        val listener = typingCollection
            .whereEqualTo("chat_id", chatId)
            .whereEqualTo("is_typing", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val typingStatuses = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(TypingStatus::class.java)
                } ?: emptyList()
                
                // Filter out old typing indicators (e.g., older than 10 seconds)
                val currentTime = Timestamp.now()
                val recentTyping = typingStatuses.filter { status ->
                    val diff = currentTime.seconds - status.timestamp.seconds
                    diff < 10 // Only show typing if updated in the last 10 seconds
                }
                
                trySend(recentTyping)
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun updateTypingStatus(chatId: String, isTyping: Boolean): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        val typingStatusId = "$chatId-$currentUserId"
        val typingStatus = TypingStatus(
            userId = currentUserId,
            chatId = chatId,
            isTyping = isTyping,
            timestamp = Timestamp.now()
        )
        
        return try {
            typingCollection.document(typingStatusId).set(typingStatus).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}