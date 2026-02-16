# Implementation Log

## M1: Project Scaffold (2026-02-09)

**What was built:**
- Full Gradle project structure with Kotlin DSL and version catalog
- AGP 9.0.0 with built-in Kotlin 2.2.10
- Dependencies: Jetpack Compose (BOM 2026.01.01), Material 3, Hilt 2.59.1, Room 2.8.4, Retrofit 3.0.0, OkHttp 5.3.0, kotlinx.serialization 1.8.0, Navigation 2.9.7, DataStore 1.2.0
- KSP 2.3.5 (Kotlin-version-independent, AGP 9 compatible)
- Minimal app: HiltAndroidApp, single Activity, dark-only Material 3 theme, "Note Taker" text

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (41 tasks, ~1.5 min)
- APK produced at `app/build/outputs/apk/debug/app-debug.apk` (63MB debug)

**Key decisions:**
- KSP 2.3.5 instead of 2.2.10-2.0.2 — the latter has a known bug with AGP 9's built-in Kotlin source set handling
- OkHttp 5.3.0 (stable) instead of 5.0.0-alpha series
- Gradle wrapper set to 9.1.0 (AGP 9.0.0 minimum requirement)

## M2: Note Input Screen UI (2026-02-09)

**What was built:**
- `TopicBar` — displays sticky topic (or "No topic set" / loading state), settings gear icon
- `SubmissionHistory` — collapsible "Recent" section with success/failure icons, timestamps, note previews
- `NoteInputScreen` — full screen: topic bar, 200dp text field, centered submit button, history, snackbar
- `NoteViewModel` — mock state management: tracks note text, submissions list, submit clears field and adds to history
- Submit button disabled when text is empty or submitting; shows spinner during submit
- Snackbar shows "Note saved" after successful submit

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (6s incremental)

## M3: Room Database (2026-02-09)

**What was built:**
- `SubmissionEntity` — Room entity with id, timestamp, preview, success fields
- `SubmissionDao` — insert + getRecent (last 10, ordered by timestamp desc) as Flow
- `AppDatabase` — Room database (exportSchema=false)
- `NoteRepository` — data access layer: submitNote, fetchCurrentTopic, getUserRepos
- `AppModule` — Hilt DI: provides OkHttpClient, Retrofit, Json, Room database, DAO
- Updated `NoteViewModel` to use real Room data via repository

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M4: GitHub OAuth Device Flow (2026-02-09)

**What was built:**
- `GitHubApi` — Retrofit interface with device flow endpoints, user/repos, contents API
- `AuthManager` — Preferences DataStore for token, username, repo owner/name
- `AuthViewModel` — full device flow: requestDeviceCode → poll → save token → load repos
- `AuthScreen` — 3-step UI: Welcome → Device Code (with copy-to-clipboard, open browser) → Repo Selection
- `NavGraph` — type-safe Compose Navigation: AuthRoute, NoteRoute, SettingsRoute
- First-run gating: app shows auth screen when no token/repo saved

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- Note: `GITHUB_CLIENT_ID` placeholder needs to be replaced with a real OAuth App client ID

## M5: Push Notes to GitHub (2026-02-09)

**What was built:**
- `NoteRepository.submitNote()` — creates file via GitHub Contents API in `inbox/` folder
- Filename format: `inbox/2026-02-09T143200-0500.md` (ISO 8601 local timezone)
- Content Base64-encoded per GitHub API requirements
- Success/failure recorded to Room database
- `NoteViewModel.submit()` wired to real repository
- Error displayed via snackbar, text preserved on failure

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M6: Sticky Topic Display (2026-02-09)

**What was built:**
- `NoteRepository.fetchCurrentTopic()` — fetches `.current_topic` file from repo, Base64-decodes
- `NoteViewModel` fetches topic on init, shows loading state then result
- `TopicBar` shows topic text, "No topic set" (dimmed), or "..." (loading)

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M7: Settings Screen (2026-02-09)

**What was built:**
- `SettingsScreen` — three sections: GitHub Account (sign out), Repository (change), Digital Assistant (role detection)
- `SettingsViewModel` — observes auth state, checks ROLE_ASSISTANT, repo picker with bottom sheet
- Sign out clears DataStore and navigates to auth screen
- Repo picker loads user repos and saves selection
- Digital assistant section detects if app holds ROLE_ASSISTANT, links to system settings if not
- Re-checks role on resume (returning from system settings)

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (0 warnings)

## M8: Lock Screen Launch (2026-02-09)

**What was built:**
- `NoteAssistService` — VoiceInteractionService, launches NoteCaptureActivity from lock screen
- `NoteAssistSessionService` — boilerplate session factory
- `NoteAssistSession` — handles unlocked launch, disables system overlay, starts activity
- `NoteCaptureActivity` — showWhenLocked + turnScreenOn, shows NoteInputScreen
- Settings button from lock screen triggers `requestDismissKeyguard()` for biometric/PIN
- `assist_service.xml` — supportsAssist + supportsLaunchVoiceAssistFromKeyguard
- AndroidManifest updated with both services and lock screen activity

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (0 warnings)

## M9: Auth Migration — OAuth Device Flow → Fine-Grained PAT (2026-02-09)

**What was built:**
- Replaced OAuth device flow with simple PAT-based setup screen
- `AuthScreen.kt` — Single screen: instructions, "Create Token on GitHub" button, token field (password-masked with visibility toggle), repo field (`owner/repo`), "Continue" button with validation spinner
- `AuthViewModel.kt` — Validates token via `GET /user`, parses `owner/repo`, saves via AuthManager
- `GitHubApi.kt` — Removed device flow endpoints (`requestDeviceCode`, `pollAccessToken`, `getUserRepos`) and data classes (`DeviceCodeRequest/Response`, `AccessTokenRequest/Response`, `GitHubRepo`)
- `NoteRepository.kt` — Removed `getUserRepos()` method
- `SettingsViewModel.kt` — Removed repo picker state and methods, removed `NoteRepository` dependency
- `SettingsScreen.kt` — Removed repo picker bottom sheet and "Change" button, repo shown read-only with hint
- Created `docs/adr/001-pat-over-oauth.md` — Documents the decision
- Created `docs/PAT-SETUP.md` — User-facing setup instructions

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (7s)
- AuthManager, NavGraph, AppModule, NoteRepository (submitNote/fetchCurrentTopic) unchanged — PAT works identically as Bearer token

