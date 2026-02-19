# Note Taker — Android App

Minimal Android app for capturing notes and pushing them to a GitHub repo via the REST API. Part of a three-part system (app → notes repo with LLM processing → signal-messages-processor for historical data).

## Project Files

- `settings.gradle.kts` — Gradle settings (repos, project name, modules)
- `build.gradle.kts` — Root build file (plugin declarations)
- `gradle.properties` — Gradle JVM args and Android settings
- `gradle/libs.versions.toml` — Version catalog (all dependency versions)
- `.gitignore` — Git ignore rules
- `CHANGELOG.md` — User-facing release notes by version
- `IMPLEMENTATION_LOG.md` — Build log for each milestone
- `HANDOFF.md` — Current state, blockers, and next steps for handoff

### `app/` — Android Application Module

- `app/build.gradle.kts` — App module build config (SDK versions, dependencies, R8 minification)
- `app/proguard-rules.pro` — R8/ProGuard keep rules for Retrofit, kotlinx.serialization, Hilt, Room, Markwon
- `app/src/main/AndroidManifest.xml` — App manifest (activities, services, permissions)
- `app/src/main/res/values/strings.xml` — String resources
- `app/src/main/res/xml/assist_service.xml` — VoiceInteractionService config

### Source: `app/src/main/kotlin/com/carsondavis/notetaker/`

- `NoteApp.kt` — `@HiltAndroidApp` Application class, runs token migration on startup
- `MainActivity.kt` — Main launcher activity, hosts NavGraph
- `OAuthCallbackActivity.kt` — Translucent activity that receives `notetaker://callback` redirects from GitHub OAuth, passes code to OAuthCallbackHolder (state optional — GitHub App install flow omits it), bounces to MainActivity
- `NoteCaptureActivity.kt` — Lock screen entry (showWhenLocked, turnScreenOn)

#### `speech/`
- `SpeechRecognizerManager.kt` — Encapsulates Android SpeechRecognizer with continuous listening, auto-restart, app-level audio focus hold, and state flows

#### `assist/` — VoiceInteractionService (digital assistant registration)
- `NoteAssistService.kt` — Handles lock screen launch (`onLaunchVoiceAssistFromKeyguard`)
- `NoteAssistSessionService.kt` — Session factory (boilerplate)
- `NoteAssistSession.kt` — Handles unlocked launch path
- `NoteRecognitionService.kt` — Stub RecognitionService (required by Android 16 for valid VoiceInteractionService)

#### `data/api/`
- `GitHubApi.kt` — Retrofit interface: user validation, repository validation, contents API
- `GitHubInstallationApi.kt` — Retrofit interface for GitHub App installation/repo discovery (`/user/installations`, `/user/installations/{id}/repositories`)

#### `data/auth/`
- `AuthManager.kt` — Token + repo + auth type storage. Tokens in EncryptedSharedPreferences, metadata in DataStore. Supports both PAT and OAuth auth types with one-time migration from plain DataStore. Sign-out preserves installation_id for re-auth; clearAllData() is a full factory reset
- `OAuthConfig.kt` — OAuth constants (client ID/secret from BuildConfig, URLs) and PKCE helpers (code verifier, code challenge, state generation)
- `OAuthTokenExchanger.kt` — Exchanges OAuth authorization codes for access tokens via POST to GitHub's token endpoint; revokes tokens via DELETE to GitHub's application token endpoint
- `OAuthCallbackHolder.kt` — Singleton StateFlow that shuttles OAuth callback data (code + state) from OAuthCallbackActivity to AuthViewModel

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
- `AppModule.kt` — Hilt providers (Retrofit, OkHttp, Room, DAOs, WorkManager, EncryptedSharedPreferences, GitHubInstallationApi)

#### `ui/components/`
- `TopicBar.kt` — Sticky topic display + browse icon + settings gear
- `SubmissionHistory.kt` — Collapsible recent submissions list
- `MarkdownContent.kt` — Markwon-based markdown renderer wrapped in AndroidView for Compose

#### `ui/navigation/`
- `NavGraph.kt` — Compose Navigation with type-safe routes (Auth, Note, Settings, Browse)

#### `ui/screens/`
- `NoteInputScreen.kt` — Main note input (growing text field, submit with queued state, pending count, history, onboarding dialog)
- `AuthScreen.kt` — Auth setup screen: two-card layout (1. Fork repo, 2. Connect repo). OAuth "Sign in with GitHub" as primary with "What am I agreeing to?" dialog, PAT as inline content swap within card 2. Fork help dialog explains template repo + Claude Code agent. Green step numbers, right-justified `?` icons. `LifecycleEventEffect(ON_RESUME)` resets OAuth spinner on back-press. "Need help?" video link at bottom
- `SettingsScreen.kt` — Two-card layout: "Device Connection" (username, repo, auth type, helper text about easy reconnect, red Disconnect button with confirmation dialog) and "GitJot on GitHub" (OAuth only — permissions description, Manage on GitHub button). Plus two-step digital assistant setup and delete all data
- `BrowseScreen.kt` — Read-only repo browser: directory listing, file viewer with markdown rendering

