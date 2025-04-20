package com.example.chatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chatapp.ui.screens.auth.AuthViewModel
import com.example.chatapp.ui.screens.auth.LoginScreen
import com.example.chatapp.ui.screens.auth.RegisterScreen
import com.example.chatapp.ui.screens.chat.ChatDetailScreen
import com.example.chatapp.ui.screens.chat.ChatViewModel
import com.example.chatapp.ui.screens.chat.ThreadScreen
import com.example.chatapp.ui.screens.home.HomeScreen
import com.example.chatapp.ui.screens.profile.ProfileScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    object Thread : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
    object Profile : Screen("profile/{userId}") {
        fun createRoute(userId: String) = "profile/$userId"
    }
}

@Composable
fun ChatAppNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = { navController.navigate(Screen.Home.route) { popUpTo(0) } },
                viewModel = authViewModel
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                onRegisterSuccess = { navController.navigate(Screen.Home.route) { popUpTo(0) } },
                viewModel = authViewModel
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToChat = { chatId -> navController.navigate(Screen.Chat.createRoute(chatId)) },
                onNavigateToProfile = { userId -> navController.navigate(Screen.Profile.createRoute(userId)) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) { popUpTo(0) }
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val chatViewModel: ChatViewModel = hiltViewModel()
            
            ChatDetailScreen(
                chatId = chatId,
                onNavigateToThread = { threadId -> navController.navigate(Screen.Thread.createRoute(threadId)) },
                onNavigateToProfile = { userId -> navController.navigate(Screen.Profile.createRoute(userId)) },
                onNavigateBack = { navController.popBackStack() },
                viewModel = chatViewModel
            )
        }
        
        composable(
            route = Screen.Thread.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            val chatViewModel: ChatViewModel = hiltViewModel()
            
            ThreadScreen(
                threadId = threadId,
                onNavigateToProfile = { userId -> navController.navigate(Screen.Profile.createRoute(userId)) },
                onNavigateBack = { navController.popBackStack() },
                viewModel = chatViewModel
            )
        }
        
        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            
            ProfileScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { chatId -> navController.navigate(Screen.Chat.createRoute(chatId)) }
            )
        }
    }
}