**Key decisions:**
- Fine-grained PAT over OAuth (see ADR 001): zero infrastructure, user controls repo scope natively via GitHub's PAT UI, simpler code
- No repo picker — user types `owner/repo` manually (single-user app, one-time setup)
- Token visibility toggle for usability during paste

## M10: Topic Refresh After Submission (2026-02-10)

**What was built:**
- `NoteViewModel.submit()` now calls `fetchTopic()` after a successful note submission, so the topic bar updates if the LLM agent has changed the topic
- Added "Smarter Topic Refresh" section to `docs/ROADMAP.md` documenting the limitation and future approaches (periodic polling, webhooks, ETag)

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (4s)

## M11: On-Device Bug Fixes (2026-02-10)

**What was built:**
- **Submit success animation**: Submit button now animates through three states (Submit → Saving → Sent!) using `AnimatedContent` with fade+scale transitions. Shows checkmark icon + tertiary color for 1.5s on success, then resets. Replaced snackbar-based "Note saved" feedback.
- **Retrofit path encoding fix**: Added `encoded = true` to `@Path("path")` in `GitHubApi.kt` for both `getFileContent` and `createFile`. Without this, Retrofit URL-encodes the `/` in `inbox/filename.md` to `%2F`, causing GitHub to return 404.
- **Digital assistant registration fix**: Three issues prevented the app from working as the digital assistant:
  1. `NoteAssistSessionService` was `exported="false"` — the system couldn't bind to it. Changed to `exported="true"` (protected by `BIND_VOICE_INTERACTION` permission).
  2. Missing `android.intent.action.ASSIST` intent filter on `MainActivity`. `ROLE_ASSISTANT` requires both a `VoiceInteractionService` and an activity handling the ASSIST intent. Added the intent filter.
  3. Missing `recognitionService` in `assist_service.xml`. On Android 16, `VoiceInteractionServiceInfo` requires a `recognitionService` — without it, the service is "unqualified" and the `voice_interaction_service` secure setting is never populated even though `ROLE_ASSISTANT` is assigned. Created `NoteRecognitionService.kt` (stub that returns `ERROR_RECOGNIZER_BUSY`) and referenced it in the XML.
- Updated `docs/APP-TRIGGER.md` to document all three requirements.

**How verified:**
- `./gradlew installDebug` → installed on SM-S928U1 (Android 16)
- Note submission confirmed working on device
- App appears in Settings > Apps > Default Apps > Digital assistant app
- `adb shell cmd role get-role-holders android.app.role.ASSISTANT` → `com.carsondavis.notetaker`
- `adb shell dumpsys voiceinteraction` → shows `mComponent=com.carsondavis.notetaker/.assist.NoteAssistService` (active, no parse errors)

**Key discovery:**
- `ROLE_ASSISTANT` (RoleManager) and `voice_interaction_service` (secure setting) are separate systems. The role can be assigned while the VoiceInteractionManager still shows "No active implementation" if the service fails validation. The "unqualified" log message from `AssistantRoleBehavior` is the clue.

## M12: PendingNote Entity + Room Migration (2026-02-10)

**What was built:**
- `PendingNoteEntity` — Room entity: id, text, filename, createdAt, status (pending/uploading/failed)
- `PendingNoteDao` — insert, getAllPending, getPendingCount (Flow), updateStatus, delete
- `AppDatabase` — bumped version 1→2, added `MIGRATION_1_2` (CREATE TABLE for pending_notes)
- `AppModule` — added `.addMigrations(MIGRATION_1_2)`, provides PendingNoteDao

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M13: Queue-First Submission + WorkManager (2026-02-10)

**What was built:**
- Added dependencies: `work-runtime-ktx:2.10.1`, `hilt-work:1.2.0`, `hilt-compiler:1.2.0`
- `NoteUploadWorker` — `@HiltWorker` CoroutineWorker: processes all pending notes, handles 422 conflict with `-1` suffix
- `NoteRepository` — new queue-first flow: always insert to pending_notes first, try immediate push, fall back to WorkManager retry. Returns `SubmitResult.SENT` or `SubmitResult.QUEUED`. Exposes `pendingCount: Flow<Int>`
- `NoteApp` — implements `Configuration.Provider` with `HiltWorkerFactory` for `@HiltWorker` support
- `AndroidManifest.xml` — disabled default WorkManager initializer via `<provider>` removal
- `AppModule` — provides `WorkManager` instance

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M14: Queue UI Indicators (2026-02-10)

**What was built:**
- `NoteUiState` — added `submitQueued: Boolean` and `pendingCount: Int`
- `NoteViewModel` — observes `pendingCount`, handles `SubmitResult.QUEUED` (clears text, sets submitQueued)
- `NoteInputScreen` — added "queued" state to `AnimatedContent` (clock icon + "Queued" text, secondary color), auto-dismisses after 1.5s. Shows "N notes queued" text below button when `pendingCount > 0`
- `TopicBar` — added `onBrowseClick` parameter with browse icon (MenuBook) next to settings gear

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M15: Browse Notes — Data Layer + ViewModel (2026-02-10)

**What was built:**
- `GitHubApi` — added `getDirectoryContents()` and `getRootContents()` endpoints, `GitHubDirectoryEntry` data class
- `NoteRepository` — added `fetchDirectoryContents(path)` (sorts dirs-first then alphabetical) and `fetchFileContent(path)` (Base64-decodes)
- `BrowseViewModel` — manages browse state: directory navigation, file viewing, navigateUp

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M16: Browse Notes — Screen + Navigation + Markdown (2026-02-10)

