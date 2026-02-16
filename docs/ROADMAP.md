# Note Taker — Roadmap

## Completed (V2)

### Offline Note Queuing ✅
Notes are queued locally in Room and retried via WorkManager when network is available. UI shows "Queued" animation and pending count badge.

### Browse Notes ✅
Read-only repo browser with directory listing, file viewer, and Markwon markdown rendering. Accessible from the top bar and from lock screen (with keyguard dismiss).

### GitHub App OAuth ✅
GitHub App OAuth as primary auth (M31). One-tap "Sign in with GitHub" installs the GitJot GitHub App on a user-chosen repo. PKCE-protected flow, EncryptedSharedPreferences token storage, PAT as fallback. Token revocation on sign-out (M34).

## V3 Features

### Donate / Tip Button
In-app option for users to support development. Could be a simple link to GitHub Sponsors, Buy Me a Coffee, or similar. No in-app purchases — just an external link.

### Smarter Topic Refresh
Currently the topic refreshes on app launch and after each note submission. This won't catch topic changes that happen between submissions (e.g., the LLM agent processes a "new topic" note while the app is sitting open). Need a better mechanism:

- Periodic polling (e.g., every 60s while the app is in the foreground)
- GitHub webhook via push notification (requires server infrastructure)
- ETag/If-None-Match on the Contents API to make polling cheap
