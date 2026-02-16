# Note Taker App — Handoff Document

## What Exists

A fully compiling Android app at `/Users/cdavis/github/note-taker/note-taker/` with all features implemented through M35. `./gradlew assembleDebug` and `./gradlew bundleRelease` both build cleanly. The APK installs and launches on the target device (Samsung Galaxy S24 Ultra, Android 16).

See `INDEX.md` for the complete file listing and `IMPLEMENTATION_LOG.md` for build details per milestone.

### Tech Stack
- AGP 9.0.0, Kotlin 2.2.10 (built-in), Gradle wrapper 9.1.0
- Jetpack Compose (BOM 2026.01.01), Material 3, dark theme only
- Hilt 2.59.1, Room 2.8.4, Retrofit 3.0.0, OkHttp 5.3.0, Navigation 2.9.7
- KSP 2.3.5 (must be 2.3.5+, not 2.2.10-x — see IMPLEMENTATION_LOG.md)
- EncryptedSharedPreferences for token storage (Android Keystore-backed)

### What Works
- **Auth:** GitHub App OAuth (primary) and PAT (fallback). Two-card setup screen with help dialogs.
- **Note input:** Voice-first with continuous listening, keyboard fallback, growing text field
- **Submission:** Queue-first via Room → immediate push → WorkManager retry on failure
- **Browse:** Read-only repo browser with markdown rendering via Markwon
- **Lock screen:** VoiceInteractionService + side button launch, capture over keyguard
- **Settings:** Sign out (with OAuth token revocation), digital assistant two-step setup, delete all data
- **Theme:** Teal/blue/green accents on purple-tinted dark surfaces matching app icon
- **CI/CD:** Three-branch pipeline (develop → staging → master) with automated Google Play uploads
- **Security:** R8 minification, no HTTP body logging in release, ADB backup disabled, encrypted token storage

### Branch Strategy
- `develop` — daily driver, no deploys
- `staging` → auto-deploys to Google Play internal testing track
- `master` → auto-deploys to Google Play production track
- See `docs/DEPLOYMENT.md` for full details

---

## How to Build and Deploy

```bash
cd /Users/cdavis/github/note-taker/note-taker
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Build + install on connected device
./gradlew bundleRelease        # Build signed release AAB (needs signing config)
```

ADB path: `~/Library/Android/sdk/platform-tools/adb`
Device: `R5CX12TCQ1N` (SM-S928U1, Android 16)

---

## Key Files Quick Reference

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Build config, signing, versionCode/versionName |
| `app/src/main/.../ui/screens/AuthScreen.kt` | Two-card auth setup (OAuth + PAT) |
| `app/src/main/.../ui/viewmodels/AuthViewModel.kt` | OAuth flow + PAT validation |
| `app/src/main/.../data/auth/AuthManager.kt` | Token storage (EncryptedSharedPreferences + DataStore) |
| `app/src/main/.../data/auth/OAuthConfig.kt` | OAuth constants and PKCE helpers |
| `app/src/main/.../data/api/GitHubApi.kt` | GitHub API endpoints |
| `app/src/main/.../data/repository/NoteRepository.kt` | Submit notes, fetch topic, browse |
| `app/src/main/.../speech/SpeechRecognizerManager.kt` | Continuous speech recognition |
| `.github/workflows/deploy.yml` | CI/CD: build + deploy to Google Play |
| `docs/DEPLOYMENT.md` | Deployment guide (branching, versioning, release process) |
| `docs/playstore/` | All Play Store materials (listing, screenshots, privacy policy) |
| `gradle/libs.versions.toml` | All dependency versions |

---

## Next Steps

1. **Google Play bootstrap** — First manual AAB upload, service account setup, GitHub secrets (see `docs/DEPLOYMENT.md`)
2. **Create `staging` and `develop` branches** — `git checkout -b staging && git push -u origin staging && git checkout -b develop && git push -u origin develop`
3. **Set GitHub default branch to `develop`** via GitHub UI
