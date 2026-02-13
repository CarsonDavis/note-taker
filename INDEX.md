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
- `SpeechRecognizerManager.kt` — Encapsulates Android SpeechRecognizer with continuous listening, auto-restart, and state flows

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
- `SettingsScreen.kt` — Sign out, read-only repo display, digital assistant role detection
- `BrowseScreen.kt` — Read-only repo browser: directory listing, file viewer with markdown rendering

#### `ui/viewmodels/`
- `NoteViewModel.kt` — Note input state, queue-first submit, pending count, topic fetch
- `AuthViewModel.kt` — PAT validation + setup flow
- `SettingsViewModel.kt` — Settings state, sign out, role check
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
- `docs/research/` — Research on assist API, lock screen, power button, GitHub OAuth

### Docs: Play Store

- `docs/playstore/checklist.md` — Step-by-step Play Store publishing checklist (7 phases)
- `docs/playstore/store-listing.md` — Store listing content: title, descriptions, keywords, visual asset specs
- `docs/playstore/data-safety-declaration.md` — Data safety form answers for Play Console
- `docs/playstore/privacy-policy.md` — Privacy policy for Play Store listing

### CI/CD

- `.github/workflows/release.yml` — GitHub Actions: build signed AAB and upload to Google Play on `v*` tag push

## Status

M1-M20 complete. V1 features (M1-M11) verified on device. V2 adds offline note queuing with WorkManager retry (M12-M14) and a read-only repo browser with markdown rendering (M15-M16). M17 adds voice-first note input with auto-start speech recognition, continuous listening, and mode switching. M18 adds Play Store publishing docs, release signing config, and GitHub Actions CI/CD. M19 validates Play Store docs against codebase — corrects speech recognition "on-device" claims and adds keystore patterns to .gitignore. M20 is a pre-publication security audit: disables HTTP body logging in release, disables ADB backup, enables R8 minification with ProGuard rules. All compiling.
