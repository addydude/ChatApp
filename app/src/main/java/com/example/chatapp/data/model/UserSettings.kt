package com.example.chatapp.data.model

/**
 * Represents user settings and preferences
 */
data class UserSettings(
    val id: String = "",
    val userId: String = "",
    val enableEncryption: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val theme: String = "system", // system, light, dark
    val fontSize: Int = 14,
    val language: String = "en",
    val lastActive: Long = System.currentTimeMillis(),
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val mediaAutoDownload: Boolean = true,
    val showOnlineStatus: Boolean = true
)