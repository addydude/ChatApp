package com.example.chatapp.ui.screens.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.utils.NetworkUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
    private val networkUtils: NetworkUtils,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val TAG = "AuthViewModel"
    
    // Google Sign In with improved configuration
    val googleSignInClient: GoogleSignInClient by lazy {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("617266385341-2u4jqkrs9tu8k5gb1fero8plu3oj8fus.apps.googleusercontent.com") // Web client ID from Firebase
                .requestEmail()
                .requestProfile() // Add profile request
                .build()
            
            Log.d(TAG, "Initializing Google Sign-In client")
            GoogleSignIn.getClient(context, gso).also {
                // Check if there's an existing Google Sign-In account for silent sign in
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    Log.d(TAG, "Found existing Google account: ${account.email}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Google Sign-In: ${e.message}", e)
            throw e
        }
    }
    
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState
    
    private val _authEvents = MutableSharedFlow<AuthEvent>()
    val authEvents: SharedFlow<AuthEvent> = _authEvents
    
    private val _networkAvailable = MutableStateFlow(networkUtils.isNetworkAvailable())
    val networkAvailable: StateFlow<Boolean> = _networkAvailable
    
    init {
        // Check if user is already logged in
        _isLoggedIn.value = auth.currentUser != null
        
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            _isLoggedIn.value = firebaseAuth.currentUser != null
        }
        
        // Monitor network status
        networkUtils.observeNetworkStatus()
            .onEach { isAvailable -> 
                _networkAvailable.value = isAvailable
                if (!isAvailable) {
                    Log.w(TAG, "Network connectivity lost")
                } else {
                    Log.d(TAG, "Network connectivity restored")
                    // Optionally retry pending operations when network is restored
                }
            }
            .launchIn(viewModelScope)
    }
    
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("Email and password cannot be empty"))
            }
            return
        }
        
        if (!networkUtils.isNetworkAvailable()) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("No internet connection available"))
            }
            return
        }
        
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success
                _authEvents.emit(AuthEvent.LoggedIn)
            } catch (e: FirebaseAuthInvalidUserException) {
                _authState.value = AuthState.Error("User not found. Please register first.")
                _authEvents.emit(AuthEvent.Error("User not found. Please register first."))
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _authState.value = AuthState.Error("Invalid email or password")
                _authEvents.emit(AuthEvent.Error("Invalid email or password"))
            } catch (e: FirebaseNetworkException) {
                _authState.value = AuthState.Error("Network error. Please check your connection.")
                _authEvents.emit(AuthEvent.Error("Network error. Please check your connection."))
            } catch (e: FirebaseAuthException) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                _authEvents.emit(AuthEvent.Error(e.message ?: "Authentication failed"))
            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.message}", e)
                
                val errorMessage = if (networkUtils.isNetworkError(e)) {
                    "Network error. Please check your connection and try again."
                } else {
                    "An unexpected error occurred: ${e.message}"
                }
                
                _authState.value = AuthState.Error(errorMessage)
                _authEvents.emit(AuthEvent.Error(errorMessage))
            }
        }
    }
    
    fun register(username: String, email: String, password: String, confirmPassword: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("All fields must be filled"))
            }
            return
        }
        
        if (password != confirmPassword) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("Passwords do not match"))
            }
            return
        }
        
        if (!networkUtils.isNetworkAvailable()) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("No internet connection available"))
            }
            return
        }
        
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                // Create user with email and password
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                
                // Create user profile in Firestore
                val user = User(
                    id = result.user?.uid ?: "",
                    username = username,
                    email = email,
                    isOnline = true
                )
                
                firestore.collection("users")
                    .document(user.id)
                    .set(user)
                    .await()
                
                _authState.value = AuthState.Success
                _authEvents.emit(AuthEvent.Registered)
            } catch (e: FirebaseAuthWeakPasswordException) {
                _authState.value = AuthState.Error("Password is too weak. Use at least 6 characters.")
                _authEvents.emit(AuthEvent.Error("Password is too weak. Use at least 6 characters."))
            } catch (e: FirebaseAuthUserCollisionException) {
                _authState.value = AuthState.Error("This email is already registered. Try logging in.")
                _authEvents.emit(AuthEvent.Error("This email is already registered. Try logging in."))
            } catch (e: FirebaseNetworkException) {
                _authState.value = AuthState.Error("Network error. Please check your connection.")
                _authEvents.emit(AuthEvent.Error("Network error. Please check your connection."))
            } catch (e: FirebaseFirestoreException) {
                Log.e(TAG, "Firestore error: ${e.message}", e)
                _authState.value = AuthState.Error("Failed to create user profile. Please try again.")
                _authEvents.emit(AuthEvent.Error("Failed to create user profile. Please try again."))
            } catch (e: FirebaseAuthException) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
                _authEvents.emit(AuthEvent.Error(e.message ?: "Registration failed"))
            } catch (e: Exception) {
                Log.e(TAG, "Registration error: ${e.message}", e)
                
                val errorMessage = if (networkUtils.isNetworkError(e)) {
                    "Network error. Please check your connection and try again."
                } else {
                    "An unexpected error occurred: ${e.message}"
                }
                
                _authState.value = AuthState.Error(errorMessage)
                _authEvents.emit(AuthEvent.Error(errorMessage))
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            try {
                // Update user status to offline
                auth.currentUser?.uid?.let { userId ->
                    userRepository.updateUserStatus(false)
                }
                
                // Sign out
                auth.signOut()
                _authEvents.emit(AuthEvent.LoggedOut)
            } catch (e: Exception) {
                _authEvents.emit(AuthEvent.Error("Failed to logout"))
            }
        }
    }
    
    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("Google sign in failed"))
            }
            return
        }
        
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                
                val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false
                val currentUser = auth.currentUser
                
                if (isNewUser && currentUser != null) {
                    // Create user profile in Firestore for new users
                    val userId = currentUser.uid
                    val displayName = currentUser.displayName ?: "User"
                    val email = currentUser.email ?: ""
                    val photoUrl = currentUser.photoUrl?.toString()
                    
                    val user = User(
                        id = userId,
                        username = displayName,
                        email = email,
                        displayName = displayName,
                        photoUrl = photoUrl,
                        isOnline = true
                    )
                    
                    firestore.collection("users")
                        .document(userId)
                        .set(user)
                        .await()
                    
                    Log.d(TAG, "Created new user profile for Google Sign-In: $userId")
                }
                
                _authState.value = AuthState.Success
                _authEvents.emit(AuthEvent.LoggedIn)
                Log.d(TAG, "Google sign in successful")
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed: ${e.statusCode}", e)
                _authState.value = AuthState.Error("Google sign in failed: ${e.message}")
                _authEvents.emit(AuthEvent.Error("Google sign in failed: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Authentication failed: ${e.message}", e)
                
                val errorMessage = if (networkUtils.isNetworkError(e)) {
                    "Network error. Please check your connection and try again."
                } else {
                    "Authentication failed: ${e.message}"
                }
                
                _authState.value = AuthState.Error(errorMessage)
                _authEvents.emit(AuthEvent.Error(errorMessage))
            }
        }
    }
    
    fun processGoogleSignInActivityResult(data: android.content.Intent?) {
        try {
            if (data == null) {
                Log.e(TAG, "Google sign in data is null")
                viewModelScope.launch {
                    _authEvents.emit(AuthEvent.Error("Google sign-in failed: No data received"))
                }
                return
            }
            
            if (!networkUtils.isNetworkAvailable()) {
                viewModelScope.launch {
                    _authEvents.emit(AuthEvent.Error("No internet connection available. Please connect and try again."))
                }
                return
            }
            
            Log.d(TAG, "Processing Google Sign-In result")
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            
            if (task.isSuccessful) {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Google Sign-In successful, account ID: ${account.id}, email: ${account.email}")
                handleGoogleSignInResult(account)
            } else {
                val exception = task.exception
                Log.e(TAG, "Google sign in task unsuccessful: ${exception?.message}", exception)
                
                val errorMessage = if (networkUtils.isNetworkError(exception)) {
                    "Network error. Please check your connection and try again."
                } else {
                    "Google sign-in failed: ${exception?.message ?: "Unknown error"}"
                }
                
                viewModelScope.launch {
                    _authEvents.emit(AuthEvent.Error(errorMessage))
                }
            }
        } catch (e: ApiException) {
            // Handle specific API exceptions with detailed error messages
            val errorMsg = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in was cancelled"
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign in failed"
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Sign in already in progress"
                GoogleSignInStatusCodes.INVALID_ACCOUNT -> "Invalid account selected"
                GoogleSignInStatusCodes.SIGN_IN_REQUIRED -> "Sign in required"
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error occurred"
                else -> "Error code: ${e.statusCode}"
            }
            Log.e(TAG, "Google Sign-In API Exception: $errorMsg", e)
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error("Google sign-in failed: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in error: ${e.message}", e)
            
            val errorMessage = if (networkUtils.isNetworkError(e)) {
                "Network error during sign-in. Please check your connection and try again."
            } else {
                "Google sign-in error: ${e.message}"
            }
            
            viewModelScope.launch {
                _authEvents.emit(AuthEvent.Error(errorMessage))
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthEvent {
    object LoggedIn : AuthEvent()
    object Registered : AuthEvent()
    object LoggedOut : AuthEvent()
    data class Error(val message: String) : AuthEvent()
}