# MNlists — ACTIVITY

## Project Status
- Current step: 2
- Last updated: 2026-11-18T20:30:00+06:00
- Overall: 2/16 steps complete

## Step Checklist
- [x] Step 0: Bootstrap activity file
- [x] Step 1: Project skeleton (Kotlin, Compose, Material 3, Hilt, Navigation-Compose, min SDK 26, single activity, 4-tab scaffold + top bar + 3-dot menu)
- [ ] Step 2: Theme system (light/dark palettes, system-follow + manual override)
- [ ] Step 3: Data layer: entities + DAOs + DB + repositories
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

## Next Up
- Step 2: Theme system — light (white + light green) and dark (midnight dusk) palettes, system-follow default, manual override selectable in Settings, applied instantly.

## Deviations & Issues
- **Step 1 was not actually complete on arrival.** Project contained only the Android Studio Views template (`activity_main.xml` + Hello World), no Kotlin source directory, no Compose/Hilt/Navigation. Skeleton was built from scratch and the unused layout deleted.
- **AGP 9.5.1 uses built-in Kotlin**: applying `org.jetbrains.kotlin.android` is rejected. Removed the plugin; Compose compiler plugin pinned to Kotlin **2.2.10** (the KGP version AGP depends on) and KSP to **2.3.14** (older KSP `2.2.10-2.0.2` fails with "Using kotlin.sourceSets DSL ... is not allowed with built-in Kotlin").
- **Material icons are not transitive from Material 3** in this Compose BOM; added explicit `androidx.compose.material:material-icons-core`.
- **Runtime launch not verified**: `assembleDebug` succeeds, but the AVD (`Medium_Phone_API_37.0`) refuses to boot — emulator error "Not enough space to create userdata partition. Available: 8494 MB, need 12288 MB". Needs free disk space or a manual run in Android Studio; nothing suggests a runtime crash, but tabs/menu have not been clicked on a device yet.
- Compiler warning fixed: `@StringRes` on a constructor `val` → changed to `@param:StringRes`.
- Open question (default proceeding): habit "start value" after reset = 0; deleting a habit keeps its history (per §9 defaults).

## Recovery Notes
- Working dir: `D:\Shadid\Pro_Pro`; SDK: `C:\Users\HP\AppData\Local\Android\Sdk` (platforms android-36.1, android-37.0; build-tools 36.0.0).
- No `java` on PATH. Always set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (OpenJDK 25) before Gradle:
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug --console=plain`
- Stack: Gradle 9.6.0, AGP 9.5.1, built-in Kotlin (KGP 2.2.10), KSP 2.3.14, Compose BOM 2026.09.00, Navigation-Compose 2.10.2, Hilt 2.60.1, Lifecycle 2.11.0, Activity-Compose 1.13.0, SDK 37, minSdk 26, namespace `com.example.mnlists`.
- Source root: `app/src/main/java/com/example/mnlists/`. Routes live in `ui/navigation/Destinations.kt`; scaffold in `ui/MnlistsApp.kt`.
- Debug build manifest includes `androidx.compose.ui.tooling.PreviewActivity` (debug-only, expected).
