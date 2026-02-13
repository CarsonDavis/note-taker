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