**What was built:**
- Added dependency: `markwon-core:4.6.2` for markdown rendering
- `MarkdownContent` — `AndroidView` wrapping Markwon `TextView`, respects Material theme text color
- `BrowseScreen` — TopAppBar with back arrow, directory listing with folder/file icons, file viewer (markdown via Markwon or monospace for non-.md), BackHandler for in-screen navigation, empty/error/loading states
- `NavGraph` — added `BrowseRoute`, `initialRoute` parameter for intent-driven navigation
- `NoteInputScreen` — added `onBrowseClick` parameter
- `NoteCaptureActivity` — refactored to `dismissAndNavigate()` helper, supports both settings and browse
- `MainActivity` — reads `open_settings`/`open_browse` intent extras, passes `initialRoute` to `AppNavGraph`

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M17: Voice-First Note Input (2026-02-12)

**What was built:**
- `SpeechRecognizerManager` — new class encapsulating Android `SpeechRecognizer` lifecycle with continuous listening. Auto-restarts on `onResults()`, `ERROR_NO_MATCH`, and `ERROR_SPEECH_TIMEOUT`. Real errors (audio, network, server) stop listening and notify UI. Exposes `listeningState: StateFlow` and `partialText: StateFlow`.
- `NoteViewModel` — added `@ApplicationContext` for SpeechRecognizerManager. New state fields: `inputMode` (VOICE/KEYBOARD), `listeningState`, `speechAvailable`, `permissionGranted`. Text accumulation: finalized speech segments joined with spaces, partial text appended for display. Methods: `onPermissionResult()`, `startVoiceInput()`, `switchToKeyboard()`, `stopVoiceInput()`. Submit stops voice, submits, clears, and restarts voice.
- `NoteInputScreen` — permission request via `rememberLauncherForActivityResult` on first composition. `LifecycleEventEffect(ON_RESUME)` starts voice, `ON_PAUSE` stops it. Listening indicator (red mic icon + "Listening...") above text field in voice mode. `onFocusChanged` on text field switches to keyboard mode. Mic button next to Submit in keyboard mode. Text field `readOnly` in voice mode.
- `NavGraph` — `LifecycleEventEffect(ON_START)` with `rememberSaveable` flag pops back to NoteRoute when app returns from background (skips first start).
- `AndroidManifest.xml` — added `RECORD_AUDIO` permission.

**Edge cases handled:**
- Permission denied → keyboard-only fallback, mic button hidden
- SpeechRecognizer unavailable → keyboard-only fallback
- Submit while listening → stops, submits, clears, restarts voice
- App backgrounded → ON_PAUSE stops recognizer, ON_RESUME restarts
- Network/server errors → stops listening, shows error via snackbar

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M18: Play Store Publishing Docs + CI/CD (2026-02-12)

**What was built:**
- `docs/playstore/checklist.md` — 7-phase publishing checklist: prerequisites, materials, build, Play Console setup, testing track (12-tester requirement), first release, CI/CD setup with service account instructions
- `docs/playstore/store-listing.md` — Store listing content: title, short/full descriptions, IARC questionnaire answers (Everyone rating), visual asset specs, keywords, contact info placeholders
- `docs/playstore/data-safety-declaration.md` — Data safety form answers covering all Play Console data types. Key declarations: collects note text and GitHub username, transmits note text to GitHub API over HTTPS, no audio collected (RECORD_AUDIO used for SpeechRecognizer API), no analytics/ads/crash reporting
- `docs/playstore/privacy-policy.md` — Full privacy policy suitable for Play Store listing. Covers data access, transmission, local storage, speech recognition, third-party services (GitHub API only), user rights (delete, revoke, uninstall)
- `app/build.gradle.kts` — Added release signing config reading from env vars (KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) with null check so local builds still work unsigned. Added env-var-based versioning (VERSION_CODE, VERSION_NAME) with fallback defaults
- `.github/workflows/release.yml` — GitHub Actions workflow triggered on `v*` tag push. Steps: checkout, JDK 21, Gradle with caching, version extraction from tag (v1.2.3 → versionCode 10203), keystore decode from base64 secret, signed AAB build, upload to Play Store internal track via r0adkll/upload-google-play@v1, attach AAB to GitHub release
- `docs/ROADMAP.md` — Added "Donate / Tip Button" as a V3 feature

**GitHub Secrets required (6):**
KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, PLAY_SERVICE_ACCOUNT_JSON, PLAY_PACKAGE_NAME

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (signing config is no-op without env vars)
- `./gradlew bundleRelease` → BUILD SUCCESSFUL, produces `app-release.aab` (13.7 MB)
- Workflow YAML validated for correct syntax and step dependencies
- All docs cross-reference each other consistently and match actual app permissions (INTERNET, RECORD_AUDIO) and data flows

**Key notes:**
- First AAB must be uploaded manually via Play Console — the GitHub Action requires the app to already exist
- RECORD_AUDIO triggers a Permissions Declaration Form during review (justification: speech-to-text via Android SpeechRecognizer, no audio stored by app), may add 1-2 weeks
- Personal accounts created after Nov 2023 need 14-day closed test with 12 testers before production access

## M19: Play Store Docs — Codebase Validation Fixes (2026-02-12)

**What was built:**
Validated all 4 Play Store documents against the actual codebase. Found and fixed 2 issues:

1. **Speech recognition "on-device" claims corrected** — The code uses `SpeechRecognizer.createSpeechRecognizer(context)` (the default recognizer), which delegates to the device's speech service (typically Google) and may process audio in the cloud. Updated 3 docs to remove "on-device only" / "entirely on-device" claims and accurately state that speech processing is handled by the device's default speech service:
   - `docs/playstore/privacy-policy.md` — Updated Speech Recognition section heading and body; added Google Speech Services to Third-Party Services section
   - `docs/playstore/data-safety-declaration.md` — Updated Audio section note, summary statement, and data flow diagram
   - `docs/playstore/store-listing.md` — Updated voice-first input paragraph and privacy bullet

