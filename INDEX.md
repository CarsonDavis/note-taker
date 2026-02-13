# Note Taker — Android App

Minimal Android app for capturing notes and pushing them to a GitHub repo via the REST API. Part of a three-part system (app → notes repo with LLM processing → signal-messages-processor for historical data).

## Project Files

- `settings.gradle.kts` — Gradle settings (repos, project name, modules)
- `build.gradle.kts` — Root build file (plugin declarations)
- `gradle.properties` — Gradle JVM args and Android settings
- `gradle/libs.versions.toml` — Version catalog (all dependency versions)
- `.gitignore` — Git ignore rules
- `IMPLEMENTATION_LOG.md` — Build log for each milestone
- `HANDOFF.md` — Current state, blockers, and next steps for handoff

### `app/` — Android Application Module

- `app/build.gradle.kts` — App module build config (SDK versions, dependencies, R8 minification)
- `app/proguard-rules.pro` — R8/ProGuard keep rules for Retrofit, kotlinx.serialization, Hilt, Room, Markwon
- `app/src/main/AndroidManifest.xml` — App manifest (activities, services, permissions)
- `app/src/main/res/values/strings.xml` — String resources
- `app/src/main/res/xml/assist_service.xml` — VoiceInteractionService config

### Source: `app/src/main/kotlin/com/carsondavis/notetaker/`

- `NoteApp.kt` — `@HiltAndroidApp` Application class
- `MainActivity.kt` — Main launcher activity, hosts NavGraph
- `NoteCaptureActivity.kt` — Lock screen entry (showWhenLocked, turnScreenOn)

#### `speech/`
- `SpeechRecognizerManager.kt` — Encapsulates Android SpeechRecognizer with continuous listening, auto-restart, app-level audio focus hold, and state flows

#### `assist/` — VoiceInteractionService (digital assistant registration)
- `NoteAssistService.kt` — Handles lock screen launch (`onLaunchVoiceAssistFromKeyguard`)
- `NoteAssistSessionService.kt` — Session factory (boilerplate)
- `NoteAssistSession.kt` — Handles unlocked launch path
- `NoteRecognitionService.kt` — Stub RecognitionService (required by Android 16 for valid VoiceInteractionService)

#### `data/api/`
- `GitHubApi.kt` — Retrofit interface: user validation, contents API

#### `data/auth/`
- `AuthManager.kt` — Token + repo storage via Preferences DataStore

#### `data/local/`
- `AppDatabase.kt` — Room database definition (v2: submissions + pending_notes)
- `SubmissionDao.kt` — History queries (insert, getRecent)
- `SubmissionEntity.kt` — Submission history table
- `PendingNoteEntity.kt` — Offline queue table (text, filename, status)
- `PendingNoteDao.kt` — Queue queries (insert, getAllPending, getPendingCount, updateStatus, delete)

#### `data/repository/`
- `NoteRepository.kt` — Data access: queue-first submit, fetch topic, browse directory/file contents

#### `data/worker/`
- `NoteUploadWorker.kt` — HiltWorker for retrying pending note uploads when network is available

#### `di/`
- `AppModule.kt` — Hilt providers (Retrofit, OkHttp, Room, DAOs, WorkManager)

#### `ui/components/`
- `TopicBar.kt` — Sticky topic display + browse icon + settings gear
- `SubmissionHistory.kt` — Collapsible recent submissions list
- `MarkdownContent.kt` — Markwon-based markdown renderer wrapped in AndroidView for Compose

#### `ui/navigation/`
- `NavGraph.kt` — Compose Navigation with type-safe routes (Auth, Note, Settings, Browse)

#### `ui/screens/`
- `NoteInputScreen.kt` — Main note input (text field, submit with queued state, pending count, history)
- `AuthScreen.kt` — PAT setup screen: token + repo input, validation
- `SettingsScreen.kt` — Sign out, read-only repo display, digital assistant role detection, delete all data
- `BrowseScreen.kt` — Read-only repo browser: directory listing, file viewer with markdown rendering

