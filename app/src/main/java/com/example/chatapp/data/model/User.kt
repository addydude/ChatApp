package com.example.chatapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Represents a user in the chat application
 */
data class User(
    @DocumentId
    val id: String = "",
    
    var email: String = "",
    
    val username: String = "",
    
    @get:PropertyName("display_name")
    @set:PropertyName("display_name") 
    var displayName: String = "",
    
    @get:PropertyName("photo_url")
    @set:PropertyName("photo_url")
    var photoUrl: String? = null,
    
    var bio: String = "",
    
    var phone: String? = null,
    
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now(),
    
    @get:PropertyName("last_active")
    @set:PropertyName("last_active")
    var lastActive: Timestamp = Timestamp.now(),
    
    @get:PropertyName("is_online")
    @set:PropertyName("is_online")
    var isOnline: Boolean = false,
    
    @get:PropertyName("fcm_token")
    @set:PropertyName("fcm_token")
    var fcmToken: String? = null,
    
    @get:PropertyName("public_key")
    @set:PropertyName("public_key")
    var publicKey: String? = null,
    
    // Status message like "Busy", "Available", etc.
    var status: String? = null,
    
    // User preference settings
    @get:PropertyName("notification_enabled")
    @set:PropertyName("notification_enabled")
    var notificationEnabled: Boolean = true,
    
    @get:PropertyName("theme_preference")
    @set:PropertyName("theme_preference")
    var themePreference: String = "system", // "light", "dark", or "system"
    
    // For friend list/social features
    var contacts: List<String> = emptyList(), // User IDs of contacts/friends
    
    // Privacy settings
    @get:PropertyName("privacy_settings")
    @set:PropertyName("privacy_settings")
    var privacySettings: PrivacySettings = PrivacySettings(),
    
    @get:PropertyName("blocked_users")
    @set:PropertyName("blocked_users")
    var blockedUsers: List<String> = emptyList(),
    
    var metadata: Map<String, Any> = emptyMap() // For extensibility
) {
    // Helper methods
    
    fun getInitials(): String {
        return if (displayName.isNotEmpty()) {
            displayName.split(" ")
                .take(2)
                .joinToString("") { it.firstOrNull()?.toString() ?: "" }
                .uppercase()
        } else {
            "?"
        }
    }
    
    fun getActivityStatus(): String {
        return if (isOnline) {
            "Online"
        } else {
            "Last seen " + getTimeSinceLastActive()
        }
    }
    
    @Exclude
    private fun getTimeSinceLastActive(): String {
        // Calculate the time difference between now and lastActive
        val now = Timestamp.now()
        val diffSeconds = (now.seconds - lastActive.seconds).toInt()
        
        return when {
            diffSeconds < 60 -> "just now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
            diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
            else -> "${diffSeconds / 86400}d ago"
        }
    }
    
    fun isContact(userId: String): Boolean {
        return contacts.contains(userId)
    }
}

data class PrivacySettings(
    @get:PropertyName("show_last_seen")
    @set:PropertyName("show_last_seen")
    var showLastSeen: String = "everyone", // "everyone", "contacts", "nobody"
    
    @get:PropertyName("show_profile_photo")
    @set:PropertyName("show_profile_photo")
    var showProfilePhoto: String = "everyone", // "everyone", "contacts", "nobody"
    
    @get:PropertyName("show_status")
    @set:PropertyName("show_status")
    var showStatus: String = "everyone", // "everyone", "contacts", "nobody"
    
    @get:PropertyName("read_receipts")
    @set:PropertyName("read_receipts")
    var readReceipts: Boolean = true
)