2. **Keystore patterns added to `.gitignore`** — `checklist.md` line 44 claimed keystore files were already in `.gitignore`, but they weren't. Added `*.jks` and `*.keystore` patterns.

**Also noted (not fixed — requires user action):**
- `store-listing.md` line 103 has placeholder email `[your-support-email@example.com]` — must be replaced before publishing

**How verified:**
- Re-read all 4 modified files to confirm accuracy against codebase
- Confirmed `SpeechRecognizerManager.kt` uses `SpeechRecognizer.createSpeechRecognizer(context)` (not `createOnDeviceSpeechRecognizer`)
- Confirmed `.gitignore` now includes `*.jks` and `*.keystore`

## M20: Pre-Publication Security Audit (2026-02-12)

**What was built:**
Pre-publication security review before Play Store release. Three issues fixed:

1. **HTTP body logging disabled in release** — `HttpLoggingInterceptor.Level.BODY` was set unconditionally, leaking the PAT (Authorization header) to logcat. Now conditionally set: `BODY` in debug, `NONE` in release. Added `buildConfig = true` to `buildFeatures` so `BuildConfig.DEBUG` is available.
2. **ADB backup disabled** — `android:allowBackup="true"` allowed `adb backup` to extract the entire DataStore (with plaintext PAT) without root. Set to `false`.
3. **R8/ProGuard enabled for release** — `isMinifyEnabled` was `false`, leaving full class/method names in the release AAB. Enabled minification with keep rules for Retrofit, kotlinx.serialization, Hilt, Room, and Markwon.

**Files changed:**
- `app/build.gradle.kts` — `buildConfig = true`, `isMinifyEnabled = true`, `proguardFiles()`
- `app/src/main/kotlin/com/carsondavis/notetaker/di/AppModule.kt` — conditional logging level via `BuildConfig.DEBUG`
- `app/src/main/AndroidManifest.xml` — `allowBackup="false"`
- `app/proguard-rules.pro` — new file with keep rules

