# Note Taker — Roadmap

## Completed (V2)

### Offline Note Queuing ✅
Notes are queued locally in Room and retried via WorkManager when network is available. UI shows "Queued" animation and pending count badge.

### Browse Notes ✅
Read-only repo browser with directory listing, file viewer, and Markwon markdown rendering. Accessible from the top bar and from lock screen (with keyguard dismiss).

### GitHub App OAuth ✅
GitHub App OAuth as primary auth (M31). One-tap "Sign in with GitHub" installs the GitJot GitHub App on a user-chosen repo. PKCE-protected flow, EncryptedSharedPreferences token storage, PAT as fallback. Token revocation on disconnect (M34).

## V3 Features

### Multi-Repo Support
Ability to connect more than one GitHub repository and switch between them in-app. Currently requires disconnect/reconnect to change repos. Would need a repo switcher UI in Settings and changes to how AuthManager stores repo configuration.

### Donate / Tip Button
In-app option for users to support development. Could be a simple link to GitHub Sponsors, Buy Me a Coffee, or similar. No in-app purchases — just an external link.

## Removed

### Sticky Topic Display (removed M44)
The app used to fetch `.current_topic` from the repo and show it at the top of the note screen, with a planned "smarter topic refresh" follow-up. Removed in M44: notes now cover everything in the user's life and carry their own inline context for the processing agent, so a single repo-wide topic is no longer meaningful. The "smarter topic refresh" idea is dropped along with it.
