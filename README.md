# MNlists

A strictly local-only, offline Android app for personal task and habit management. No accounts, no sync, no cloud, no gamification. Just a calm, modern, and soft planner designed to keep you organized.

## The Vision

MNlists is built around four core concepts, separated into a clean bottom-tab navigation:
- **Habits:** Counters that track your progress with custom up/down increments and auto-reset schedules.
- **To-Do's:** One-off tasks with optional due dates, notes, reminders, and cascading subtasks.
- **Dailies:** Recurring tasks that generate fresh instances on a schedule (Daily, Weekly, Monthly, or Every N). Skip days without breaking the series.
- **Calendar:** A built-in planner view to see your tasks and habits for any given day, preserving your history.

## Key Features
- **100% Offline & Local-First:** All data is stored securely on your device using Room (SQLite). The app does not even request the INTERNET permission.
- **Cascading Logic:** Checking off a parent task checks its subtasks; checking all subtasks completes the parent.
- **Smart Rescheduling:** Habit counters auto-reset based on your custom schedule, logging history without losing past progress.
- **System Notifications:** Standard Android local notifications for reminders (no backend required).
- **Modern UI:** Built entirely with Jetpack Compose and Material 3. Features full Light and Dark mode support with custom soft palettes.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Unidirectional Data Flow (UDF)
- **Persistence:** Room (SQLite) with proper schema migrations
- **Dependency Injection:** Hilt
- **Async:** Coroutines + Flow
- **Navigation:** Navigation-Compose
- **Minimum SDK:** 26 (Android 8.0)

## Project Status
Complete and release-ready. All features and polish steps are done, backed by 184 passing JVM/Robolectric unit and UI tests (0 failures), including WCAG AA contrast checks for both palettes. Version 1.0 (versionCode 1).

*Built as a completely native Android experience, designed for speed, reliability, and zero network dependencies.*

## Building a signed release APK

The app has **no signing configuration committed** — you create and keep your own key.

1. In Android Studio: **Build → Generate Signed App Bundle / APK…** → choose **APK** → **Create new…** key store, pick a safe path (e.g. `release/mnlists-release.jks`), set store/key passwords and an alias such as `mnlists`, fill in at least your name, and choose a validity of 25+ years. **Back up the keystore and passwords** — without them you can never publish an update to the same app.
2. Choose the **release** variant and an output folder (the wizard defaults to `app/release/`). Android Studio builds and signs the APK.
3. To repeat this from Gradle instead, add a `signingConfigs { create("release") { … } }` block to `app/build.gradle.kts` pointing at your keystore and reference it with `signingConfig = signingConfigs.getByName("release")` inside `buildTypes.release`, then run:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. Keep passwords in environment variables (or `local.properties`, which is gitignored) rather than in source control. `*.jks` / `*.keystore` are gitignored — never commit your signing key.