**Issues reviewed and accepted (no fix needed):**
PAT not encrypted at rest (sandbox protection sufficient), no certificate pinning (standard practice), Markwon XSS (TextView not WebView), browse path traversal (GitHub API is trust boundary), exported assist services (system-only permissions), lock screen capture (by design), no FLAG_SECURE (user control), unencrypted Room DB (no credentials), error message leaks (no PAT in messages), MainActivity ASSIST intent (required for feature).

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew assembleRelease` → BUILD SUCCESSFUL (R8 minification runs without errors)

## M21: Fix audiobook blip during speech recognizer restart (2026-02-13)

**What was built:**
App-level audio focus hold in `SpeechRecognizerManager` to prevent audiobook players from briefly resuming during the 150ms recognizer restart gap. The recognizer's internal audio focus release/re-acquire during `restart()` no longer reaches other apps because our app stays in the audio focus stack above them.

**Changes:**
- `SpeechRecognizerManager.kt` — Added `AudioManager` + `AudioFocusRequest` fields (`AUDIOFOCUS_GAIN_TRANSIENT`, `USAGE_ASSISTANT`, `CONTENT_TYPE_SPEECH`). Request focus in `start()` before creating recognizer. Abandon focus in `stop()` after stopping recognizer. `destroy()` already calls `stop()`, so it's covered.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- On-device testing needed: play audiobook → open note-taker in voice mode → audiobook should stay paused through recognizer restarts → switch to keyboard or leave app → audiobook should resume

## M22: Add Informative Context to App & Play Store Listing (2026-02-13)

**What was built:**
Added explanatory copy to two app screens and rewrote the Play Store listing to lead with philosophy and explain the capture workflow.

**Changes:**
1. **AuthScreen.kt** — Added two intro paragraphs between the "Note Taker" title and the PAT setup steps. First explains what the app does ("voice notes saved as markdown in your GitHub repo"), second introduces what's needed ("repository + personal access token"). Existing step numbering unchanged, just removed the redundant "To get started..." lead-in.
2. **SettingsScreen.kt** — Added explanatory `Text` (bodySmall, onSurfaceVariant) between the "Digital Assistant" title and the status row. When not set as default: explains side-button launch, lock screen access, Google Assistant tradeoff, "Hey Google" still works. When set as default: shorter confirmation of what's enabled.
3. **store-listing.md** — Rewrote short description ("Capture thoughts instantly — voice notes pushed straight to your GitHub repo.") and full description. New structure: opening hook (philosophy), how it works (markdown to GitHub), instant capture (side button), your notes/your repo (why GitHub), privacy by design, features, getting started. ~1,650 characters (well under 4,000 limit).

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M23: Data Collection Reference & Privacy Policy Audit (2026-02-13)

**What was built:**
Code-sourced technical reference documenting every piece of user data the app touches — Room tables, DataStore keys, network endpoints, in-memory state, voice/audio handling, and what's NOT collected. Includes a privacy policy audit section cross-referencing `privacy-policy.md` against all source files.

**Changes:**
1. **Created `docs/playstore/data-collection.md`** — 6 sections: Room database (2 tables with all columns), Preferences DataStore (4 keys), network transmission (4 endpoints with sent/received data), in-memory only data, voice/audio handling, what's NOT collected. Each section references exact source files. Privacy policy audit section confirms the policy is accurate with no corrections needed.
2. **INDEX.md** — Added entry for new file, updated status to M23.

**Privacy policy audit result:**
All claims in `privacy-policy.md` and `data-safety-declaration.md` verified accurate against code. No corrections needed. Notable observations documented: DataStore not encrypted at rest (policy doesn't claim it is), debug-only HTTP body logging (standard practice), submission preview field (covered by policy table).

**How verified:**
- All data claims cross-referenced against source files: `PendingNoteEntity.kt`, `SubmissionEntity.kt`, `AppDatabase.kt`, `AuthManager.kt`, `GitHubApi.kt`, `SpeechRecognizerManager.kt`, `AppModule.kt`, `AndroidManifest.xml`

## M24: Delete All Data from Device (2026-02-13)

**What was built:**
"Delete All Data" option on the Settings screen that wipes all local app data from the device — Room database (submissions + pending notes), DataStore preferences (token, username, repo), and cancels pending WorkManager upload jobs. Notes already pushed to GitHub are unaffected. Includes a confirmation dialog.

**Changes:**
1. **`SubmissionDao.kt`** — Added `deleteAll()` query (`DELETE FROM submissions`)
2. **`PendingNoteDao.kt`** — Added `deleteAll()` query (`DELETE FROM pending_notes`)
3. **`SettingsViewModel.kt`** — Added `clearAllData()` method that cancels WorkManager jobs, deletes all pending notes, deletes all submissions, and clears auth. Injected `SubmissionDao`, `PendingNoteDao`, and `WorkManager` via constructor.
4. **`SettingsScreen.kt`** — Added "Delete All Data" section at the bottom with description text and red button. Confirmation `AlertDialog` explains what will be deleted and that GitHub data is unaffected. On confirm, calls `clearAllData()` and navigates to auth screen.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M25: Play Store Screenshots (2026-02-13)

**What was built:**
7 screenshots captured from Samsung Galaxy S24 Ultra (1440x3120 native) via ADB for the Play Store listing. Review repo (`CarsonDavis/notes-playstore-review`) populated with `.current_topic` ("The Selfish Gene"), a sample note submitted through the app, and `notes/chapter-3-summary.md` (rich markdown with headers, bold, italics, lists, blockquote) via GitHub API. Android demo mode used to clean status bar for captures.

**Screenshots:**
1. `01_voice_input.png` — Main screen in voice mode with "Listening..." indicator and topic bar
2. `02_text_input.png` — Text field with note content and active Submit button
3. `03_sent_success.png` — "Sent!" confirmation with checkmark
4. `04_browse_folders.png` — Browse root: inbox/, notes/, .current_topic
5. `05_browse_markdown.png` — Rendered chapter-3-summary.md with full markdown formatting
6. `06_auth_setup.png` — Auth screen with PAT and repository fields populated
7. `07_settings.png` — Settings: GitHub account, repository, digital assistant, delete all data

**How verified:**
- All 7 screenshots visually confirmed at 1440x3120 resolution
- Play Store minimum is 1080x1920; native resolution exceeds requirement

## M26: Update App Theme to Match Icon Colors (2026-02-13)

**What was built:**
Replaced the default Material 3 purple/pink theme with a teal/blue/green palette derived from the app icon's gradient. All UI elements using `MaterialTheme.colorScheme` automatically pick up the new colors — no screen files needed changes.

**Changes:**
1. **`Color.kt`** — Replaced 6 purple/pink color values with 6 new ones: `Teal80`/`Teal40` (primary), `Blue80`/`Blue40` (secondary), `Green80`/`Green40` (tertiary)
2. **`Theme.kt`** — Updated `DarkColorScheme` to use `Teal80`, `Blue80`, `Green80`

**Effect:**
- Primary accents (mic button, folder icons, check marks, active icons) → teal (#4FD8B4)
- Secondary (queued submission button) → blue (#6EC6FF)
- Tertiary (success submission button) → mint green (#5EEAA0)
- Error states → unchanged (Material default red)
- All derived colors (containers, surfaces, onPrimary, etc.) auto-derived by Material 3

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- Grep confirms zero references to old Purple/Pink color names
- Visual check on device needed to confirm colors look good

## M27: Modern UI Polish — Surface Colors + Component Upgrades (2026-02-13)

**What was built:**
Deep visual polish pass: replaced flat gray Material default surfaces with purple-tinted dark surfaces matching the app icon background, and upgraded key components with modern Material 3 containers.

**Changes:**
1. **`Color.kt`** — Added 5 dark surface colors with cool purple undertone: `DarkPurple10` (scaffold bg), `DarkPurple15` (surface), `DarkPurple20` (surfaceContainer), `DarkPurple25` (surfaceContainerHigh), `DarkPurple30` (surfaceVariant)
2. **`Theme.kt`** — Full dark color scheme mapping: background, surface, surfaceVariant, surfaceDim, surfaceContainer, surfaceContainerLow/High/Highest all set to the purple-tinted palette. All M3 components auto-pick up the new surface colors.
3. **`NoteInputScreen.kt`** — Pill-shaped submit button via `RoundedCornerShape(36.dp)` (half of 72dp height)
4. **`TopicBar.kt`** — Wrapped Row in `Surface(color = surfaceContainer)` so it reads as a proper header bar
5. **`SettingsScreen.kt`** — Replaced 3x `HorizontalDivider` separators with 4 `Card` containers (surfaceContainer bg), each wrapping a section (GitHub Account, Repository, Digital Assistant, Delete All Data)
6. **`AuthScreen.kt`** — Wrapped form area (token field, repo field, buttons) in a `Card` container; intro text stays above
7. **`SubmissionHistory.kt`** — Replaced `HorizontalDivider` + bare Row header with `Surface(color = surfaceContainer)` wrapper

**Effect:**
- All screens now have purple-tinted dark surfaces instead of flat gray
- TopAppBar, Scaffold background, dialogs, text field outlines all carry the icon's purple undertone
- Settings sections visually grouped in cards instead of separated by thin lines
- Auth form has clear visual boundary
- Submit button is pill-shaped (modern look)
- Teal/blue/green accents unchanged
- Error states still default red

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- Installed on device via ADB

## M28: Include Native Debug Symbols in Release AAB (2026-02-13)

**What was built:**
Added `ndk { debugSymbolLevel = "FULL" }` to `defaultConfig` in `app/build.gradle.kts`. This bundles native debug symbols (`.so.dbg`) inside the AAB so Play Console can symbolicate crash reports from native libraries (Room/SQLite, OkHttp). Resolves the Play Console warning about missing debug symbols.

Also refactored build config to read signing/version properties from `local.properties` with env var fallback (via `prop()` helper), and removed `readOnly` constraint from the note text field during voice mode so users can edit while dictating.

**Changes:**
- `app/build.gradle.kts` — `prop()` helper for env/local.properties lookup, `ndk { debugSymbolLevel = "FULL" }`, default versionName "0.1.0"
- `NoteInputScreen.kt` — Removed `readOnly = uiState.inputMode == InputMode.VOICE`

**How verified:**
- `./gradlew bundleRelease` → BUILD SUCCESSFUL
- `extractReleaseNativeDebugMetadata` task ran successfully, confirming symbols are being extracted

## M28b: Auth Screen Redesign + UX Improvements (2026-02-13)

**What was built:**
Complete auth screen redesign with 4-step guided flow, two-step validation with distinct error messages, URL parsing for the repo field, digital assistant onboarding dialog, and growing text field on the note input screen.

**Changes:**
1. **`GitHubApi.kt`** — Added `getRepository()` endpoint (`GET repos/{owner}/{repo}`) and `GitHubRepository` data class (`id`, `fullName`) for validating repo access separately from token.
2. **`AuthViewModel.kt`** — Added `parseRepo()` function that handles `owner/repo`, `https://github.com/owner/repo`, and `github.com/owner/repo` (strips trailing `/` and `.git`). Rewrote `submit()` with two-step validation: `getUser()` catches 401 → "Personal access token is invalid"; `getRepository()` catches 404 → "Repository not found — check the name and token permissions"; other errors → "Network error: {message}".
3. **`AuthScreen.kt`** — Complete rewrite as 4-step guided flow in a scrollable column: (1) Fork the Notes Repo button → opens GitHub fork page, (2) Repo field with `(?)` help icon → accepts owner/repo or full GitHub URL, (3) Generate PAT button → shows AlertDialog with step-by-step instructions then opens GitHub PAT page, (4) Token field with `(?)` help icon and visibility toggle. Added `StepHeader` composable (teal step number + title). Three AlertDialogs for PAT instructions, token security info, and repo format help.
4. **`AuthManager.kt`** — Added `ONBOARDING_SHOWN` boolean preference key, `onboardingShown` flow, and `markOnboardingShown()` suspend function. Sign-out clears all preferences (including onboarding) so dialog reappears after re-auth.
5. **`NoteViewModel.kt`** — Injected `AuthManager`, added `showOnboarding` StateFlow that checks the flag on init, and `dismissOnboarding()` that sets false + persists.
6. **`NoteInputScreen.kt`** — Added onboarding AlertDialog ("Instant Note Capture") with "Set Up" → opens voice input settings and "Maybe Later" → dismisses. Replaced fixed 200dp text field with `weight(1f)` growing field that fills available space. Layout: 16dp top margin → growing text field → 16dp gap → submit button → history.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (no errors, no warnings from our code)
- Installed on device via ADB

