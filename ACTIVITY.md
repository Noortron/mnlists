# MNlists — ACTIVITY

## Project Status
- Current step: 4
- Last updated: 2026-11-18T21:28:21+06:00
- Overall: 4/16 steps complete

## Step Checklist
- [x] Step 0: Bootstrap activity file
- [x] Step 1: Project skeleton (Kotlin, Compose, Material 3, Hilt, Navigation-Compose, min SDK 26, single activity, 4-tab scaffold + top bar + 3-dot menu)
- [x] Step 2: Theme system (light/dark palettes, system-follow + manual override)
- [x] Step 3: Data layer: entities + DAOs + DB + repositories
- [ ] Step 4: Habits: list + create/edit + counter
- [ ] Step 5: Habits: auto-reset + history
- [ ] Step 6: To-Do's: list + create/edit
- [ ] Step 7: Subtasks (To-Do's)
- [ ] Step 8: Dailies: model + occurrence generation
- [ ] Step 9: Dailies UI + subtasks + skip
- [ ] Step 10: Reordering & drag handle everywhere
- [ ] Step 11: Reminders & notifications
- [ ] Step 12: Calendar UI + day list
- [ ] Step 13: Settings & About pages
- [ ] Step 14: Polish pass
- [ ] Step 15: Final QA + release prep

## Done Log
### Step 0 — Bootstrap activity file — 2026-11-18T19:53:48+06:00
- What was done: Read SPEC.md in full. `ACTIVITY.md` did not exist, so created it from the §0.2 template with all 16 steps (0–15) from §12 listed.
- Files touched: `ACTIVITY.md` (created)
- Notes / decisions: Checklist covers Step 0 through Step 15 inclusive → 16 steps total.

### Step 1 — Project skeleton — 2026-11-18T20:30:00+06:00
- What was done: Verified the "manually completed in Android Studio" project — it was still the empty Views "Hello World" template (no Kotlin sources, no Compose/Hilt/Navigation), so the skeleton was implemented for real: Kotlin + Compose + Material 3 + Hilt + Navigation-Compose, min SDK 26, target/compile SDK 37, single `MainActivity`, 4-tab bottom bar (Habits / Dailies / To-Do's / Calendar), top bar with 3-dot overflow → Settings/About placeholder screens (with back navigation), context-aware FAB hidden on Calendar. `.\gradlew :app:assembleDebug` → BUILD SUCCESSFUL; APK produced (~12.4 MB). No INTERNET permission in the merged manifest.
- Files touched:
  - Build: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
  - Manifest/resources: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`; deleted unused `app/src/main/res/layout/activity_main.xml`
  - Kotlin: `MnlistsApplication.kt`, `MainActivity.kt`, `ui/MnlistsApp.kt`, `ui/navigation/Destinations.kt`, `ui/components/PlaceholderContent.kt`, `ui/theme/{Color,Theme,Type}.kt`, `ui/screens/{Habits,Dailies,Todos,Calendar,Settings,About}Screen.kt`
- Notes / decisions: Step 1 was marked "manually completed in Android Studio" by the user, but verification failed and the step was executed here. Theme colors are a deliberate baseline only (Step 2 owns the real palettes + override). FAB `onClick` is a no-op placeholder until Steps 4/6/9. Removed appcompat/material/constraintlayout deps since UI is 100% Compose.

### Step 2 — Theme system — 2026-11-18T20:49:07+06:00
- What was done: Added the real light/dark palettes and a persisted theme override.
  - Light: `#FFFFFF` background, light green primary `#7CB342` with dark ink `onPrimary` for AA contrast, soft green containers, neutral green-tinted surfaces.
  - Dark ("midnight dusk"): `#0E1120` background, dusk-blue accent `#9DB2FF`, layered dusk surfaces (`#141829`…`#252B41`).
  - Full Material 3 `ColorScheme` (incl. `surfaceContainer*`, inverse + error tokens), custom `Shapes` (8/12/14/16/20dp; cards & rows 12–16dp), typography unchanged from Step 1.
  - `ThemeMode` enum (`SYSTEM` default / `LIGHT` / `DARK`) persisted in DataStore Preferences via `SettingsRepository` (Flow), exposed by `SettingsViewModel` (`StateFlow`), collected in `MainActivity` above the nav host → palette switches instantly, no app restart.
  - Settings page now has a real "Appearance" section: rounded, selectable radio rows (System default / Light / Dark) with descriptions, 56dp min row height, `selectableGroup` + `Role.RadioButton` semantics.
  - Status/navigation bar icon appearance follows the effective theme (handles the case where the in-app override disagrees with the device night mode).
- Files touched:
  - New: `data/settings/ThemeMode.kt`, `data/settings/SettingsRepository.kt`, `ui/settings/SettingsViewModel.kt`, `ui/theme/Shape.kt`
  - Edited: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/screens/SettingsScreen.kt`, `ui/MnlistsApp.kt` (theme params + `surfaceContainer` nav bar), `MainActivity.kt` (theme source of truth), `res/values/strings.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Notes / decisions: `assembleDebug` → BUILD SUCCESSFUL with no Kotlin warnings (also fixed the deprecated `hiltViewModel` import → `androidx.hilt.lifecycle.viewmodel.compose` and made the `@ApplicationContext` target explicit as `@param:`). No network/analytics touched. New dependency: `androidx.datastore:datastore-preferences:1.3.0` (local file storage only).
- **Step 1 runtime status: device-verified by the user** (tabs/nav/menu confirmed working on a device); the earlier "runtime not verified" caveat is resolved.

### Step 3 — Data layer: entities + DAOs + DB + repositories — 2026-11-18T21:28:21+06:00
- What was done: Full Room data layer per SPEC §7, no UI.
  - 7 entities in `data/local/entity`: `Habit`, `HabitLog`, `Todo`, `Daily`, `DailyOccurrence`, `Subtask`, `Reminder` (fields per §7; `sortOrder` as `Double` for cheap reordering).
  - Enums in `data/model/Enums.kt`: `ResetSchedule`, `RepeatType`, `ParentType`, `ReminderParentType`; `Converters` store enums by name, `DayOfWeek` as ISO int, weekday sets as sorted CSV.
  - 7 DAOs with Flow observation queries + suspend writes; DAO-level helpers for cascade resets, max sort order, unique-occurrence inserts (`OnConflictStrategy.IGNORE`), history range reads, reminder id allocation.
  - `MnlistsDatabase` (version 1, `exportSchema = true`, no destructive fallback) + `di/DatabaseModule` providing the DB and every DAO via Hilt.
  - Repositories: `HabitRepository` (CRUD, allow-direction-aware `adjustCount`, log append), `TodoRepository` (delete cascade to subtasks/reminders, §8 two-way parent↔subtask cascade in transactions), `DailyRepository` (series CRUD, delete-series keeps past occurrences but drops future ones + reminders, idempotent `ensureOccurrence`, skip, occurrence↔subtask cascade), `ReminderRepository` (CRUD, due query, unique notification ids).
  - Dates are stored as epoch **days** (`Long`) and timestamps as epoch **millis**; documented in entity KDoc.
- **Smoke test result (explicit):** `:app:testDebugUnitTest --tests "com.example.mnlists.data.local.DaoSmokeTest"` → **BUILD SUCCESSFUL**; JUnit report `app/build/test-results/testDebugUnitTest/TEST-…DaoSmokeTest.xml` = **tests=5, failures=0, errors=0, skipped=0** (total 41.5 s; suite timestamp 2026-11-18T15:25:10Z). Passing cases: `habit_createReadUpdateCount_andConvertersRoundTrip`, `habitLog_insertAndReadHistory`, `todo_withSubtasks_andCompletionCascadePrimitives`, `dailyOccurrence_uniquePerDate_andSkipFlag`, `reminder_insertReadAndDeleteForParent`. Runs on the JVM via Robolectric (in-memory Room DB), no device required.
- Also verified: `:app:assembleDebug` → BUILD SUCCESSFUL; schema exported to `app/schemas/com.example.mnlists.data.local.MnlistsDatabase/1.json` containing all 7 tables.
- Files touched:
  - New: `data/model/Enums.kt`, `data/local/Converters.kt`, `data/local/MnlistsDatabase.kt`, `data/local/entity/{Habit,HabitLog,Todo,Daily,DailyOccurrence,Subtask,Reminder}.kt`, `data/local/dao/{Habit,HabitLog,Todo,Daily,DailyOccurrence,Subtask,Reminder}Dao.kt`, `data/repository/{Habit,Todo,Daily,Reminder}Repository.kt`, `di/DatabaseModule.kt`, `app/src/test/java/com/example/mnlists/data/local/DaoSmokeTest.kt`
  - Edited: `gradle/libs.versions.toml` (room 2.8.5, robolectric 4.18.1), `app/build.gradle.kts` (Room + KSP schema args + test deps)
- Notes / decisions: entities double as domain models (no extra mapping layer) — revisit only if a UI need appears. `HabitLog` has **no FK** and `DailyOccurrence` has **no FK** on purpose, so history survives habit/series deletion (§9). `Todo` keeps both `isCompleted` and `completedAt` (single source of truth = `isCompleted`). Occurrence generation logic itself is Step 8, but `ensureOccurrence`/`latestOccurrenceDate` primitives are in place.

## Next Up
- Step 4: Habits UI — list rows (title, note preview, count, −/+ per enabled), FAB → create, tap title → edit page with note emphasized, delete with confirm; persistence across restart.

## Deviations & Issues
- **Step 1 was not actually complete on arrival.** Project contained only the Android Studio Views template (`activity_main.xml` + Hello World), no Kotlin source directory, no Compose/Hilt/Navigation. Skeleton was built from scratch and the unused layout deleted.
- **AGP 9.5.1 uses built-in Kotlin**: applying `org.jetbrains.kotlin.android` is rejected. Removed the plugin; Compose compiler plugin pinned to Kotlin **2.2.10** (the KGP version AGP depends on) and KSP to **2.3.14** (older KSP `2.2.10-2.0.2` fails with "Using kotlin.sourceSets DSL ... is not allowed with built-in Kotlin").
- **Material icons are not transitive from Material 3** in this Compose BOM; added explicit `androidx.compose.material:material-icons-core`.
- **Step 1 runtime — resolved**: the AVD could not boot here ("Not enough space to create userdata partition", needs 12 GB), but the user device-verified Step 1 (tabs navigate, menu opens placeholders). Steps 2+ still need on-device verification by the user.
- Compiler warning fixed: `@StringRes` on a constructor `val` → changed to `@param:StringRes`.
- **Robolectric added (test-only)** so the Room smoke test can run on the JVM — the AVD still cannot boot here (disk space), and an emulator run would not be repeatable. Robolectric downloads `android-all` jars on first run; test JVM warnings about native access are benign.
- **Room schema location** is `app/schemas` (KSP arg `room.schemaLocation`); schema v1 is committed. Future entity changes need real migrations (no destructive fallback by design).
- Open question (default proceeding): habit "start value" after reset = 0; deleting a habit keeps its history (per §9 defaults).

## Recovery Notes
- Working dir: `D:\Shadid\Pro_Pro`; SDK: `C:\Users\HP\AppData\Local\Android\Sdk` (platforms android-36.1, android-37.0; build-tools 36.0.0).
- No `java` on PATH. Always set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (OpenJDK 25) before Gradle:
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug --console=plain`
- Stack: Gradle 9.6.0, AGP 9.5.1, built-in Kotlin (KGP 2.2.10), KSP 2.3.14, Compose BOM 2026.09.00, Navigation-Compose 2.10.2, Hilt 2.60.1, Lifecycle 2.11.0, Activity-Compose 1.13.0, DataStore Preferences 1.3.0, SDK 37, minSdk 26, namespace `com.example.mnlists`.
- Source root: `app/src/main/java/com/example/mnlists/`. Routes in `ui/navigation/Destinations.kt`; scaffold in `ui/MnlistsApp.kt`.
- Theme: palettes/tokens in `ui/theme/Color.kt`, scheme + system-bar handling in `ui/theme/Theme.kt`, rounding in `ui/theme/Shape.kt`. Preference: `data/settings/ThemeMode.kt` + `SettingsRepository` (DataStore file `settings.preferences_pb`), exposed via `ui/settings/SettingsViewModel`, applied in `MainActivity`.
- Data layer: DB name `mnlists.db`, entities/DAOs in `data/local/`, repositories in `data/repository/`, Hilt providers in `di/DatabaseModule.kt`. Room schema JSON: `app/schemas/com.example.mnlists.data.local.MnlistsDatabase/1.json` (version 1).
- Run tests: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest` (first Robolectric run downloads jars and takes several minutes).
- Debug build manifest includes `androidx.compose.ui.tooling.PreviewActivity` (debug-only, expected).
