# Note Taker — Roadmap

## Completed (V2)

### Offline Note Queuing ✅
Notes are queued locally in Room and retried via WorkManager when network is available. UI shows "Queued" animation and pending count badge.

### Browse Notes ✅
Read-only repo browser with directory listing, file viewer, and Markwon markdown rendering. Accessible from the top bar and from lock screen (with keyguard dismiss).

## V3 Features

### Smarter Topic Refresh
Currently the topic refreshes on app launch and after each note submission. This won't catch topic changes that happen between submissions (e.g., the LLM agent processes a "new topic" note while the app is sitting open). Need a better mechanism:

- Periodic polling (e.g., every 60s while the app is in the foreground)
- GitHub webhook via push notification (requires server infrastructure)
- ETag/If-None-Match on the Contents API to make polling cheap
