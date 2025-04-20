package com.example.chatapp.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.chatapp.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val RSA_MODE = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_ALIAS = "ChatAppKeyPair"
        private const val KEY_SIZE = 2048
        private const val AES_KEY_SIZE = 256
    }
    
    private val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
        load(null)
    }
    
    private val userKeysCollection = firestore.collection("user_keys")
    
    // Generate a new RSA key pair for the user if one doesn't exist
    fun generateKeyPair(): Pair<String, String> {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, KEY_STORE_PROVIDER
                )
                
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).apply {
                    setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    setKeySize(KEY_SIZE)
                }.build()
                
                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
            }
            
            // Extract the public key for sharing
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            val publicKeyString = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            
            // For the private key, we'll just return the key alias as we never expose it
            return Pair(KEY_ALIAS, publicKeyString)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate RSA key pair", e)
        }
    }
    
    // Generate a random AES key for symmetric encryption
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
    
    // Encrypt a message with AES (symmetric encryption)
    private fun encryptWithAES(message: String, secretKey: SecretKey): String {
        try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            
            val encryptedBytes = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Error encrypting message with AES", e)
        }
    }
    
    // Decrypt a message with AES (symmetric decryption)
    private fun decryptWithAES(encryptedMessage: String, secretKey: SecretKey): String {
        try {
            val encryptedData = Base64.decode(encryptedMessage, Base64.NO_WRAP)
            
            // Extract IV
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(encryptedData, 0, iv, 0, iv.size)
            
            // Extract encrypted bytes
            val encryptedBytes = ByteArray(encryptedData.size - iv.size)
            System.arraycopy(encryptedData, iv.size, encryptedBytes, 0, encryptedBytes.size)
            
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("Error decrypting message with AES", e)
        }
    }
    
    // Encrypt AES key with RSA public key (for the recipient)
    fun encryptAESKey(aesKey: SecretKey, recipientPublicKeyString: String): String {
        try {
            val publicKeyBytes = Base64.decode(recipientPublicKeyString, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            
            val cipher = Cipher.getInstance(RSA_MODE)
            val spec = OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)
            
            val encryptedKeyBytes = cipher.doFinal(aesKey.encoded)
            return Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Error encrypting AES key with RSA", e)
        }
    }
    
    // Decrypt AES key with RSA private key (user's own key)
    fun decryptAESKey(encryptedAESKey: String): SecretKey {
        try {
            val encryptedKeyBytes = Base64.decode(encryptedAESKey, Base64.NO_WRAP)
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            
            val cipher = Cipher.getInstance(RSA_MODE)
            val spec = OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT
            )
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)
            
            val keyBytes = cipher.doFinal(encryptedKeyBytes)
            return SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
        } catch (e: Exception) {
            throw RuntimeException("Error decrypting AES key with RSA", e)
        }
    }
    
    // High-level method to encrypt a message for a recipient
    fun encryptMessage(message: String, recipientPublicKey: String): EncryptedMessage {
        val aesKey = generateAESKey()
        val encryptedContent = encryptWithAES(message, aesKey)
        val encryptedKey = encryptAESKey(aesKey, recipientPublicKey)
        
        return EncryptedMessage(encryptedContent, encryptedKey)
    }
    
    // High-level method to decrypt a message received
    fun decryptMessage(encryptedMessage: EncryptedMessage): String {
        val aesKey = decryptAESKey(encryptedMessage.encryptedKey)
        return decryptWithAES(encryptedMessage.encryptedContent, aesKey)
    }
    
    // Import an external RSA public key
    fun importPublicKey(publicKeyString: String): PublicKey {
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        val publicKeyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        return keyFactory.generatePublic(keySpec)
    }
    
    // Data class to hold encrypted message components
    data class EncryptedMessage(
        val encryptedContent: String,
        val encryptedKey: String
    )
    
    // Get the public key of a specific user from Firestore
    suspend fun getUserPublicKey(userId: String): PublicKey? {
        val document = userKeysCollection.document(userId).get().await()
        
        if (document.exists()) {
            val publicKeyString = document.getString("public_key")
            if (publicKeyString != null) {
                val publicKeyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
                val keySpec = X509EncodedKeySpec(publicKeyBytes)
                val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
                return keyFactory.generatePublic(keySpec)
            }
        }
        
        return null
    }
    
    // Encrypt a message for multiple recipients
    suspend fun encryptMessageForParticipants(message: Message, participantIds: List<String>): Message {
        if (participantIds.isEmpty()) {
            return message
        }
        
        // Generate a random AES key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(AES_KEY_SIZE)
        val secretKey = keyGenerator.generateKey()
        
        // Encrypt the message content with AES key
        val encryptedContent = encryptWithAES(message.content, secretKey)
        
        // Encrypt the AES key with each participant's public key
        val encryptedKeys = mutableMapOf<String, String>()
        
        for (participantId in participantIds) {
            val publicKey = getUserPublicKey(participantId)
            if (publicKey != null) {
                val encryptedKey = encryptAESKey(secretKey, Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))
                encryptedKeys[participantId] = encryptedKey
            }
        }
        
        // Return new message with encrypted content and keys
        return message.copy(
            content = encryptedContent,
            isEncrypted = true,
            metadata = mapOf("encrypted_keys" to encryptedKeys)
        )
    }
    
    // Decrypt a message for the current user
    suspend fun decryptMessage(message: Message): Message {
        if (!message.isEncrypted) {
            return message
        }
        
        val currentUserId = auth.currentUser?.uid ?: return message
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        
        // Get the encrypted keys from metadata
        @Suppress("UNCHECKED_CAST")
        val encryptedKeys = message.metadata["encrypted_keys"] as? Map<String, String> ?: return message
        
        // Get the encrypted key for the current user
        val encryptedKeyBase64 = encryptedKeys[currentUserId] ?: return message
        val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
        
        // Decrypt the AES key with the user's private key
        val decryptedKeyBytes = decryptAESKey(Base64.encodeToString(encryptedKey, Base64.NO_WRAP)).encoded
        val secretKey = SecretKeySpec(decryptedKeyBytes, KeyProperties.KEY_ALGORITHM_AES)
        
        // Decrypt the message content with the AES key
        val encryptedContent = Base64.decode(message.content, Base64.NO_WRAP)
        val decryptedContent = decryptWithAES(Base64.encodeToString(encryptedContent, Base64.NO_WRAP), secretKey)
        
        // Return the decrypted message
        return message.copy(
            content = decryptedContent,
            isEncrypted = true // Keep this flag as true to indicate it was originally encrypted
        )
    }
}