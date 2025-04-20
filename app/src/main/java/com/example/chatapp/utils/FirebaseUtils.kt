package com.example.chatapp.utils

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUtils @Inject constructor(
    private val networkUtils: NetworkUtils
) {
    private val TAG = "FirebaseUtils"
    
    /**
     * Executes a Firebase operation with retries and proper error handling
     * 
     * @param operationName A description of the operation for logging
     * @param timeoutMs Timeout in milliseconds
     * @param maxRetries Maximum number of retries to attempt
     * @param operation The suspending operation to execute
     * 
     * @return Result of the operation
     * @throws Exception if all retries fail
     */
    suspend fun <T> executeWithRetry(
        operationName: String,
        timeoutMs: Long = 15000,
        maxRetries: Int = 3,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var retryCount = 0
        
        while (retryCount <= maxRetries) {
            try {
                if (retryCount > 0) {
                    Log.d(TAG, "Retrying $operationName (attempt ${retryCount}/${maxRetries})")
                }
                
                // Check for network before attempting operation
                if (!networkUtils.isNetworkAvailable()) {
                    throw FirebaseNetworkException("No network available")
                }
                
                // Execute with timeout to prevent hanging operations
                return withTimeout(timeoutMs) {
                    withContext(Dispatchers.IO) {
                        operation()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "$operationName timed out after ${timeoutMs}ms")
                lastException = e
            } catch (e: FirebaseNetworkException) {
                Log.w(TAG, "$operationName failed due to network error: ${e.message}")
                lastException = e
            } catch (e: Exception) {
                // For other exceptions, only retry if it seems network-related
                if (networkUtils.isNetworkError(e)) {
                    Log.w(TAG, "$operationName failed with likely network error: ${e.message}")
                    lastException = e
                } else {
                    // For non-network errors, don't retry, just throw
                    Log.e(TAG, "$operationName failed with non-network error", e)
                    throw e
                }
            }
            
            retryCount++
            
            // Small delay before retry - exponential backoff
            if (retryCount <= maxRetries) {
                val delayMs = 500L * (1 shl (retryCount - 1)) // 500, 1000, 2000, etc.
                withContext(Dispatchers.IO) {
                    Thread.sleep(delayMs)
                }
            }
        }
        
        // If we get here, all retries failed
        Log.e(TAG, "$operationName failed after $maxRetries retries")
        throw lastException ?: Exception("Operation $operationName failed after $maxRetries retries")
    }
}