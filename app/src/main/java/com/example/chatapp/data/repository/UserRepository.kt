package com.example.chatapp.data.repository

import android.util.Log
import com.example.chatapp.data.model.User
import com.example.chatapp.utils.FirebaseUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val firebaseUtils: FirebaseUtils
) {
    private val TAG = "UserRepository"
    private val usersCollection = firestore.collection("users")
    
    suspend fun getCurrentUser(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return getUserById(userId)
    }
    
    suspend fun getUserById(userId: String): User? {
        return try {
            firebaseUtils.executeWithRetry("getUserById") {
                val document = usersCollection.document(userId).get().await()
                if (document.exists()) {
                    document.toObject(User::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user: $userId", e)
            null
        }
    }
    
    fun getUsersById(userIds: List<String>): Flow<List<User>> = callbackFlow {
        if (userIds.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val listener = usersCollection.whereIn("id", userIds.take(10)) // Firestore limit is 10 values
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for users", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val users = snapshot?.documents?.mapNotNull {
                    it.toObject(User::class.java)
                } ?: emptyList()
                
                trySend(users)
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun createUser(user: User): Boolean {
        return try {
            firebaseUtils.executeWithRetry("createUser") {
                usersCollection.document(user.id).set(user).await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user", e)
            false
        }
    }
    
    suspend fun updateUser(user: User): Boolean {
        return try {
            firebaseUtils.executeWithRetry("updateUser") {
                usersCollection.document(user.id).set(user).await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user", e)
            false
        }
    }
    
    suspend fun updateUserStatus(isOnline: Boolean): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("updateUserStatus") {
                val updates = mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to Timestamp.now(),
                    "lastActive" to Timestamp.now()
                )
                
                usersCollection.document(userId).update(updates).await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user status", e)
            false
        }
    }
    
    fun searchUsers(query: String): Flow<List<User>> = callbackFlow {
        if (query.length < 2) {
            Log.d(TAG, "Query too short: $query")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val currentUserId = auth.currentUser?.uid
        Log.d(TAG, "Starting search for: '$query', current user: $currentUserId")
        
        try {
            // Use a safer implementation that batches Firebase requests if needed
            val snapshot = firebaseUtils.executeWithRetry("searchUsers") {
                usersCollection
                    .orderBy("username")
                    .startAt(query)
                    .endAt(query + "\uf8ff")
                    .limit(20) // Add a reasonable limit
                    .get()
                    .await()
            }
            
            Log.d(TAG, "Retrieved ${snapshot.size()} users from database")
            
            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    val user = doc.toObject(User::class.java)
                    if (user != null && user.id != currentUserId) {
                        Log.d(TAG, "Found user match: ${user.username}")
                        user
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error mapping document to User: ${e.message}")
                    null
                }
            }
            
            trySend(users)
            
        } catch (e: Exception) {
            // Capture and log the full error message with the index creation link
            val errorMessage = e.message ?: "Unknown error"
            Log.e(TAG, "FIREBASE INDEX ERROR: $errorMessage", e)
            
            // Extract and log the index creation URL if present
            val indexUrl = extractFirebaseIndexUrl(errorMessage)
            if (indexUrl != null) {
                Log.e(TAG, "FIREBASE INDEX CREATION REQUIRED: To fix this error, visit: $indexUrl", e)
            }
            
            // Fallback to a simple query approach with retry logic
            try {
                Log.d(TAG, "Falling back to simple collection get")
                
                val snapshot = firebaseUtils.executeWithRetry("searchUsers-fallback") {
                    usersCollection.limit(50).get().await()
                }
                
                Log.d(TAG, "Fallback: Retrieved ${snapshot.size()} total users")
                
                val matchedUsers = mutableListOf<User>()
                
                snapshot.documents.forEach { doc ->
                    try {
                        val user = doc.toObject(User::class.java)
                        
                        if (user != null && user.id != currentUserId) {
                            // Check for match against the query string
                            val usernameMatches = user.username.contains(query, ignoreCase = true)
                            val emailMatches = user.email.contains(query, ignoreCase = true) 
                            val displayNameMatches = user.displayName?.contains(query, ignoreCase = true) == true
                            
                            if (usernameMatches || emailMatches || displayNameMatches) {
                                Log.d(TAG, "Fallback: Found matching user: ${user.username}, id: ${user.id}")
                                matchedUsers.add(user)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping document in fallback: ${e.message}")
                    }
                }
                
                Log.d(TAG, "Fallback: Returning ${matchedUsers.size} matched users")
                trySend(matchedUsers)
                
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback query also failed: ${e2.message}", e2)
                
                // If all else fails, return empty list
                trySend(emptyList())
                
                // Try to create test users for development purposes
                try {
                    createTestUsers()
                } catch (e3: Exception) {
                    Log.e(TAG, "Creating test users failed: ${e3.message}", e3)
                }
            }
        }
        
        awaitClose { }
    }
    
    suspend fun addFriend(friendId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("addFriend") {
                // Add to current user's friends list
                usersCollection.document(userId).update(
                    "friends", com.google.firebase.firestore.FieldValue.arrayUnion(friendId)
                ).await()
                
                // Add current user to friend's friends list
                usersCollection.document(friendId).update(
                    "friends", com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                ).await()
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding friend", e)
            false
        }
    }
    
    suspend fun removeFriend(friendId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("removeFriend") {
                // Remove from current user's friends list
                usersCollection.document(userId).update(
                    "friends", com.google.firebase.firestore.FieldValue.arrayRemove(friendId)
                ).await()
                
                // Remove current user from friend's friends list
                usersCollection.document(friendId).update(
                    "friends", com.google.firebase.firestore.FieldValue.arrayRemove(userId)
                ).await()
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing friend", e)
            false
        }
    }
    
    suspend fun blockUser(userId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("blockUser") {
                usersCollection.document(currentUserId).update(
                    "blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                ).await()
                
                // Also remove from friends if they are friends
                removeFriend(userId)
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking user", e)
            false
        }
    }
    
    suspend fun unblockUser(userId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("unblockUser") {
                usersCollection.document(currentUserId).update(
                    "blockedUsers", com.google.firebase.firestore.FieldValue.arrayRemove(userId)
                ).await()
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking user", e)
            false
        }
    }
    
    suspend fun updateUserSettings(settings: com.example.chatapp.data.model.UserSettings): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("updateUserSettings") {
                usersCollection.document(userId).update("settings", settings).await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user settings", e)
            false
        }
    }
    
    suspend fun updatePublicKey(publicKey: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firebaseUtils.executeWithRetry("updatePublicKey") {
                usersCollection.document(userId).update("publicKey", publicKey).await()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating public key", e)
            false
        }
    }
    
    suspend fun getUserPublicKey(userId: String): String? {
        return try {
            val user = getUserById(userId)
            user?.publicKey
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user public key", e)
            null
        }
    }

    suspend fun getUserSettings(userId: String): com.example.chatapp.data.model.UserSettings? {
        return try {
            firebaseUtils.executeWithRetry("getUserSettings") {
                val document = usersCollection.document(userId).get().await()
                if (document.exists()) {
                    val settings = document.get("settings")
                    if (settings != null && settings is Map<*, *>) {
                        // Convert the map to UserSettings
                        return@executeWithRetry com.example.chatapp.data.model.UserSettings(
                            id = (settings["id"] as? String) ?: "",
                            userId = userId, 
                            enableEncryption = (settings["enableEncryption"] as? Boolean) ?: true,
                            notificationsEnabled = (settings["notificationsEnabled"] as? Boolean) ?: true,
                            soundEnabled = (settings["soundEnabled"] as? Boolean) ?: true,
                            vibrationEnabled = (settings["vibrationEnabled"] as? Boolean) ?: true,
                            theme = (settings["theme"] as? String) ?: "system",
                            fontSize = (settings["fontSize"] as? Long)?.toInt() ?: 14,
                            language = (settings["language"] as? String) ?: "en",
                            lastActive = (settings["lastActive"] as? Long) ?: System.currentTimeMillis(),
                            readReceipts = (settings["readReceipts"] as? Boolean) ?: true,
                            typingIndicators = (settings["typingIndicators"] as? Boolean) ?: true,
                            mediaAutoDownload = (settings["mediaAutoDownload"] as? Boolean) ?: true,
                            showOnlineStatus = (settings["showOnlineStatus"] as? Boolean) ?: true
                        )
                    }
                }
                // Return default settings if not found
                com.example.chatapp.data.model.UserSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user settings", e)
            // Return default settings on error
            com.example.chatapp.data.model.UserSettings()
        }
    }

    // Function to create test users for development purposes
    suspend fun createTestUsers(): Boolean {
        try {
            Log.d(TAG, "Starting to create test users")
            
            // Verify Firebase connection
            if (!isFirebaseInitialized()) {
                Log.e(TAG, "Firebase not properly initialized")
                return false
            }
            
            return firebaseUtils.executeWithRetry("createTestUsers") {
                Log.d(TAG, "Firebase is initialized, proceeding with test user creation")
                
                // Check if we already have users other than the current user
                val currentUserId = auth.currentUser?.uid
                val snapshot = usersCollection.limit(10).get().await()
                
                Log.d(TAG, "Retrieved ${snapshot.size()} existing users from Firestore")
                
                // Create test users with unique IDs
                val testUserIds = listOf("test_user_1", "test_user_2", "test_user_3")
                
                // Create batch operation
                val batch = firestore.batch()
                var userCount = 0
                
                testUserIds.forEach { userId ->
                    // Skip if user already exists
                    if (snapshot.documents.any { it.id == userId }) {
                        Log.d(TAG, "User $userId already exists, skipping")
                        return@forEach
                    }
                    
                    val user = User(
                        id = userId,
                        email = "$userId@example.com",
                        username = userId.replace("_", ""),
                        displayName = "Test User ${userId.last()}",
                        isOnline = true,
                        createdAt = Timestamp.now(),
                        lastActive = Timestamp.now()
                    )
                    
                    Log.d(TAG, "Adding user to batch: $userId")
                    val docRef = usersCollection.document(userId)
                    batch.set(docRef, user)
                    userCount++
                }
                
                if (userCount > 0) {
                    // Commit the batch
                    Log.d(TAG, "Committing batch with $userCount new users")
                    batch.commit().await()
                    Log.d(TAG, "Successfully created $userCount test users")
                } else {
                    Log.d(TAG, "No new users to create")
                }
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating test users: ${e.message}", e)
            return false
        }
    }
    
    private fun isFirebaseInitialized(): Boolean {
        return try {
            // Check if Firebase Auth is initialized
            FirebaseAuth.getInstance() != null &&
            // Check if Firestore is initialized
            FirebaseFirestore.getInstance() != null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization check failed: ${e.message}", e)
            false
        }
    }

    // Utility method to extract Firebase index creation URL from error message
    private fun extractFirebaseIndexUrl(errorMessage: String): String? {
        // Look for URLs in the format: https://console.firebase.google.com/project/...
        val urlRegex = "(https://console\\.firebase\\.google\\.com/[\\w\\d\\-/_?&=.]+)".toRegex()
        val matchResult = urlRegex.find(errorMessage)
        return matchResult?.groupValues?.get(1)
    }
}