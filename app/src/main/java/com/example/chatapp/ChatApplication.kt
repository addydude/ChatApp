package com.example.chatapp

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class ChatApplication : Application() {

    companion object {
        private const val TAG = "ChatApplication"
    }

    // Global exception handler
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception: ${throwable.message}", throwable)
    }

    override fun onCreate() {
        // Install our custom exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            // Show more specific error message
            val errorMessage = "Error: ${throwable.message?.take(100) ?: "Unknown error"}"
            Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()
        }

        try {
            super.onCreate()
            
            // Initialize Firebase safely
            initializeFirebase()
            
            Log.d(TAG, "Application successfully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing application: ${e.message}", e)
        }
    }
    
    private fun initializeFirebase() {
        try {
            // Initialize Firebase
            if (!FirebaseApp.getApps(this).isEmpty()) {
                Log.d(TAG, "Firebase already initialized")
            } else {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase initialized successfully")
            }
            
            // Configure Firestore for offline persistence
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            
            Log.d(TAG, "Firestore configured with offline persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}", e)
        }
    }
}