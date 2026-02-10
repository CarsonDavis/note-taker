# Note Taker — Android App

Minimal Android app for capturing notes and pushing them to a GitHub repo via the REST API. Part of a three-part system (app → notes repo with LLM processing → signal-messages-processor for historical data).

## Files

- `docs/REQUIREMENTS.md` — Functional and non-functional requirements for the app
- `docs/APP-TRIGGER.md` — How the app launches from lock screen (digital assistant registration via VoiceInteractionService)
- `docs/research/` — Research into Android launch mechanisms:
  - `android-assist-api/` — Using the Assist API for quick launch
  - `android-lock-screen-launch/` — Launching from the lock screen
  - `android-power-button-triple-press/` — Triple-press power button shortcut

## Status

Pre-development. Requirements being finalized, no code yet.
