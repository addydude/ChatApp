package com.example.chatapp.di

import android.content.Context
import com.example.chatapp.utils.FirebaseUtils
import com.example.chatapp.utils.NetworkUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils {
        return NetworkUtils(context)
    }
    
    @Provides
    @Singleton
    fun provideFirebaseUtils(networkUtils: NetworkUtils): FirebaseUtils {
        return FirebaseUtils(networkUtils)
    }
}