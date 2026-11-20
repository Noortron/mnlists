# SPEC.md — MNlists Android App

> **READ THIS FIRST — MANDATORY OPERATING RULES FOR THE AI**
>
> 1. **Before doing anything**, read this entire `SPEC.md`.
> 2. Then read `ACTIVITY.md` in the project root.
>    - If it **does not exist**, create it using the template in **§0.2** and mark all steps as `TODO`.
>    - If it **exists**, read it to find the **next unfinished step**.
> 3. Perform **only the next unfinished step** from §12 (Build Steps). Do not skip ahead. Do not bundle steps.
> 4. **After completing the step**, update `ACTIVITY.md` (§0.3 rules) *before* responding.
> 5. Respond with a short summary: what you did, what's next, and any deviations/mistakes flagged.
> 6. **Stop.** Wait for the user's next instruction.
>
> **Never edit `SPEC.md`.** It is the stable source of truth. All progress goes in `ACTIVITY.md`.
> If a step is too large for your context window, split it into sub-steps inside `ACTIVITY.md` (e.g., `Step 7a`, `Step 7b`) and complete them one at a time.

---

## 0. Project Management Files

### 0.1 File Roles

| File | Role | Who writes it |
|---|---|---|
| `SPEC.md` | Stable blueprint: requirements, data model, step list, acceptance criteria | Human (once). AI never edits. |
| `ACTIVITY.md` | Progress ledger: checklist, done/next, deviations, recovery notes | AI, after every step |

### 0.2 `ACTIVITY.md` Template (create this in Step 0 if missing)

```markdown
# MNlists — ACTIVITY

## Project Status
- Current step: <n>
- Last updated: <ISO timestamp>
- Overall: <x/y steps complete>

## Step Checklist
- [ ] Step 0: ...
- [ ] Step 1: ...
(one line per step from SPEC §12)

## Done Log
### Step <n> — <title> — <timestamp>
- What was done:
- Files touched:
- Notes / decisions:

## Next Up
- Step <n+1>: <title> — <one-line scope>

## Deviations & Issues
- (list any mistakes, bugs, workarounds, or open questions here)

## Recovery Notes
- (anything a fresh session needs to know: paths, config, gotchas)
```

### 0.3 Update Rules (after every step)
- Tick the completed step in **Step Checklist**.
- Append an entry to **Done Log** (what, where, notes).
- Rewrite **Next Up** to point at the next step.
- Add anything suspicious to **Deviations & Issues**.
- Update **Project Status** (current step, timestamp, progress).
- Keep `ACTIVITY.md` under ~200 lines by summarizing old entries if needed.

---

## 1. Product Summary

**MNlists** is a strictly **local-only, offline Android app** for personal task and habit management. Four bottom tabs: **Habits**, **Dailies**, **To-Do's**, **Calendar**. No accounts, no sync, no backend, no gamification. All data lives on-device (Room/SQLite). The app is a calm, soft, modern planner — visual references: Mihon (clean typography/spacing) and Habitica (structure only, **no gamification**).

---

## 2. Core Concepts

- **Habit** — a counter (up and/or down) with a user-defined auto-reset schedule. Tracks a running count per reset-cycle, not per day.
- **To-Do** — a one-off checkbox task. Mandatory due date. Optional note, subtasks, reminders.
- **Daily** — a recurring to-do generated per occurrence from a schedule. Each occurrence is a fresh instance. Supports skip.
- **Calendar** — planner view; top half = month calendar, bottom = tasks/habits for the selected date. Check/uncheck and habit +/− allowed; no other manipulation.
- **Subtasks** — children of To-Dos and Dailies. Each has checkbox, note, own deadline. Cascade rules below.

---

## 3. Tabs & Navigation

- **Bottom tab bar**: Habits | Dailies | To-Do's | Calendar.
- **Top bar**: title of current tab + a single **three-dot overflow menu** → **Settings**, **About**.
- **FAB** (floating add button): fixed bottom-right, just above the tab bar. Context-aware (adds Habit / Daily / To-Do depending on active tab). **Hidden on Calendar tab.**
- Lists are displayed as **rows**, not blocks.

