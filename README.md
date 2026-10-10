# AgentPhone

## Description
Android application for connecting to AgentBridge on macOS. Provides WebSocket-based communication between phone and Mac agent, with session management, chat, approvals, and settings.

## Tech Stack
- Kotlin
- Android Gradle project
- OkHttp WebSocket
- Jetpack Compose UI

## Structure
- app/src/main/java/com/kapil/agentphone/
  - ui/ - Compose screens: MainActivity, SettingsScreen, ChatScreen, SessionsScreen, ApprovalsScreen
  - service/ - BridgeService for background WebSocket connection
  - data/ - BridgeClient, Prefs, Models

## Key Files
- app/build.gradle.kts
- build.gradle.kts
- settings.gradle.kts

## Usage
Build with Gradle. Connect to AgentBridge via host/port/token configured in Settings.
