# Note Taker App — Handoff Document

## What Exists

A fully compiling Android app at `/Users/cdavis/github/note-taker/note-taker/` with all features implemented through M8. `./gradlew assembleDebug` builds cleanly with 0 warnings. The APK installs and launches on the target device (Samsung Galaxy S24 Ultra, Android 16).

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
- Settings screen: sign out, repo picker, digital assistant role detection
- Lock screen launch: VoiceInteractionService registered, NoteCaptureActivity with showWhenLocked
- Device flow auth endpoints hit GitHub correctly (form-encoded, not JSON)

### What Doesn't Work Yet
- **Auth is mid-migration from OAuth App to GitHub App** (see below)
- End-to-end note submission (blocked on auth)
- Topic fetch (blocked on auth)
- Lock screen launch not yet tested on device

---

## Current Blocker: Auth Migration

### The Problem

The app was initially built with a **GitHub OAuth App** using the device flow. This works, but the OAuth App's `repo` scope grants access to ALL user repositories — not just the one selected in the app. The user (correctly) rejected this.

### The Fix: Switch to a GitHub App

GitHub Apps allow fine-grained permissions. When a user installs a GitHub App, they choose exactly which repositories to grant access to. The token can only access those repos.

### Current State

The user has started creating a GitHub App at `github.com/settings/apps/new` with:
- "Request user authorization (OAuth) during installation": checked
- "Enable Device Flow": checked
- "Expire user authorization tokens": unchecked
- Webhook: should be unchecked/inactive
- Repository permissions → Contents: Read and write
- Where can this app be installed: "Only on this account"

**The Callback URL field is required by GitHub even for device flow.** Put `https://localhost` — it won't be used.

Once the GitHub App is created, the Client ID (starts with `Iv`) goes into `AuthViewModel.kt:21`.

### Code Changes Needed

The current code at `AuthViewModel.kt:21`:
```kotlin
const val GITHUB_CLIENT_ID = "Ov23liLi2uorJRemy1Zb"  // OLD OAuth App — delete this app on GitHub
```

Replace with the new GitHub App's client ID.

The device flow endpoints (`/login/device/code` and `/login/oauth/access_token`) are the same for both OAuth Apps and GitHub Apps — no URL changes needed.

**However, the repo listing needs to change.** With a GitHub App, the user selects repos during installation. The app should list only repos the GitHub App is installed on, not all user repos.

Current code in `GitHubApi.kt` uses:
```kotlin
@GET("user/repos")  // Returns ALL user repos
```

Change to:
```kotlin
@GET("user/installations/{installation_id}/repositories")  // Only repos with app installed
```

Or simpler: after auth, call `GET /installation/repositories` to get only accessible repos. This requires:

1. After getting the access token, call `GET /user/installations` to find the installation ID
2. Then `GET /user/installations/{id}/repositories` to list only the repos the user granted

Alternatively, **skip the repo picker entirely** — if the GitHub App is installed on only one repo, just use that one automatically. The user controls access via GitHub's App installation settings, not in-app.

### Files to Modify

1. **`AuthViewModel.kt`** — Update `GITHUB_CLIENT_ID`, possibly simplify repo selection
2. **`GitHubApi.kt`** — Add installation/repositories endpoint, possibly remove `user/repos`
3. **`AuthScreen.kt`** — If skipping repo picker, simplify the auth flow (welcome → device code → done)
4. **`NoteRepository.kt`** — May need to discover the repo from the installation rather than from saved preferences
5. **`SettingsScreen.kt`** — Repo "change" button would link to GitHub App installation settings instead of an in-app picker

### GitHub API Reference

```
# List installations for the authenticated user
GET /user/installations
Authorization: Bearer <user-access-token>

# List repos accessible to a specific installation
GET /user/installations/{installation_id}/repositories
Authorization: Bearer <user-access-token>
```

Response for `/user/installations` includes:
```json
{
  "installations": [
    {
      "id": 12345,
      "app_id": 67890,
      "target_type": "User",
      "repository_selection": "selected",
      ...
    }
  ]
}
```

Response for `/user/installations/{id}/repositories` includes:
```json
{
  "repositories": [
    {
      "full_name": "CarsonDavis/notes",
      ...
    }
  ]
}
```

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

## After Auth Works: Remaining Testing

1. Submit a note → verify file appears in `inbox/` of the selected repo
2. Create `.current_topic` file in repo → verify topic shows in app
3. Set app as default digital assistant → long-press side key from lock screen → verify app launches
4. Settings: sign out → verify returns to auth screen
5. Airplane mode → submit → verify error shown, text preserved

---

## Key Files Quick Reference

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/.../ui/viewmodels/AuthViewModel.kt` | Device flow auth + CLIENT_ID |
| `app/src/main/kotlin/.../data/api/GitHubApi.kt` | All GitHub API endpoints |
| `app/src/main/kotlin/.../data/auth/AuthManager.kt` | Token + repo storage (DataStore) |
| `app/src/main/kotlin/.../data/repository/NoteRepository.kt` | Submit notes, fetch topic |
| `app/src/main/kotlin/.../ui/screens/AuthScreen.kt` | Auth flow UI (3 steps) |
| `app/src/main/kotlin/.../ui/navigation/NavGraph.kt` | Screen routing |
| `app/src/main/kotlin/.../di/AppModule.kt` | Hilt dependency injection |
| `gradle/libs.versions.toml` | All dependency versions |
