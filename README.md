![License](https://img.shields.io/badge/License-MIT-blue.svg)
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

  
