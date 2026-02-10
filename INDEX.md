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

- `app/build.gradle.kts` — App module build config (SDK versions, dependencies)
- `app/src/main/AndroidManifest.xml` — App manifest (activities, services, permissions)
- `app/src/main/res/values/strings.xml` — String resources
- `app/src/main/res/xml/assist_service.xml` — VoiceInteractionService config

### Source: `app/src/main/kotlin/com/carsondavis/notetaker/`

- `NoteApp.kt` — `@HiltAndroidApp` Application class
- `MainActivity.kt` — Main launcher activity, hosts NavGraph
- `NoteCaptureActivity.kt` — Lock screen entry (showWhenLocked, turnScreenOn)

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
- `AppDatabase.kt` — Room database definition
- `SubmissionDao.kt` — History queries (insert, getRecent)
- `SubmissionEntity.kt` — Submission history table

#### `data/repository/`
- `NoteRepository.kt` — Data access: submit notes, fetch topic

#### `di/`
- `AppModule.kt` — Hilt providers (Retrofit, OkHttp, Room, DAO)

#### `ui/components/`
- `TopicBar.kt` — Sticky topic display + settings gear
- `SubmissionHistory.kt` — Collapsible recent submissions list

#### `ui/navigation/`
- `NavGraph.kt` — Compose Navigation with type-safe routes (Auth, Note, Settings)

#### `ui/screens/`
- `NoteInputScreen.kt` — Main note input (text field, submit, history, snackbar)
- `AuthScreen.kt` — PAT setup screen: token + repo input, validation
- `SettingsScreen.kt` — Sign out, read-only repo display, digital assistant role detection

#### `ui/viewmodels/`
- `NoteViewModel.kt` — Note input state, submit, topic fetch
- `AuthViewModel.kt` — PAT validation + setup flow
- `SettingsViewModel.kt` — Settings state, sign out, role check

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

## Status

M1-M11 complete. All features implemented, compiling, and verified on device (SM-S928U1, Android 16): note input with success animation, Room history, PAT-based auth, push to GitHub, sticky topic with post-submit refresh, settings, lock screen launch via VoiceInteractionService (registered as digital assistant).