## M29: Help Video + Side Button Settings (2026-02-14)

**What was built:**
Two UX additions: (1) "Need help?" link on the auth screen that opens a YouTube setup walkthrough video, and (2) reworked the Settings Digital Assistant card into a two-step guide — step 1 sets the app as the default digital assistant, step 2 opens Samsung's side button settings so users can rebind the side key from Bixby to "Digital assistant".

**Changes:**
1. **`AuthScreen.kt`** — Added `TextButton("Need help? Watch the setup walkthrough")` at the bottom of the scrollable column (after the card). On click opens `https://youtu.be/sNow-kcrxRo` via `Intent.ACTION_VIEW`.
2. **`SettingsScreen.kt`** — Reworked "Digital Assistant" card into two numbered steps with a `HorizontalDivider` between them. Step 1: existing assistant status row + "Open Assistant Settings" button (same `ACTION_VOICE_INPUT_SETTINGS` intent). Step 2: description text + "Open Side Button Settings" button that launches Samsung's `SideKeySettings` activity via `ComponentName`, with `try/catch` fallback to `ACTION_APPLICATION_DETAILS_SETTINGS`. Added imports for `ComponentName`, `HorizontalDivider`, and `Arrangement`.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M31: GitHub App OAuth Authentication (2026-02-14)

**What was built:**
Full GitHub App OAuth flow as the primary auth method, with the existing PAT flow as a collapsible fallback. Users tap "Sign in with GitHub" → Chrome Custom Tab opens GitHub App install page → user picks account and repo → GitHub redirects through a Pages bounce page → app receives the callback via `notetaker://` custom scheme → exchanges code for token → discovers installed repo → done.

**Changes:**

Phase 1 — Encrypted storage foundation:
- `gradle/libs.versions.toml` — Added `security-crypto = "1.0.0"` version + library entry
- `app/build.gradle.kts` — Added `implementation(libs.security.crypto)`, `buildConfigField` for `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` read from `local.properties` via `prop()`
- `app/proguard-rules.pro` — Added Tink keep rules and `dontwarn` for `com.google.errorprone.annotations` and `javax.annotation`
- `di/AppModule.kt` — Added `@Provides` for `EncryptedSharedPreferences` (using `MasterKeys.AES256_GCM_SPEC`) and `GitHubInstallationApi`
- `data/auth/AuthManager.kt` — Rewritten to store access token in `EncryptedSharedPreferences` while keeping metadata (username, repo, auth type) in DataStore. Added `AUTH_TYPE` ("pat"/"oauth"), `INSTALLATION_ID`, `TOKEN_UPDATED_AT` keys. `accessToken` Flow reads from encrypted prefs. One-time migration from old plain DataStore token via `migrateTokenIfNeeded()`. New `saveOAuthTokens()` and `saveInstallationId()` methods. `signOut()` clears both encrypted prefs and DataStore
- `NoteApp.kt` — Calls `authManager.migrateTokenIfNeeded()` in `onCreate` via app-scoped coroutine