---

## 4. Feature Specifications

### 4.1 Habits

**Fields (create/edit):** Title, Note, Reset counter schedule (auto-reset; user controls timing — e.g., daily/weekly/monthly/every N units), +/− choice (enable up, down, or both).

**Row display:** Title, truncated note preview underneath, current count, − and/or + buttons per enabled choice.

**Behavior:**
- Counter increments/decrements by tapping +/− (only enabled directions shown).
- Auto-resets on the configured schedule. Reset affects the **current counter only**; history is preserved.
- **Habits do not collapse.** Note preview always visible (truncated).
- **Tap title** → opens edit page, with the note field emphasized/visible in view.
- No subtasks.
- Deletion supported (confirm dialog).

### 4.2 To-Do's

**Fields (create/edit):** Title, Note, Due date/deadline (**mandatory**), Reminders (multiple), Subtasks (multiple; each: title, note, checkbox, own deadline).

**Subtasks:**
- Each has its own checkbox + note + deadline.
- **All subtasks checked → parent auto-checks.**
- **Parent checked → all subtasks auto-check.**
- Unchecking a parent unchecks all subtasks (inverse of cascade; needed for consistency — see §9).
- Subtask deadlines visible **only within the parent**, not in Calendar.
- When a subtask is checked, it goes gray + strikethrough **in place** (does not move).

**Row behavior:**
- Tap **title** → edit page.
- Tap **drag handle** → collapse/expand subtasks (Dailies/To-Do's **expanded by default**; Habits don't collapse).
- **Long-press drag handle** → drag to reorder within list.
- Completed tasks: gray + strikethrough, **moved to a separate "Completed" section at the bottom** of the list.

**Deletion:** supported with confirmation. Deleting a parent cascades (with confirm) to its subtasks.

### 4.3 Dailies