#### `ui/viewmodels/`
- `NoteViewModel.kt` — Note input state, queue-first submit, pending count, topic fetch, onboarding
- `AuthViewModel.kt` — OAuth flow (PKCE, callback handling, token exchange, repo discovery), dual-path `startOAuthFlow()` (authorize URL for returning users, install URL for first-time), state validation, stale installation recovery, `cancelOAuthFlow()` for back-press reset with install hint (guarded against race with `isValidating`), PAT validation fallback with URL parsing, repo selection dialog when multiple repos available
- `SettingsViewModel.kt` — Settings state, disconnect with OAuth token revocation, role check, delete all data, auth type display, pending note count
- `BrowseViewModel.kt` — Browse state: directory navigation, file viewing

#### `ui/theme/`
- `Theme.kt` — Dark-only Material 3 theme (teal/blue/green accents, purple-tinted dark surfaces)
- `Color.kt` — Teal/blue/green accent colors + dark purple surface palette matching app icon
- `Type.kt` — Typography

## Docs

- `docs/REQUIREMENTS.md` — Functional and non-functional requirements
- `docs/WIREFRAMES.md` — ASCII wireframes for all screens and states
- `docs/PAT-SETUP.md` — User guide for creating a fine-grained GitHub PAT
- `docs/APP-TRIGGER.md` — Lock screen launch via VoiceInteractionService
- `docs/ROADMAP.md` — Future features (v2+)
- `docs/adr/001-pat-over-oauth.md` — ADR: why fine-grained PAT over OAuth/GitHub App
- `docs/auth-flows.md` — Reference for all auth flows (OAuth, PAT, sign-out, re-auth, recovery) organized by user intent, with known gaps
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

- `.github/workflows/deploy.yml` — GitHub Actions: build signed AAB and upload to Google Play on push to `staging` (closed testing) or `master` (production track)

### Deployment

- `docs/DEPLOYMENT.md` — Deployment guide: branch strategy, versioning, release process, Google Play bootstrap, troubleshooting
- `whatsnew/whatsnew-en-US` — Play Store release notes (plain text, max 500 chars, user-facing)

## Status

M1-M42 complete. V1 features (M1-M11) verified on device. V2 adds offline note queuing with WorkManager retry (M12-M14) and a read-only repo browser with markdown rendering (M15-M16). M17 adds voice-first note input with auto-start speech recognition, continuous listening, and mode switching. M18 adds Play Store publishing docs, release signing config, and GitHub Actions CI/CD. M19 validates Play Store docs against codebase — corrects speech recognition "on-device" claims and adds keystore patterns to .gitignore. M20 is a pre-publication security audit: disables HTTP body logging in release, disables ADB backup, enables R8 minification with ProGuard rules. M21 fixes audiobook blip during speech recognizer restart by holding app-level audio focus for the entire voice session. M22 adds informative context to the auth screen and settings screen, and rewrites the Play Store listing to lead with the capture philosophy. M23 adds a code-sourced data collection reference and audits the privacy policy against the codebase. M24 adds "Delete All Data" to the settings screen — wipes Room DB, DataStore, and WorkManager jobs with a confirmation dialog. M26 updates the app theme from default purple to a teal/blue/green palette matching the app icon. M27 adds purple-tinted dark surfaces, card-wrapped settings/auth sections, pill submit button, and surface-backed headers. M28b redesigns the auth screen as a 4-step guided flow with URL parsing, two-step validation (distinct errors for bad token vs bad repo), help dialogs, digital assistant onboarding, and a growing text field. M30 creates the `CarsonDavis/gitjot-oauth` GitHub Pages redirect repo (OAuth callback bounce page) and updates the OAuth implementation plan with actual URLs. M31 implements GitHub App OAuth — "Sign in with GitHub" button as primary auth, PAT as collapsible fallback, EncryptedSharedPreferences for token storage, token migration, PKCE-protected OAuth flow. M32 fixes three OAuth bugs: makes `state` optional in callback (GitHub App install flow omits it), adds ON_RESUME spinner reset for back-press escape, pulls fork step above both auth methods. M33 redesigns the auth screen as two clean cards (Fork + Connect) with green step numbers, right-justified help icons, fork help dialog explaining the Claude Code agent, PAT as inline content swap, and updated subtitle. M34 adds OAuth token revocation on sign-out — confirmation dialog with "Uninstall from GitHub" link, API-based token revocation with 5s timeout, spinner during sign-out, PAT users unaffected. M35 establishes three-branch CI/CD pipeline (develop → staging → master) with automated Google Play deployment, version code from run_number, and deployment guide. M36 fixes OAuth re-authentication flow — sign-out preserves installation_id so returning users get the authorize URL instead of the install URL, with stale installation recovery and factory reset via "Delete All Data". M37 adds auth failure handling: worker stops retrying on 401/403, pending notes warning on sign-out, reactive "Session expired" errors in note submit and browse. M38 improves OAuth UX text: two-tap install hint, better error messages for no-repos and stale-installation scenarios. M39 adds "Manage Repository Access" button in Settings for OAuth users. M40 adds repo selection dialog when GitHub App has access to multiple repositories. M41 renames Sign Out → Disconnect with honest terminology (removes credentials from device, GitJot stays on GitHub), renames "Manage Repository Access" → "Manage on GitHub", simplifies disconnect dialog (no more "Uninstall from GitHub" link — handled by Manage on GitHub button), and adds `isValidating` guard to `cancelOAuthFlow()` race condition. All compiling (debug + release).
