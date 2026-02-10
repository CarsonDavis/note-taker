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

1. User opens app
2. App fetches current sticky topic from the notes repo and displays it
3. User types a note (or dictates via keyboard mic button)
4. User submits
5. App creates a new file in `notes/inbox/` via the GitHub API
6. File is named with an ISO timestamp, content is the raw text

## Functional Requirements

### FR1: Note Input
- Single text input field
- User types or uses the Android keyboard's built-in mic for voice-to-text
- No custom speech recognition — rely entirely on the keyboard
- Submit button to send the note

### FR2: Sticky Topic Display
- On app open, fetch `.current_topic` from the notes repo via GitHub API
- Display the current topic at the top of the screen (read-only in the app)
- If no topic is set, display "No topic set"
- Topic changes happen through note content (e.g., "new topic, Frankenstein"), processed by the LLM — the app does not need topic-setting UI

### FR3: Push to GitHub
- On submit, create a new file in the `inbox/` directory of the notes repo
- Use the GitHub REST API (Contents API) to create the file
- **Filename**: ISO 8601 timestamp (e.g., `2026-02-09T143200.md`)
- **Content**: the raw note text, nothing else
- Handle errors gracefully (no network, auth failure, conflict)
- Show confirmation or error feedback to user

### FR4: Authentication
- GitHub personal access token (PAT) stored locally on the device
- Token needs `repo` scope for the notes repo
- First-run setup screen to enter/paste the token
- Token stored in Android encrypted shared preferences

## Non-Functional Requirements

- **Platform**: Android only
- **Simplicity**: Minimal UI — this is a capture tool, not a note management app
- **Speed**: App should open and be ready to type within 1-2 seconds
- **Offline**: If offline, queue the note locally and push when connectivity returns
- **Launch**: Should be quickly accessible (home screen shortcut at minimum; explore assist API / lock screen launch later)

## Out of Scope

- Note processing, cleaning, or organization (handled by the notes repo's LLM agent)
- Topic management UI (topics are set via note content)
- Browsing or reading existing notes
- Web UI
- Multi-user support
- iOS

## Open Questions

- [ ] Should the app show a brief history of recent submissions (last 5-10) for confidence that notes went through?
- [ ] Should timestamps include timezone info or always use UTC?
- [ ] Exact Android minimum SDK version
