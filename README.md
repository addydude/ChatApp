# Android Chat Application

A modern Android chat application built with Jetpack Compose and Firebase.

## Features

- **User Authentication**: Email/password and Google Sign-In
- **Real-time Messaging**: Private and group chats with real-time updates
- **User Profiles**: Customizable user profiles with status indicators
- **Friend Management**: Add/remove friends and block users
- **Message Encryption**: End-to-end encryption support for secure communication
- **Push Notifications**: Real-time notifications for new messages
- **Media Sharing**: Support for sharing images and other media files
- **Offline Support**: Access to chat history when offline

## Tech Stack

- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt
- **Backend**: Firebase (Authentication, Firestore, Storage)
- **Networking**: Resilient network handling with retry mechanisms
- **Async Image Loading**: Coil
- **Coroutines & Flow**: For asynchronous operations
- **Encryption**: Google Tink for E2E encryption

## Screenshots

*[Screenshots to be added]*

## Setup Instructions

1. Clone the repository
2. Set up Firebase project and download `google-services.json`
3. Place the `google-services.json` file in the app directory
4. Run the application on Android Studio

## License

[MIT License](LICENSE)