#### `ui/viewmodels/`
- `NoteViewModel.kt` — Note input state, queue-first submit, pending count, topic fetch
- `AuthViewModel.kt` — PAT validation + setup flow
- `SettingsViewModel.kt` — Settings state, sign out, role check, delete all data
- `BrowseViewModel.kt` — Browse state: directory navigation, file viewing

#### `ui/theme/`
- `Theme.kt` — Dark-only Material 3 theme
- `Color.kt` — Color definitions
- `Type.kt` — Typography

## Docs

- `docs/REQUIREMENTS.md` — Functional and non-functional requirements
- `docs/WIREFRAMES.md` — ASCII wireframes for all screens and states
- `docs/PAT-SETUP.md` — User guide for creating a fine-grained GitHub PAT
- `docs/APP-TRIGGER.md` — Lock screen launch via VoiceInteractionService
- `docs/ROADMAP.md` — Future features (v2+)
- `docs/adr/001-pat-over-oauth.md` — ADR: why fine-grained PAT over OAuth/GitHub App
- `docs/github-app-oauth-implementation.md` — Implementation plan for replacing PAT auth with GitHub App OAuth
- `docs/research/` — Research on assist API, lock screen, power button, GitHub OAuth, GitHub App OAuth Option B

### Docs: Play Store

- `docs/playstore/checklist.md` — Step-by-step Play Store publishing checklist (7 phases)
- `docs/playstore/store-listing.md` — Store listing content: title, descriptions, keywords, visual asset specs
- `docs/playstore/data-safety-declaration.md` — Data safety form answers for Play Console
- `docs/playstore/privacy-policy.md` — Privacy policy for Play Store listing
- `docs/playstore/delete-your-data.md` — User-facing data deletion instructions (linked from privacy policy, used as Play Store "Delete data URL")
- `docs/playstore/data-collection.md` — Code-sourced technical reference of all user data the app touches, plus privacy policy audit
- `docs/playstore/app-access-instructions.md` — Play Console "App access" credentials for reviewer (PAT + review repo)

### Docs: Play Store Graphics

- `docs/playstore/images/GitJot-icon-512x512.png` — App icon (512x512)
- `docs/playstore/images/feature-graphic.png` — Feature graphic (1024x500)

### Docs: Play Store Screenshots

- `docs/playstore/screenshots/01_voice_input.png` — Voice input mode with "Listening..." indicator (1440x3120)
- `docs/playstore/screenshots/02_text_input.png` — Note input with text and Submit button (1440x3120)
- `docs/playstore/screenshots/03_sent_success.png` — "Sent!" success confirmation state (1440x3120)
- `docs/playstore/screenshots/04_browse_folders.png` — Browse view: root directory listing (1440x3120)
- `docs/playstore/screenshots/05_browse_markdown.png` — Browse view: rendered markdown file (1440x3120)
- `docs/playstore/screenshots/06_auth_setup.png` — Auth setup screen with fields populated (1440x3120)
- `docs/playstore/screenshots/07_settings.png` — Settings screen with account, repo, assistant, data deletion (1440x3120)

### CI/CD

- `.github/workflows/release.yml` — GitHub Actions: build signed AAB and upload to Google Play on `v*` tag push

## Status

M1-M25 complete. V1 features (M1-M11) verified on device. V2 adds offline note queuing with WorkManager retry (M12-M14) and a read-only repo browser with markdown rendering (M15-M16). M17 adds voice-first note input with auto-start speech recognition, continuous listening, and mode switching. M18 adds Play Store publishing docs, release signing config, and GitHub Actions CI/CD. M19 validates Play Store docs against codebase — corrects speech recognition "on-device" claims and adds keystore patterns to .gitignore. M20 is a pre-publication security audit: disables HTTP body logging in release, disables ADB backup, enables R8 minification with ProGuard rules. M21 fixes audiobook blip during speech recognizer restart by holding app-level audio focus for the entire voice session. M22 adds informative context to the auth screen and settings screen, and rewrites the Play Store listing to lead with the capture philosophy. M23 adds a code-sourced data collection reference and audits the privacy policy against the codebase. M24 adds "Delete All Data" to the settings screen — wipes Room DB, DataStore, and WorkManager jobs with a confirmation dialog. All compiling.
