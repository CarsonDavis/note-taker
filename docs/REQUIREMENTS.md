# Note Taker — Android App Requirements

## Overview

A minimal Android app for capturing notes (typed or voice-dictated via keyboard mic) and pushing them to a GitHub repository. Notes are processed by an LLM agent in the notes repo.

## System Context

The app is one piece of a three-part system:

1. **This app** — captures notes, pushes to GitHub
2. **notes repo** (`CarsonDavis/notes`) — stores raw and processed notes as markdown files and does LLM processing
3. **Processor** (separate, runs locally) — processes existing notes from signal messages

The app's only job is getting text into the `inbox/` folder of the notes repo.

## Core Flow

1. User long-presses side key (or opens app normally)
2. Note input screen appears immediately (over lock screen if triggered from keyguard)
3. App fetches current sticky topic from the repo and displays it at the top
4. User types a note (or dictates via keyboard mic button)
5. User taps submit
6. App creates a new file in `inbox/` via the GitHub API
7. Input field clears, brief success animation, user stays on the same screen ready for the next note
8. If launched from lock screen, pressing Back returns to the lock screen

## Screens

### 1. Note Input (Home)
The default and only landing screen. Always opens here.

- **Top bar**: current sticky topic (read-only), icon to navigate to Settings
- **Body**: single text input field, full height available
- **Bottom**: submit button
- On submit: push to GitHub, clear the field, show brief success feedback (animation/snackbar), stay on this screen
- On error: show error message (no network, auth failure, etc.)

### 2. Settings
Accessible from the top bar of the note input screen.

- GitHub account — sign in / sign out
- Repository — shown read-only (sign out to change)
- Digital assistant setup — detect if app holds `ROLE_ASSISTANT`, guide user to system settings if not

## Functional Requirements

### FR1: Note Input ✅
- Single text input field
- User types or uses the Android keyboard's built-in mic for voice-to-text
- No custom speech recognition — rely entirely on the keyboard
- Submit button to send the note
- On submit: clear field, brief success animation, stay on same screen

### FR2: Sticky Topic Display ✅
- On app open, fetch `.current_topic` from the configured repo via GitHub API
- Display the current topic at the top of the screen (read-only)
- If no topic is set, display "No topic set"
- Topic changes happen through note content (e.g., "new topic, Frankenstein"), processed by the LLM — the app does not need topic-setting UI

### FR3: Push to GitHub ✅
- On submit, create a new file in the `inbox/` directory of the configured repo
- Use the GitHub REST API (Contents API) to create the file
- **Filename**: ISO 8601 timestamp with local timezone (e.g., `2026-02-09T143200-0500.md`)
- **Content**: the raw note text, nothing else
- Handle errors gracefully (no network, auth failure, conflict)
- Show error feedback to user

### FR4: Submission History ✅
- After each successful push, save a local record: timestamp, first ~50 characters of note text, success/failure
- Display the last 5-10 submissions in a compact list on the note input screen (collapsible section below the input field)
- Stored locally (SharedPreferences or Room) — not fetched from GitHub
- Persists across app restarts

### FR5: Authentication & Configuration ✅
- Authenticate via **fine-grained Personal Access Token (PAT)**:
  1. On first run, app shows a setup screen with instructions
  2. User creates a fine-grained PAT on github.com scoped to their notes repo with Contents read/write
  3. User pastes the token and enters `owner/repo` in the app
  4. App validates the token via `GET /user`, stores token + repo via Preferences DataStore
- No OAuth infrastructure needed — no client ID, no device flow, no polling
- Token scoped to a single repository by GitHub's PAT UI
- To change repo or rotate token: sign out in settings and re-enter
- See `docs/adr/001-pat-over-oauth.md` for why PAT was chosen over OAuth

### FR6: Lock Screen Launch ✅
- App registers as an Android digital assistant via `VoiceInteractionService`
- Long-press side key launches note capture over the lock screen (no unlock required)
- Works from both locked and unlocked states
- See `APP-TRIGGER.md` for full implementation details

### FR7: Offline Note Queuing ✅
- Notes are always saved to a local Room queue before attempting GitHub push
- On success, note is removed from queue and recorded in submission history
- On failure (no network, API error), note stays queued and WorkManager schedules retry with network connectivity constraint
- Handles 422 conflict (duplicate filename) by appending suffix
- UI shows "Queued" animation on submit failure and pending count badge
- Count drops to 0 automatically when WorkManager succeeds in the background

### FR8: Browse Notes ✅
- Read-only repo browser accessible from the note input screen's top bar
- Fetches repo contents via GitHub REST API (Contents API)
- Displays folder/file tree — tap folder to navigate, tap file to view
- Renders markdown files via Markwon, non-markdown in monospace
- BackHandler for in-screen navigation (file → directory → parent → exit)
- From lock screen, browse icon triggers `requestDismissKeyguard()` → opens in MainActivity
- Empty state, error state with retry, loading indicator

### FR9: Voice-First Input
- App auto-starts speech recognition when NoteInputScreen appears (on resume)
- Words stream into the text field in real time (partial → finalized segments)
- Continuous listening: no timeout, auto-restarts between speech segments
- Mode switching: tap text field → keyboard mode; tap mic button → voice mode; text preserved across switches
- Permission denied or SpeechRecognizer unavailable → falls back to keyboard-only mode
- Submit while listening: stops voice, submits, clears, restarts voice
- App backgrounded: ON_PAUSE stops recognizer, ON_RESUME restarts
- App always returns to NoteInputScreen when brought to foreground

### Lock Screen Security ✅
Two-tier model (same pattern as the camera app):
1. **Quick capture (no auth)** — note input works over the lock screen
2. **Full app access (auth required)** — settings requires `requestDismissKeyguard()` to prompt for biometric/PIN

## Non-Functional Requirements

- **Platform**: Android only, Kotlin, Jetpack Compose
- **Min SDK**: API 29 (Android 10) — covers all Galaxy S21+ devices
- **Target device**: Samsung Galaxy S24 Ultra (SM-S928U1), Android 16, OneUI 8.0
- **Theme**: Dark mode only
- **Simplicity**: Minimal UI — this is a capture tool, not a note management app
- **Speed**: App should open and be ready to type within 1-2 seconds
- **Security**: HTTP logging disabled in release builds, ADB backup disabled, R8 minification enabled (M20 audit)

## Out of Scope

- Note processing, cleaning, or organization (handled by the notes repo's LLM agent)
- Topic management UI (topics are set via note content)
- Smarter topic refresh (see `ROADMAP.md`)
- Web UI
- Multi-user support
- iOS

## Open Questions

(none currently)