**Fields (create/edit):** Title, Notes, Subtasks (same behavior as To-Do's), Reminders (multiple), Scheduling:
- Start date
- Repeats: Daily / Weekly / Monthly
- "Every: N unit(s)"
- Weekly also has a custom day picker.

**Behavior:**
- Each occurrence generates a **fresh instance** for that date. Checking one day does not affect others.
- **Skip** option (Dailies only) — skips one occurrence without deleting the series.
- Editing: **one occurrence edit vs. whole-series edit must be decided per edit** (see §9 open decisions — spec selects: editing from the list edits the **series**; editing from Calendar edits **that occurrence only**).
- Completion, collapse, drag, deletion behave as in To-Do's, with cascade confirmation for series deletion.

### 4.4 Calendar

**Layout:** Top half = month calendar. Bottom half = list of tasks/habits for the selected date.

- Habits **included**.
- For tasks: **check/uncheck only** (reflects everywhere — single source of truth). No other manipulation.
- For habits: show **that day's count** with **+/−** (equivalent to check-off).
- Navigate/select any date; completed and incomplete are both shown as **history** on past dates.
- Completed items still render as gray + strikethrough in the day list.
- FAB hidden here.
- **Default State:** When the Calendar tab is opened, it must automatically default to and highlight **today's date**, displaying today's tasks and habits immediately without requiring the user to tap or navigate first.

### 4.5 Reminders & Notifications

- Android **standard local notifications** via `AlarmManager` / `WorkManager` + notification channel.
- Multiple reminders per To-Do/Daily.
- Permission requested at appropriate time (`POST_NOTIFICATIONS` on Android 13+).
- Reschedule on boot (`BOOT_COMPLETED`) and on device timezone changes.

### 4.6 Completion Display (global)
- Completed task → gray text + strikethrough → moved to **"Completed"** section at bottom of its list.
- Completed subtask → gray + strikethrough, stays in place.

### 4.7 Deletion (global)
- All entities (Habits, To-Do's, Dailies, Subtasks, individual Daily occurrences, reminders) are deletable.
- Parent deletion → confirmation dialog → cascade to children.
- Deleting a Daily series deletes future occurrences; history (past completed) preserved per §9 decision.

### 4.8 Settings & About
- **Settings**: Theme (Light / Dark / System), default reset schedule for new habits (optional), notification permission status.
- **About**: app name, version, short description, credits.

### 4.9 Import / Export (Backup & Restore)

- **Export:** User can export their entire database (Habits, To-Dos, Dailies, Subtasks, Reminders, History) to a single JSON file. This file is saved to the device's public Downloads folder (or via the Android Share Sheet).
- **Import:** User can select a previously exported JSON file. The app parses it and completely replaces (or merges into) the current local database.
- **UI Location:** Add "Backup & Restore" rows in the **Settings** screen (§4.8).
- **Edge Cases:** 
  - Importing invalid/corrupted JSON must show an error toast/snackbar and leave the existing database untouched.
  - Merging should handle ID collisions safely (e.g., generating new IDs if merging, or strictly overwriting if replacing).

---

## 5. Visual & UX Requirements

- **Themes**: Light mode and Dark mode both mandatory. Follow system by default; manual override in Settings.
  - **Light palette:** white background, **light green** accent/buttons.
  - **Dark palette:** dark background, **midnight dusk** accent/buttons.
- **Rounded corners** — soft but not pill-shaped (target ~12–16dp radius on cards/rows/buttons).
- **Modern, smooth, soft** aesthetic. Reference: **Mihon** (typography, spacing, list rows) and **Habitica** (structure only — **no gamification**).
- **Rows, not blocks.**
- Transitions and state changes should feel smooth (Material 3 motion, subtle animations).
- Accessibility: content descriptions, minimum touch targets (48dp), contrast-compliant text.

---

## 6. Tech Stack (fixed)

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + unidirectional data flow; single-activity
- **Persistence:** Room (SQLite), local only
- **DI:** Hilt
- **Async:** Coroutines + Flow
- **Notifications:** `AlarmManager`/`WorkManager` + `NotificationManagerCompat`
- **Navigation:** Navigation-Compose
- **Min SDK:** 26 (Android 8.0) — adjust in Step 1 if a hard blocker appears
- **Target/Compile SDK:** latest stable
- **No network permission. No analytics. No tracking.**

---

## 7. Data Model (Room)

Entities (fields are indicative; AI refines in Step 3):

- **Habit**: id, title, note, resetSchedule (enum: DAILY/WEEKLY/MONTHLY/EVERY_N + N + optional weekday), allowUp (bool), allowDown (bool), currentCount (int), lastResetAt (timestamp), createdAt, updatedAt.
- **HabitLog** (history): id, habitId, date, countAtEndOfCycle, cycleStart, cycleEnd.
- **Todo**: id, title, note, dueDate (mandatory), createdAt, updatedAt, completedAt (nullable), sortOrder, isCompleted.
- **Daily**: id, title, note, startDate, repeatType (DAILY/WEEKLY/MONTHLY), everyN, weekdays (for weekly), createdAt, updatedAt.
- **DailyOccurrence**: id, dailyId, date, isCompleted, isSkipped, completedAt, sortOrder.
- **Subtask**: id, parentType (TODO/DAILY_OCCURRENCE), parentId, title, note, deadline (nullable), isCompleted, sortOrder.
- **Reminder**: id, parentType (TODO/DAILY), parentId, triggerAt, notificationId.

Relationships: Todo 1—* Subtask; Daily 1—* DailyOccurrence 1—* Subtask; Habit 1—* HabitLog; Todo/Daily 1—* Reminder.

**Single source of truth:** completion state stored once; Calendar and lists read the same records.

---

## 8. Business Rules (cascade & edge cases)

- Subtask all-checked → parent checked.
- Parent checked → all subtasks checked.
- Parent unchecked → all subtasks unchecked.
- Subtask unchecked (when parent was checked) → parent unchecked.
- Completed tasks sorted below incomplete, inside a "Completed" section.
- Habit reset: currentCount → 0 (or start value), `lastResetAt` updated, **HabitLog appended**; history never wiped.
- Daily occurrence generation: on app open and on date change, ensure today's (and any missed since last-open, per §9 decision) occurrences exist.
- Deleting parent → confirm → cascade delete children (and pending reminders).
- Deleting a Daily series → past completed occurrences and logs preserved; future occurrences and pending reminders removed.
- Time zone / clock changes: recompute reminder schedules.

---

## 9. Locked Decisions & Open Items

**Locked:**
- Local-only, offline, no accounts.
- History persists forever; habit reset never deletes history.
- Reminders = Android local notifications.
- To-Do due date mandatory.
- Subtask deadlines only within parent.
- Calendar allows check/uncheck and habit +/− only.
- FAB hidden on Calendar.
- Drag handle: tap = collapse/expand, long-press = drag reorder.
- Tap title = edit page (Habit edit page shows note emphasized).

**Decided here (flag if you disagree):**
- **Editing a Daily from the list → edits the whole series.** Editing a Daily **from the Calendar** → edits **that occurrence only**.
- **Missed Daily occurrences** are generated for missed dates since last app open, marked incomplete (skippable), but not retroactively checked.
- **Unchecking parent unchecks all subtasks** (inverse cascade, for consistency).
- **Reminder on a Daily** fires for each occurrence based on its scheduled time relative to the occurrence date.

**Open (AI may ask user, otherwise proceed with default):**
- Habit "start value" after reset (default: 0).
- Whether deleting a habit deletes its history (default: **keep history**, orphan-safe).

---

## 10. Non-Goals
- No gamification, points, streaks-as-rewards, avatars.
- No cloud, sync, login, sharing, collaboration.
- No web/desktop version.
- No analytics/tracking.
- No ads.

---

## 11. Acceptance Criteria (summary — per-step ACs in §12)

- App launches offline and shows 4 tabs.
- Each tab supports CRUD as specified; deletion confirms; cascades work.
- Subtask ↔ parent cascade works both directions and on uncheck.
- Habits auto-reset per schedule; history preserved and visible in Calendar.
- Dailies generate occurrences, support skip, and per-occurrence completion.
- Calendar shows tasks + habits per selected date; check/uncheck and +/− work and reflect everywhere.
- Light and dark themes match palette rules.
- Drag-handle tap toggles collapse; long-press drag reorders.
- Notifications fire for reminders while respecting Android permissions.
- No network calls anywhere.

---

## 12. Build Steps (execute ONE per session, in order)

Each step lists **Goal**, **Scope**, **Done when**. Sub-steps (`a`, `b`…) may be split if token-limited.

**Step 0 — Bootstrap activity file**
- Goal: Create `ACTIVITY.md` from §0.2 template with full checklist.
- Done when: File exists, all steps listed as TODO.

**Step 1 — Project skeleton**
- Goal: New Android project (Kotlin, Compose, Material 3), Hilt, Navigation-Compose, min SDK 26, single activity, empty 4-tab scaffold with bottom bar + top bar + 3-dot menu (Settings/About placeholders).
- Done when: App builds and runs; tabs navigate; menu opens placeholder screens.

**Step 2 — Theme system**
- Goal: Light + dark palettes per §5; system-follow + manual override in Settings.
- Done when: Toggling theme in Settings changes UI instantly; both palettes match spec colors.

**Step 3 — Data layer: entities + DAOs + DB**
- Goal: Room entities (§7), DAOs, database, repositories; no UI yet.
- Done when: Project compiles; basic DAO unit smoke test passes (create/read).

**Step 4 — Habits: list + create/edit + counter**
- Goal: Habits tab list rows (title, note preview, count, −/+ per enabled), FAB→create, tap title→edit page with note emphasized, deletion with confirm.
- Done when: Can CRUD habits; counters work; persistence verified after restart.

**Step 5 — Habits: auto-reset + history**
- Goal: Scheduled reset (WorkManager or on-open check), HabitLog writes, history preserved.
- Done when: A habit with a short reset schedule resets correctly and logs history.

**Step 6 — To-Do's: list + create/edit**
- Goal: To-Do tab list rows, FAB→create (due date mandatory), tap title→edit, deletion w/ cascade confirm, Completed section at bottom with gray+strike.
- Done when: CRUD works; completed items move to Completed section.

**Step 7 — Subtasks (To-Do's)**
- Goal: Add/edit/delete subtasks with note + deadline; cascade rules (§8); in-place gray+strike on check; collapse/expand via drag handle (expanded by default).
- Done when: All cascade rules verified; collapse works; reorder works within list.

**Step 8 — Dailies: model + occurrence generation**
- Goal: Create/edit Daily with schedule; generate occurrences for today and missed dates since last open; per-occurrence completion.
- Done when: Occurrences appear correctly; missed dates backfilled per §9.

**Step 9 — Dailies UI + subtasks + skip**
- Goal: Dailies tab list, expand/collapse, subtasks (same as To-Do's), Skip option, editing rules (list=series, Calendar=occurrence).
- Done when: All Dailies behaviors match §4.3 + §9.

**Step 10 — Reordering & drag handle everywhere**
- Goal: Unified drag handle: tap=collapse/expand, long-press=drag-reorder; persists `sortOrder`.
- Done when: Reorder persists across restart in all relevant tabs.

**Step 11 — Reminders & notifications**
- Goal: Add/edit/delete reminders on To-Do's and Dailies; AlarmManager scheduling; notification channel; permissions; BOOT_COMPLETED + timezone reschedule.
- Done when: A test reminder fires a notification; reschedules after reboot.

**Step 12 — Calendar UI + day list**
- Goal: Month calendar (top half), day list (bottom half) with tasks + habits for the selected date; check/uncheck tasks; +/− habits; history visible.
- Done when: Selecting dates shows correct items; toggling updates everywhere; FAB hidden on this tab.

**Step 13 — Settings & About pages**
- Goal: Full Settings (theme, notification permission status, optional habit defaults) + About (name, version, description, credits).
- Done when: Both pages complete and wired from the 3-dot menu.

**Step 13.5 — Import / Export**
- Goal: Implement JSON serialization of the Room database (using `kotlinx.serialization` or `Gson`). Create the Settings UI buttons for "Export to File" and "Import from File".
- Done when: Exported JSON contains all app data; importing it successfully restores the database.

**Step 14 — Polish pass**
- Goal: Motion/transition polish, empty states, loading states, accessibility (content descriptions, 48dp targets, contrast), rounded corners per §5, final visual QA against Mihon/Habitica references.
- Done when: App feels smooth; no accessibility blockers; both themes look finished.

**Step 15 — Final QA + release prep**
- Goal: Run through all Acceptance Criteria (§11); fix defects (log in ACTIVITY.md); verify no network calls; generate signed-build instructions; final `ACTIVITY.md` marked complete.
- Done when: All ACs pass; `ACTIVITY.md` shows 15/15 complete.

---

## 13. AI Session Protocol (restated for emphasis)

1. Read `SPEC.md` fully.
2. Read or create `ACTIVITY.md`.
3. Identify the next unfinished step.
4. Do **only** that step (split into sub-steps if token-limited — record splits in `ACTIVITY.md`).
5. Update `ACTIVITY.md` (§0.3).
6. Reply briefly: done / next / issues.
7. Stop.

**If you break something:** log it in `Deviations & Issues` with what broke, where, and what you tried. Do not silently patch over it. Future sessions rely on this honesty to recover.

**If you're unsure about a requirement:** write the question in `Deviations & Issues` and, if safe, proceed with the default from §9. Never block silently.

**End of SPEC.md.**