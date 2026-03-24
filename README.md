# 📅 Cortexa — Academic Life, Organized

> **A feature-rich Android timetable & productivity app for students**, integrating Google Calendar, Google Tasks, multi-cloud storage, smart notifications, and a full academic management suite — all stored locally with Room and synced in the background. An app with notifications for students to **track Classes, Assignments, Notes, Exams, ToDo's, Actions, and Ad-hoc engagements with repetition**. Allows **multiple attachments, observations, activity breakdowns, etc. for each of these**.
>
> 24000 lines of kotlin code and 9000 lines of xml's.
>
> **Copyright (c) 2025 Uddipan Bagchi. All rights reserved.**
> **See LICENSE in the project root for license information.**
![1000317691](https://github.com/user-attachments/assets/a5f19049-4ecb-4d0d-99a0-0bb8bc4d93c9)

---

## ✨ Feature Overview

Cortexa is not a simple scheduler. It's a complete academic operating system:

| Module | What It Does |
|---|---|
| 🗓️ **Timetables** | Import from CSV, manage Lecture & Personal timetables with validity windows |
| 🔁 **Engagements** | Flexible recurring events — specific days or N-day intervals |
| 📝 **Assignments** | Track coursework with due dates, progress %, and deadline reminders |
| 📖 **Exams** | Exam scheduling with countdown notifications |
| ✅ **To-Dos** | Priority-tagged tasks with optional due date/time |
| 📓 **Notes** | Rich notes with attachments |
| 🎯 **Actions** | Time-bounded activities with start/end datetime and venue |
| 🗺️ **Location Picker** | Map-based venue selection (Google Maps + Places API) |
| 📎 **Attachments** | Files, images, audio notes, hyperlinks — attached to any item |
| 🖼️ **Image → PDF** | Scan documents to PDF with perspective correction |
| 🔔 **Smart Notifications** | Per-item configurable reminders via `AlarmManager` |
| ☁️ **Cloud Sync** | Google Drive, Dropbox, OneDrive, Box, and pCloud |
| 📆 **Google Calendar Sync** | Read-only live sync of device calendar events |
| ✔️ **Google Tasks Sync** | Background sync via WorkManager (every 4 hours) |
| 🧹 **Auto Cleanup** | Configurable background purge of old data |
| 🌗 **Dark Mode** | Full Material 3 day/night theming |

![1000317692](https://github.com/user-attachments/assets/bfcf35f4-81af-41e3-a84e-f0ab0b933844)

---

## 📸 Architecture at a Glance

```
com.costheta.cortexa
├── MainActivity               # Home screen, Google Sign-In, sync triggers
├── ScheduleActivity           # Day/week view rendered by ScheduleView
├── ScheduleView               # Custom canvas-drawn schedule widget
├── NotificationHelper         # AlarmManager-based notification scheduler
├── NotificationReceiver       # BroadcastReceiver for alarm delivery
├── CleanupWorker              # WorkManager periodic data cleanup
├── TimetableApplication       # Application class (singleton init)
│
├── timetable/                 # Timetable + ClassSession CRUD
├── engagement/                # Ad-hoc recurring engagements
├── assignment/                # Assignment tracking
├── exam/                      # Exam management
├── todo/                      # To-do list
├── note/                      # Notes
├── action/                    # Time-bounded activities
│
├── external/
│   ├── calendar/              # CalendarSyncManager (ContentObserver)
│   └── tasks/                 # TasksSyncManager + TasksSyncWorker (WorkManager)
│
├── data/
│   ├── models/                # 15 Room @Entity data classes
│   ├── db/
│   │   ├── AppDatabase.kt     # Single Room DB, version 9
│   │   └── dao/               # 12 DAOs (TimetableDao, EngagementDao, etc.)
│   └── parser/
│       └── CsvTimetableParser.kt  # Flexible CSV importer (Format A & B)
│
└── util/
    ├── attachment/            # File/audio/hyperlink attachment manager
    ├── audio/                 # In-app audio recorder
    ├── autofill/              # FuzzyAutoCompleteTextView
    ├── image2pdf/             # Camera → perspective-correct PDF
    ├── location/              # Map picker (free + paid variants)
    ├── storage/               # Cloud provider abstraction
    │   └── cloud/             # Google Drive, Dropbox, OneDrive, Box, pCloud
    ├── subtask/               # Subtask manager (drag-to-reorder)
    ├── ui/                    # CardColor theming utilities
    └── uirefresh/             # PeriodicallyRefreshAppCompatActivity
```
![1000317694](https://github.com/user-attachments/assets/74775319-8b79-4fe0-9853-4c1aa8fb2055)

---

## 🗄️ Database Schema

Cortexa uses a single **Room** database (`timetable_database`, v9) with 15 entities across all modules.

### Core Timetable Entities

```
timetables
  id, name, type (LECTURE|PERSONAL), validityStartDate, validityEndDate,
  defaultNotificationOffsetMinutes1, defaultNotificationOffsetMinutes2,
  isMasterSilenced, isActive, creationDate, creationTimeZone

class_sessions  →  FK: timetableId → timetables.id (CASCADE)
  sessionId, timetableId, courseName, dayOfWeek, startTime,
  durationMinutes, location, notes, notificationOffsets,
  validityStartDate, validityEndDate, isSilenced,
  startDateTimeMillis, endDateTimeMillis

session_exceptions  →  FK: originalSessionId → class_sessions.sessionId (CASCADE)
  exceptionId, originalSessionId, originalDate,
  classSessionExceptionType (CANCELLED|MODIFIED),
  newDate, newStartTime, newDurationMinutes, newNotes
```

### Engagement Entities

```
engagements
  engagementId, engagementName, startTime, durationMinutes,
  validityStartDate, validityEndDate,
  daySelectionType (SPECIFIC_DAYS|RECURRENCE),
  selectedDaysOfWeek, recurrenceIntervalDays,
  notes, isSilenced, isActive, venue,
  notification1/2/3Minutes, creationTimezone

engagement_exceptions  →  FK: originalEngagementId (CASCADE)
```

### Academic Tracking Entities

```
assignments
  assignmentId, courseName, assignmentTopic, assignmentName,
  dueDate, submissionTime, estimatedHours, currentProgress,
  silenceNotifications, dueDateTimeMillis, creationTimezone

exams
  examId, courseName, examName, examDate, examTime,
  venue, notes, silenceNotifications, examDateTimeMillis

todos
  todoId, title, description, dueDate, dueTime,
  isCompleted, priority, completedTimeInMillis,
  silenceNotifications, originalZoneId, dueDateTimeMillis

actions
  actionId, actionName, description, startDate, startTime,
  endDate, endTime, notificationMinutes1/2/3,
  venue, isDone, startDateTimeMillis, endDateTimeMillis, creationTimezone

notes_items
  noteId, title, content, creationDate, lastModified, validatedItem
```

### Supporting Entities

```
attachments       →  file path, type, linked to parent (polymorphic)
subtasks          →  belongs to todos/actions/assignments, drag-sortable
locations         →  lat/lng + label, reusable across items

synced_calendar_events
  localId, googleEventId (unique), googleCalendarId,
  title, description, location,
  startDateTimeMillis, endDateTimeMillis, isAllDay,
  eventColor, lastSyncedTimestamp

synced_google_tasks
  localId, googleTaskId (unique), googleTaskListId,
  title, notes, dueDateTimeMillis,
  isCompleted, parentTaskId, lastSyncedTimestamp
```

![1000317695](https://github.com/user-attachments/assets/b6ac804c-9418-4712-8020-25978ab7ec12)

---

## 📆 Google Integration

### Google Calendar Sync

- **Mechanism**: `CalendarSyncManager` reads from the Android `CalendarProvider` (system) via `ContentResolver`.
- **Fields synced**: Event ID, Calendar ID, Title, Description, Location, Start/End times, All-day flag, Color.
- **Live updates**: Registers a `ContentObserver` on `CalendarContract.Events.CONTENT_URI` to detect changes in real time.
- **Permission required**: `READ_CALENDAR` (prompted at runtime on Android 13+).
- **Lifecycle**: `startSync()` called in `MainActivity`, `stopSync()` called in `onDestroy`.

### Google Tasks Sync

- **Mechanism**: `TasksSyncWorker` (a `CoroutineWorker`) runs via **WorkManager**.
- **Schedule**: Periodic sync every **4 hours**, requires network connectivity. One-time immediate sync available for "Sync Now" actions.
- **Auth**: Google Sign-In with `TasksScopes.TASKS_READONLY` and Calendar OAuth scopes.
- **Conflict strategy**: `upsert` by `googleTaskId` — updates existing, inserts new, removes deleted.
- **Data stored**: Task list ID, task ID, title, notes, due date, completion status, parent task ID (for subtask hierarchy).

### Authentication Flow

1. User taps **Sign In with Google** on the main screen.
2. `GoogleSignInClient` requests scopes for Tasks + Calendar.
3. On successful sign-in, `CalendarSyncManager.startSync()` and `TasksSyncManager.schedulePeriodicSync()` are triggered.
4. Sign-out clears credentials and cancels all scheduled syncs.

---
![1000317696](https://github.com/user-attachments/assets/bfcd99ff-4c42-41a1-bde7-523558e28043)

## 📥 CSV Timetable Import

Cortexa's `CsvTimetableParser` supports **two flexible CSV layouts**:

### Format A — Days in rows, times in header

```csv
Day,08:45 - 10:15,10:15 – 11:45,12:00 - 13:30
Monday,Mathematics,Physics,
Tuesday,Chemistry,,Biology
Wednesday,English,Mathematics,Physics
```

### Format B — Times in rows, days in header

```csv
,Monday,Tuesday,Wednesday,Thursday,Friday
09:00-10:00,Math,Physics,Chemistry,History,Biology
10:00-11:00,History,Biology,Math,,Physics
```

**Parser features:**
- Handles both em-dash (–) and hyphen (-) as time separators
- Skips blank rows gracefully
- Supports quoted and unquoted cell values
- Auto-detects format from header structure
- Maps course names to `ClassSession` entities with correct `DayOfWeek` and `LocalTime` values

---

## 🔔 Notification System

Cortexa uses Android's `AlarmManager` with `setExactAndAllowWhileIdle()` for reliable delivery.

### Notification Channels

| Channel | Use Case |
|---|---|
| `default_channel` | Standard reminders (classes, tasks) |
| `urgent_channel` | High-priority alerts (imminent deadlines) |
| `silent_channel` | Background sync status |

### Notification Levels (per item type)

- **Timetable / Class Sessions**: Up to 2 configurable offsets (e.g., 30 min + 15 min before). Master-silence per timetable, individual-silence per session.
- **Engagements**: Up to 3 configurable offsets per engagement.
- **Assignments / Exams / To-Dos / Actions**: Per-item silence toggle + up to 3 offset minutes.

### `NotificationReceiver`

A `BroadcastReceiver` that fires when an alarm triggers. It reads the pending intent extras to reconstruct the notification content and post it to the correct channel.

---

## ☁️ Cloud Storage

All cloud providers implement the `SyncProvider` interface and support upload/download of backup files.

| Provider | SDK Used |
|---|---|
| Google Drive | `google-api-services-drive` |
| Dropbox | `dropbox-core-sdk` v7 |
| OneDrive | `microsoft-graph` v6.5 |
| Box | `box-android-sdk` |
| pCloud | `com.pcloud.sdk:core-ktx` |

Authentication is handled per-provider (OAuth2 / MSAL for Microsoft) and credentials are persisted in `SharedPreferences`.

---

## 📎 Attachments

The `AttachmentManager` supports multiple attachment types per item:

- **Files** — any document/media selected from device storage
- **Images** — from gallery or camera
- **Audio notes** — recorded in-app via `RecordAudioActivity` (uses `AudioRecord`)
- **Hyperlinks** — custom-labeled URLs
- **PDFs** — generated from images using the Image-to-PDF scanner

Attachments are stored as file-system paths (via `FileProvider`) and metadata in the `attachments` Room table.

---

## 🗺️ Location Picker

Two variants of map-based venue selection:

- **`MapPickerActivity`** — Standard (free tier), uses Google Maps SDK + Places autocomplete.
- **`MapPickerPaidActivity`** — Extended variant with additional capabilities.

Both support:
- Search by place name with autocomplete suggestions
- Tap-to-pin on map
- Reverse geocoding for address display
- Returning lat/lng + label back to the calling activity

---

## 🖼️ Image to PDF

`ImageToPdfActivity` provides an in-app document scanner:

1. Pick images from gallery or capture with camera.
2. `QuadrilateralImageView` — interactive perspective-correction widget (drag corners to define document bounds).
3. `ImageEditorActivity` — crop, rotate, adjust brightness.
4. `ImageUtils` — applies perspective transform and generates the final PDF.
5. Save to device storage or attach directly to an item.

---

## 🧹 Auto Cleanup (Background)

`CleanupWorker` (a `CoroutineWorker` scheduled via WorkManager) runs periodically and:

- **Auto-archives** timetables past their `validityEndDate` (configurable in preferences).
- **Purges** old engagements, assignments, exams, to-dos, notes, and actions after a user-defined number of days (default 180 days for most, 360 days for notes).
- Also cancels stale scheduled notifications via `NotificationHelper`.

All thresholds are configurable from `PreferencesActivity`.

---

![1000317697](https://github.com/user-attachments/assets/4b9036b2-e3ea-45ac-bc11-fb94387878ba)

## ⚙️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | API 26 (Android 8.0) |
| Target / Compile SDK | API 36 |
| UI | XML layouts, Material 3 (`Theme.CortexaApp`) |
| View Binding | Enabled |
| Database | Room 2.6.1 |
| Async | Kotlin Coroutines + Flow |
| Background Work | WorkManager 2.9 |
| Architecture | MVVM (ViewModel + LiveData) |
| Maps | Google Maps SDK 18.2, Places 3.4 |
| HTTP | OkHttp 4.12 |
| Emoji | `androidx.emoji2` + emoji picker |
| Spreadsheet parsing | Apache POI (xls + xlsx) |
| Desugaring | `coreLibraryDesugaringEnabled = true` (Java 8+ time APIs) |
| Build system | Gradle (Kotlin DSL) |

---

## 🔑 Permissions

| Permission | Purpose |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Reliable notification delivery on Android 12+ |
| `POST_NOTIFICATIONS` | Show notifications on Android 13+ |
| `CAMERA` | Document scanning / photo attachments |
| `RECORD_AUDIO` | In-app voice note recording |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Scoped media picker (Android 13+) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Map-based venue picker |
| `INTERNET` | Cloud sync, Maps API, Google Auth |
| `GET_ACCOUNTS` | Google Sign-In account selection |
| `ACCESS_NETWORK_STATE` | Conditional sync (network-aware WorkManager) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- A Google Cloud project with the following APIs enabled:
    - Google Calendar API
    - Google Tasks API
    - Google Maps SDK for Android
    - Google Places API
- A `google-services.json` placed at `app/google-services.json`

### Setup

1. **Clone the repo**
   ```bash
   git clone <your-repo-url>
   cd TimetableApp
   ```

2. **Add your Google Maps API key**

   In `AndroidManifest.xml`, replace the placeholder:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```

3. **Add `google-services.json`**

   Download from your Firebase / Google Cloud console and place at `app/google-services.json`.

4. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or run directly from Android Studio on a device/emulator running Android 8.0+.

---

## 📁 Project Structure Summary

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/costheta/cortexa/    # All Kotlin source
│   └── res/
│       ├── layout/                   # 40+ XML layout files
│       ├── values/                   # colors.xml, styles.xml, themes.xml, dimens.xml
│       ├── values-night/             # Dark theme overrides
│       ├── menu/                     # Toolbar menus
│       ├── color/                    # Color state lists
│       └── xml/                      # file_paths, backup_rules, data_extraction_rules
├── build.gradle.kts
├── proguard-rules.pro
├── google-services.json
└── keywords.txt
```

---

## 🗺️ Roadmap / Known Limitations

- `fallbackToDestructiveMigration()` is currently set on the Room database — production releases should add proper schema migrations before incrementing the database version.
- `isMinifyEnabled = false` in release builds — enable R8/ProGuard for release APKs.
- Google Maps API key is currently embedded in `AndroidManifest.xml` — move to a secrets management solution (e.g., `secrets-gradle-plugin`) before publishing.
- Calendar sync is read-only; write-back to Google Calendar is not yet implemented.

---

## 📄 License

_Specify your license here._

---

> Built with ❤️ using Kotlin, Room, WorkManager, and the Google ecosystem.