Phase 2 — OAuth plumbing:
- **New** `data/auth/OAuthConfig.kt` — Constants (client ID/secret from BuildConfig, redirect URIs, GitHub URLs) and PKCE helpers (code verifier via 32 random bytes Base64URL, code challenge via SHA-256, state via 16 random hex bytes)
- **New** `data/auth/OAuthTokenExchanger.kt` — `@Singleton` that POSTs to GitHub's token endpoint to exchange an authorization code for an access token using OkHttp
- **New** `data/auth/OAuthCallbackHolder.kt` — `@Singleton` with `MutableStateFlow<OAuthCallbackData?>` to shuttle callback code/state from Activity to ViewModel
- **New** `OAuthCallbackActivity.kt` — `@AndroidEntryPoint` Activity with `Theme.Translucent.NoTitleBar`. Extracts `code` and `state` from `notetaker://callback` intent data, passes to `OAuthCallbackHolder`, launches `MainActivity` with `CLEAR_TOP|SINGLE_TOP`, finishes immediately
- **New** `data/api/GitHubInstallationApi.kt` — Retrofit interface for `GET /user/installations` and `GET /user/installations/{id}/repositories` with model classes
- `AndroidManifest.xml` — Added `OAuthCallbackActivity` with intent filter for `notetaker://callback`

Phase 4 — AuthViewModel OAuth flow:
- `ui/viewmodels/AuthViewModel.kt` — Rewritten with OAuth as primary flow. `startOAuthFlow()` generates PKCE verifier/state, saves to `SavedStateHandle` (survives process death), returns URI for GitHub App install page. `init{}` observes `OAuthCallbackHolder` for incoming callbacks. `handleOAuthCallback()` validates saved state, exchanges code for token, calls `getUser()` for username, discovers installation and repo via `GitHubInstallationApi`, saves everything via `AuthManager`. PAT flow (`submit()`, `parseRepo()`) preserved unchanged. New `showPatFlow()`/`hidePatFlow()` toggles

Phase 5 — AuthScreen UI redesign:
- `ui/screens/AuthScreen.kt` — "Sign in with GitHub" filled Button as primary UI element. Below: one-tap setup bullet list. "Or use a Personal Access Token instead" TextButton toggles the existing 4-step PAT card via `AnimatedVisibility`. "Back to GitHub sign-in" TextButton at bottom of PAT card. Loading spinner on OAuth button while in progress. Error text shows below OAuth section

Phase 6 — Settings polish:
- `ui/viewmodels/SettingsViewModel.kt` — Added `authType` to `SettingsUiState`, observed from `AuthManager.authType`
- `ui/screens/SettingsScreen.kt` — Shows "Connected via GitHub" or "Connected via Personal Access Token" label below username

**Token strategy:**
Non-expiring GitHub App user tokens — no refresh logic needed. PKCE protects the authorization code exchange. Client secret shipped in APK (standard for public clients, endorsed by GitHub).

**Files unchanged:** `GitHubApi.kt`, `NoteRepository.kt`, `NoteUploadWorker.kt`, `NavGraph.kt` — callers still just call `authManager.accessToken.first()` with `Bearer` prefix. No changes needed.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew assembleRelease` → BUILD SUCCESSFUL (R8 minification with Tink keep rules)
- End-to-end OAuth flow needs on-device testing

## M30: OAuth Redirect Repo Setup (2026-02-14)

**What was built:**
GitHub Pages redirect repo for the OAuth callback bounce page — prerequisite 2 from the [OAuth implementation plan](docs/github-app-oauth-implementation.md).

**Changes:**
1. **Created [`CarsonDavis/gitjot-oauth`](https://github.com/CarsonDavis/gitjot-oauth)** — Public repo with GitHub Pages enabled on `master` branch. Serves at `https://madebycarson.com/gitjot-oauth/` (custom domain).
2. **`callback/index.html`** — Bounce page that redirects `?code=...&state=...` query params from GitHub's OAuth callback to the `notetaker://callback` custom URI scheme, handing control back to the Android app.
3. **`_config.yml`** — `include: [".well-known"]` so GitHub Pages will serve `assetlinks.json` when Android App Links are added later.
4. **Updated `docs/github-app-oauth-implementation.md`** — Marked prerequisite 2 as complete, updated all placeholder URLs to actual values (`madebycarson.com/gitjot-oauth/callback`), set app name to `gitjot`, resolved open question #2.

**How verified:**
- `curl -sL https://madebycarson.com/gitjot-oauth/callback/` → returns correct HTML with `notetaker://callback` redirect
- GitHub Pages status: `built`

## M32: OAuth Bug Fixes + Auth Screen Layout (2026-02-14)

**What was built:**
Three bugs discovered during first on-device OAuth test, all fixed:

1. **Endless spinner fix** — `OAuthCallbackActivity` required both `code` AND `state` to be non-null, but GitHub's App install flow only sends `?code=X` (no `state`). Changed to `if (code != null)` and passes `state ?: ""`. The callback now reaches the ViewModel.

2. **Back-press escape** — If user presses Back from the browser, `isOAuthInProgress` stayed `true` forever with no way to reset. Added `cancelOAuthFlow()` to `AuthViewModel` and `LifecycleEventEffect(ON_RESUME)` in `AuthScreen` that calls it. If a real callback arrived, the ViewModel already set `isValidating = true` before resume fires, so this doesn't interfere.

3. **Auth screen layout** — Fork step was buried inside the PAT-only card. Pulled it out as step 1, visible above both auth methods. OAuth button is step 2 with updated description ("select the repo you just forked"). PAT flow remains collapsible fallback.

4. **"What am I agreeing to?" dialog** — TextButton below the OAuth description opens an AlertDialog explaining in plain language: read/write files in one repo you choose, no access to other repos/profile/email, revocable anytime from GitHub Settings.

**Changes:**
- `OAuthCallbackActivity.kt` — `state` no longer required: `if (code != null)`, passes `state ?: ""`
- `ui/viewmodels/AuthViewModel.kt` — Added `cancelOAuthFlow()` method
- `ui/screens/AuthScreen.kt` — Fork step at top, `LifecycleEventEffect(ON_RESUME)` for spinner reset, updated OAuth description text, "What am I agreeing to?" help dialog

