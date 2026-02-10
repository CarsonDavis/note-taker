# Note Taker — Android App

Minimal Android app for capturing notes and pushing them to a GitHub repo via the REST API. Part of a three-part system (app → notes repo with LLM processing → signal-messages-processor for historical data).

## Project Files

- `settings.gradle.kts` — Gradle settings (repos, project name, modules)
- `build.gradle.kts` — Root build file (plugin declarations)
- `gradle.properties` — Gradle JVM args and Android settings
- `gradle/libs.versions.toml` — Version catalog (all dependency versions)
- `.gitignore` — Git ignore rules
- `IMPLEMENTATION_LOG.md` — Build log for each milestone

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

#### `data/api/`
- `GitHubApi.kt` — Retrofit interface: device flow, user/repos, contents API

#### `data/auth/`
- `AuthManager.kt` — Token + repo storage via Preferences DataStore

#### `data/local/`
- `AppDatabase.kt` — Room database definition
- `SubmissionDao.kt` — History queries (insert, getRecent)
- `SubmissionEntity.kt` — Submission history table

#### `data/repository/`
- `NoteRepository.kt` — Data access: submit notes, fetch topic, get repos

#### `di/`
- `AppModule.kt` — Hilt providers (Retrofit, OkHttp, Room, DAO)

#### `ui/components/`
- `TopicBar.kt` — Sticky topic display + settings gear
- `SubmissionHistory.kt` — Collapsible recent submissions list

#### `ui/navigation/`
- `NavGraph.kt` — Compose Navigation with type-safe routes (Auth, Note, Settings)

#### `ui/screens/`
- `NoteInputScreen.kt` — Main note input (text field, submit, history, snackbar)
- `AuthScreen.kt` — Device flow auth: welcome → device code → repo selection
- `SettingsScreen.kt` — Sign out, repo picker, digital assistant role detection

#### `ui/viewmodels/`
- `NoteViewModel.kt` — Note input state, submit, topic fetch
- `AuthViewModel.kt` — Device flow auth + polling
- `SettingsViewModel.kt` — Settings state, sign out, repo picker, role check

#### `ui/theme/`
- `Theme.kt` — Dark-only Material 3 theme
- `Color.kt` — Color definitions
- `Type.kt` — Typography

## Docs

- `docs/REQUIREMENTS.md` — Functional and non-functional requirements
- `docs/WIREFRAMES.md` — ASCII wireframes for all screens and states
- `docs/APP-TRIGGER.md` — Lock screen launch via VoiceInteractionService
- `docs/ROADMAP.md` — Future features (v2+)
- `docs/research/` — Research on assist API, lock screen, power button, GitHub OAuth

## Status

M1-M8 complete. All features implemented and compiling: note input, Room history, GitHub device flow auth, push to GitHub, sticky topic, settings, lock screen launch via VoiceInteractionService. `./gradlew assembleDebug` builds cleanly with 0 warnings.

**Current blocker:** Auth is mid-migration from OAuth App to GitHub App for fine-grained repo permissions. See `HANDOFF.md` for full details and next steps.
