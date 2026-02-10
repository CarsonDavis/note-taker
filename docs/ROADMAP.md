# Note Taker — Roadmap

## V2 Features

### Browse Notes
A secondary screen for viewing organized notes directly in the app.

- Accessible from the top bar of the note input screen
- Fetches repo contents via GitHub REST API (Contents API)
- Displays folder/file tree — tap a folder to open it, tap a file to view it
- Renders markdown files in a readable format
- Read-only — no editing from the app
- If launched from lock screen, requires authentication via `requestDismissKeyguard()`

### Offline Note Queuing
When the device has no network connectivity, notes should be queued locally and pushed automatically when connectivity returns.

- Store pending notes in a local Room database
- Use WorkManager to schedule retry with network constraint
- Show a badge/indicator on the note input screen when notes are queued
- Handle conflicts if the same timestamp filename already exists on push (append suffix)
- Clear queued notes after successful push
- Show queued note count somewhere visible so the user knows they're pending
