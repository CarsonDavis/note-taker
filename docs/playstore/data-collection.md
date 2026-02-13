# Data Collection — Technical Reference

*Code-sourced audit of every piece of user data the app touches.*
*Last updated: 2026-02-13*

## 1. Local Storage — Room Database

**File:** `notetaker.db` (version 2, app-private directory)
**Source:** `data/local/AppDatabase.kt`

### `pending_notes` table
Offline queue for notes waiting to upload.

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (auto) | Primary key |
| `text` | TEXT | Full note content |
| `filename` | TEXT | Target filename (e.g., `inbox/1707840000000.md`) |
| `createdAt` | INTEGER | Unix timestamp (millis) |
| `status` | TEXT | `pending`, `uploading`, or `failed` |

**Source:** `data/local/PendingNoteEntity.kt`, `data/local/PendingNoteDao.kt`

### `submissions` table
History of past submissions (displayed in UI).

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (auto) | Primary key |
| `timestamp` | INTEGER | Unix timestamp (millis) |
| `preview` | TEXT | First 50 characters of the note |
| `success` | BOOLEAN | Whether the upload succeeded |

**Source:** `data/local/SubmissionEntity.kt`, `data/local/SubmissionDao.kt`

## 2. Local Storage — Preferences DataStore

**Store name:** `auth` (app-private directory, NOT encrypted at rest — plain protobuf)
**Source:** `data/auth/AuthManager.kt`

| Key | Type | Description |
|-----|------|-------------|
| `access_token` | String | GitHub fine-grained personal access token |
| `username` | String | GitHub login (e.g., `CarsonDavis`) |
| `repo_owner` | String | Repository owner (e.g., `CarsonDavis`) |
| `repo_name` | String | Repository name (e.g., `notes`) |

All values cleared on sign-out (`signOut()` calls `dataStore.edit { it.clear() }`).

## 3. Network Transmission

All traffic goes to `https://api.github.com/` over HTTPS. No other hosts are contacted.
**Source:** `data/api/GitHubApi.kt`, `di/AppModule.kt` (base URL)

| Endpoint | Method | Sent | Received |
|----------|--------|------|----------|
| `/user` | GET | Token in `Authorization` header | `login`, `avatar_url` |
| `/repos/{owner}/{repo}/contents/{path}` | GET | Token in header | File content (Base64) or directory listing |
| `/repos/{owner}/{repo}/contents/` | GET | Token in header | Root directory listing |
| `/repos/{owner}/{repo}/contents/{path}` | PUT | Token in header, note text (Base64) + commit message in body | Created file ref (`name`, `path`, `sha`) |

The PUT to `contents/inbox/{timestamp}.md` is the only write operation. All other calls are read-only.

## 4. In-Memory Only (never persisted)

| Data | Where | Source |
|------|-------|--------|
| Current note text being composed | `NoteViewModel._noteText` | `ui/viewmodels/NoteViewModel.kt` |
| Partial speech recognition results | `SpeechRecognizerManager._partialText` | `speech/SpeechRecognizerManager.kt` |
| Current topic string | `NoteViewModel._topic` | `ui/viewmodels/NoteViewModel.kt` |
| File/directory listings from GitHub | `BrowseViewModel` state | `ui/viewmodels/BrowseViewModel.kt` |

All cleared when the relevant ViewModel is destroyed or the app process ends.

## 5. Voice / Audio

- Uses Android's built-in `SpeechRecognizer` API — audio is processed by the device's default speech recognition service (typically Google)
- App receives **only transcribed text** (via `RESULTS_RECOGNITION` bundle), no audio is stored
- `RECORD_AUDIO` permission required at runtime
- Audio focus held at app level (`AUDIOFOCUS_GAIN_TRANSIENT`) for the duration of a voice session to prevent audiobook blips during recognizer restarts

**Source:** `speech/SpeechRecognizerManager.kt`, `AndroidManifest.xml` (permission)

## 6. What's NOT Collected

- **Zero analytics/telemetry/crash reporting** — no Firebase, Google Analytics, Crashlytics, Sentry, Mixpanel, or any other third-party SDK
- **No advertising networks**
- **No device identifiers** — no IMEI, advertising ID, Android ID, or hardware fingerprinting
- **No location, contacts, calendar, camera, photos, call logs, SMS, or browsing history**
- **`allowBackup="false"`** in AndroidManifest.xml — app data excluded from ADB and cloud backups
- **HTTP body logging disabled in release builds** — `HttpLoggingInterceptor.Level.NONE` (only `Level.BODY` in debug builds)

**Source:** `AndroidManifest.xml`, `di/AppModule.kt`

## Privacy Policy Audit (2026-02-13)

Cross-referenced `privacy-policy.md` against all source code listed above.

**Result: The privacy policy is accurate. No corrections needed.**

Verified items:
- All 5 local storage items in the privacy policy table match the code exactly
- "GitHub Contents API over HTTPS" — confirmed (sole base URL `https://api.github.com/`)
- "No audio recorded or stored" — confirmed, only transcribed text via `RESULTS_RECOGNITION`
- "No analytics, ads, tracking" — confirmed, zero third-party SDKs in dependencies
- Third-party services (GitHub API + Google Speech) — correctly identified
- Data retention and user rights sections — accurate
- `data-safety-declaration.md` claims "encrypted in transit" (HTTPS) — correct; does not claim at-rest encryption — accurate

Notes (not inaccuracies):
- DataStore is NOT encrypted at rest (plain protobuf in app-private dir). The privacy policy says "app-private" which is accurate — it doesn't claim encryption.
- Debug builds log full HTTP bodies (tokens + note content) via `HttpLoggingInterceptor.Level.BODY`. Standard practice; release builds use `Level.NONE`.
- The `preview` field in `submissions` stores the first 50 characters of each note locally. Covered by the "Submission history" row in the privacy policy table.