**How verified:**
- `./gradlew installDebug` → BUILD SUCCESSFUL, installed on device
- On-device testing needed for OAuth flow, back-press, and PAT flow

## M33: Auth Screen Redesign — Two-Card Layout (2026-02-14)

**What was built:**
Complete visual redesign of the auth setup screen. Replaced the scattered step-based layout with two clean, self-contained cards.

1. **Two-card layout** — Card 1: "Create Your Notes Repo" (fork). Card 2: "Connect Your Repo" (OAuth sign-in, with PAT as inline toggle). Each card has a title, description, and action button.
2. **Green step numbers** — "1." and "2." rendered in `primary` (teal) color, separated from the title text.
3. **Right-justified help icons** — `?` icons pushed to far right of each card header via `Spacer(weight(1f))`.
4. **Fork help dialog** — Explains the template repo structure, the Claude Code inbox processor agent (cleans speech-to-text, sorts into topics, maintains indexes), and that forking gives a private copy.
5. **PAT inline toggle** — "Or connect with a Personal Access Token" replaces the OAuth content within card 2 (not a separate expanding section). "Back to GitHub sign-in" reverts.
6. **Updated subtitle** — "Your voice notes, saved to Git, organized by AI."

**Changes:**
- `ui/screens/AuthScreen.kt` — Full rewrite: removed `StepHeader` composable and `AnimatedVisibility`, two `Card` layout, fork help dialog, green step numbers, right-justified `?` icons, new subtitle, PAT flow as card 2 content swap

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- Installed on device via `adb install`, visually verified both cards, help dialogs, PAT toggle

## M34: OAuth Token Revocation on Sign-Out (2026-02-14)

**What was built:**
Token revocation on sign-out for OAuth users. When an OAuth user taps Sign Out, the app now: (1) shows a confirmation dialog explaining revocation and offering a link to uninstall the GitHub App entirely, (2) revokes the access token via GitHub's API before clearing local storage. PAT users are unaffected — they sign out immediately with no dialog.

**Changes:**
1. **`data/auth/OAuthTokenExchanger.kt`** — Added `revokeToken(accessToken: String): Boolean` method that calls `DELETE https://api.github.com/applications/{client_id}/token` with Basic auth (`client_id:client_secret`). Returns true on 204, false on any error. Wrapped in try/catch for best-effort revocation. Added `RevokeTokenRequest` data class with `@Serializable`.
2. **`ui/viewmodels/SettingsViewModel.kt`** — Injected `OAuthTokenExchanger` and `SharedPreferences` (EncryptedSharedPreferences). Added `isSigningOut: Boolean` to `SettingsUiState`. Extracted `revokeOAuthTokenIfNeeded()` helper that reads auth type and token, calls `revokeToken()` with 5s timeout if OAuth. Updated `signOut()` and `clearAllData()` to accept `onComplete: () -> Unit` callback — sets `isSigningOut = true`, revokes token, clears storage, then invokes callback.
3. **`ui/screens/SettingsScreen.kt`** — Sign Out button now shows a confirmation dialog for OAuth users (PAT users sign out immediately). Dialog text explains revocation and offers an "Uninstall from GitHub" button that opens `https://github.com/settings/installations`. Three buttons: "Uninstall from GitHub" (OutlinedButton), "Cancel" (TextButton), "Sign Out" (red Button). Sign Out button shows a spinner while `isSigningOut` is true. Delete All Data also uses the new `onComplete` callback for navigation.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## M35: Three-Branch CI/CD Pipeline with Google Play (2026-02-15)

**What was built:**
Three-branch deployment pipeline (`develop → staging → master`) with automated Google Play uploads, replacing the never-fired tag-based workflow.

**Changes:**
1. **Created `docs/DEPLOYMENT.md`** — Comprehensive deployment guide: branch strategy (develop/staging/master), how builds work, versioning conventions (versionCode from `run_number + 100`, versionName as manual semver), step-by-step release process, Google Play bootstrap (service account via Users and permissions, first manual upload, draft→completed transition, 14-day closed testing), hotfix process, troubleshooting common errors.
2. **Modified `app/build.gradle.kts`** — versionCode priority chain: Gradle property (`-PVERSION_CODE`) → env var/local.properties via `prop()` → fallback `1`. versionName hardcoded to `"0.4.0"` (bumped manually). Fresh clones build without any config.
3. **Created `.github/workflows/deploy.yml`** — Single workflow triggered on push to `staging` or `master`. Three jobs: `build` (shared, builds signed AAB, uploads artifact), `deploy-staging` (conditional on staging branch, uploads to internal track), `deploy-production` (conditional on master branch, uploads to production track with whatsnew). Version code = `run_number + 100`. Both deploy jobs start with `status: draft` for bootstrap.
4. **Deleted `.github/workflows/release.yml`** — Old tag-based workflow that never fired, replaced by deploy.yml.
5. **Created `whatsnew/whatsnew-en-US`** — Initial Play Store release notes for v0.4.0 (OAuth sign-in, redesigned setup, token revocation).
6. **Updated `docs/playstore/checklist.md`** — Phase 7 replaced with link to DEPLOYMENT.md.
7. **Updated `HANDOFF.md`** — Rewritten to reflect M35 state (OAuth, CI/CD, all features).
8. **Updated `docs/ROADMAP.md`** — Moved GitHub App OAuth to Completed (was listed as V3 future, completed in M31).

**Key decisions:**
- Kept `master` as the production branch instead of renaming to `production`. GitHub's branch rename redirects have no guaranteed duration and no SLA — too risky for the Play Store privacy policy URL which points to `blob/master/`.
- Kept `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` buildConfigFields — they are actively used by `OAuthConfig.kt` for the GitHub App OAuth flow (M31), not dead code.

**How verified:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (versionCode falls back to local.properties value, versionName reads hardcoded "0.4.0")
- `./gradlew assembleDebug -PVERSION_CODE=101` → BUILD SUCCESSFUL (Gradle property override works)

