# Note Taker App — Handoff Document

## What Exists

A fully compiling Android app at `/Users/cdavis/github/note-taker/note-taker/` with all features implemented through M9. `./gradlew assembleDebug` builds cleanly. The APK installs and launches on the target device (Samsung Galaxy S24 Ultra, Android 16).

See `INDEX.md` for the complete file listing and `IMPLEMENTATION_LOG.md` for build details per milestone.

### Tech Stack
- AGP 9.0.0, Kotlin 2.2.10 (built-in), Gradle wrapper 9.1.0
- Jetpack Compose (BOM 2026.01.01), Material 3, dark theme only
- Hilt 2.59.1, Room 2.8.4, Retrofit 3.0.0, OkHttp 5.3.0, Navigation 2.9.7
- KSP 2.3.5 (must be 2.3.5+, not 2.2.10-x — see IMPLEMENTATION_LOG.md)

### What Works
- App installs and launches on device
- Full UI: note input screen, topic bar, submit button, collapsible history, snackbar feedback
- Room database persists submission history
- Navigation: Auth → Note Input → Settings (with back)
- Settings screen: sign out, read-only repo display, digital assistant role detection
- Lock screen launch: VoiceInteractionService registered, NoteCaptureActivity with showWhenLocked
- PAT-based auth: paste token + repo, validates via GitHub API

### What Needs Testing on Device
- End-to-end note submission with a real PAT
- Topic fetch with a real PAT
- Lock screen launch on device
- Settings: sign out → verify returns to setup screen
- Airplane mode → submit → verify error shown, text preserved

---

## How to Build and Deploy

```bash
cd /Users/cdavis/github/note-taker/note-taker
./gradlew assembleDebug        # Build only
./gradlew installDebug         # Build + install on connected device
```

ADB path: `~/Library/Android/sdk/platform-tools/adb`
Device: `R5CX12TCQ1N` (SM-S928U1, Android 16)

---

## Key Files Quick Reference

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/.../ui/viewmodels/AuthViewModel.kt` | PAT validation + setup |
| `app/src/main/kotlin/.../data/api/GitHubApi.kt` | GitHub API endpoints (user, contents) |
| `app/src/main/kotlin/.../data/auth/AuthManager.kt` | Token + repo storage (DataStore) |
| `app/src/main/kotlin/.../data/repository/NoteRepository.kt` | Submit notes, fetch topic |
| `app/src/main/kotlin/.../ui/screens/AuthScreen.kt` | PAT setup screen |
| `app/src/main/kotlin/.../ui/navigation/NavGraph.kt` | Screen routing |
| `app/src/main/kotlin/.../di/AppModule.kt` | Hilt dependency injection |
| `docs/adr/001-pat-over-oauth.md` | ADR: why PAT over OAuth |
| `docs/PAT-SETUP.md` | User guide for creating a PAT |
| `gradle/libs.versions.toml` | All dependency versions |
