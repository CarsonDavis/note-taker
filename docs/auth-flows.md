# Auth Flows

Reference for every auth-related thing a user might want to do in GitJot, what actually happens, and where the gaps are. Organized by user intent, not code structure.

**Related docs:**
- [github-app-oauth-implementation.md](github-app-oauth-implementation.md) — OAuth implementation plan
- [REQUIREMENTS.md](REQUIREMENTS.md) — FR5 (authentication)
- [playstore/privacy-policy.md](playstore/privacy-policy.md) — Privacy policy
- [playstore/delete-your-data.md](playstore/delete-your-data.md) — User-facing data deletion instructions

---

## Auth Methods at a Glance

| | OAuth (GitHub App) | PAT (Personal Access Token) |
|---|---|---|
| **How token is obtained** | GitHub OAuth flow with PKCE | User generates on GitHub and pastes in |
| **How repo is determined** | Auto-discovered from GitHub App installation | User enters manually |
| **Token prefix** | `ghu_` | `ghp_` |
| **Expiration** | Controlled by GitHub (typically 8 hours, auto-refreshed by GitHub) | User-configured (or no expiration) |
| **Scope** | Single repo selected during installation | Repos selected during token creation |
| **Revoked on disconnect** | Yes (best-effort, 5s timeout via GitHub API) | No |
| **Settings label** | "Connected via GitHub" | "Connected via Personal Access Token" |

---

## Getting Started

### First-time OAuth setup

**What the user wants:** Connect GitJot to a GitHub repo for the first time using OAuth.

**How to do it:**
1. Fork the [gitjot-notes](https://github.com/CarsonDavis/gitjot-notes/fork) template repo
2. Tap "Sign in with GitHub" on the auth screen
3. GitHub opens in browser — install the GitJot app and select the forked repo
4. Authorize GitJot — browser redirects back to the app
5. App discovers the installation and repo automatically, setup is complete

**What happens under the hood:**
- First tap sends user to the install URL (`github.com/apps/gitjot-oauth/installations/select_target`) since there's no `installation_id` yet
- GitHub chains installation into authorization, returning an auth code to the callback
- App exchanges code for token (PKCE-protected), fetches username, discovers installation and repo via `/user/installations` API
- Saves token to EncryptedSharedPreferences, username/repo/auth_type/installation_id to DataStore

**Gaps:**
- If the user already installed the GitHub App from another device, the install URL doesn't redirect (app is already installed). User must tap a second time — see [Re-auth same account (no installation_id)](#re-auth-same-account-no-installation_id--transitionreinstall-case). A hint ("Already installed GitJot on GitHub? Tap again to continue.") now appears after the first tap (M38).

**Fixed (M38/M40):**
- ~~No guidance when user skips forking~~ → Error now says "Go back to Step 1 and fork the template repo..." (M38)
- ~~Multiple repos silently uses first~~ → Repo selection dialog shown when multiple repos available (M40)

### First-time PAT setup

**What the user wants:** Connect GitJot using a Personal Access Token instead of OAuth.

**How to do it:**
1. Fork the template repo (same as OAuth)
2. Tap "Or connect with a Personal Access Token" on the auth screen
3. Enter repo as `owner/repo` or full GitHub URL
4. Generate a fine-grained PAT on GitHub with Contents read/write on the target repo
5. Paste the token
6. Tap "Continue" — app validates the token (401 check) and repo (404 check)

**Gaps:**
- No validation that the token has write permissions — a read-only token passes validation but fails silently on note upload
- No guidance on token expiration — if the user sets a short expiry, the token silently stops working

---

## Disconnecting

### Disconnect (OAuth)

**What the user wants:** Remove GitJot credentials from this device.

**How to do it:** Settings > Disconnect > confirm in dialog > "Disconnect"

**What happens:**
1. Confirmation dialog appears with:
   - Pending notes warning in red (if any unsent notes)
   - "This removes your GitHub credentials from this device."
   - "GitJot will remain installed on your GitHub account."
   - Red "Disconnect" button + "Cancel"
2. On confirm: spinner shows, `revokeOAuthTokenIfNeeded()` sends DELETE to GitHub's application token endpoint (5s timeout, best-effort)
3. `authManager.signOut()` clears EncryptedSharedPreferences and DataStore, **but preserves `installation_id`** so the next sign-in uses the authorize URL instead of the install URL
4. Room DB (pending notes, submission history) and WorkManager jobs are untouched
5. App navigates to auth screen

To manage or uninstall GitJot from GitHub, use the "Manage on GitHub" button in the Repository card.

**Source:** `SettingsViewModel.kt:93-100`, `AuthManager.kt:124-131`

### Disconnect (PAT)

**What the user wants:** Remove GitJot credentials from this device.

**How to do it:** Settings > Disconnect

**What happens:**
- **No pending notes:** Immediate disconnect — no dialog, no token revocation (PATs can't be revoked via API)
- **With pending notes:** Confirmation dialog shows pending count warning + "Disconnect" / "Cancel"
- Same storage clearing as OAuth disconnect (preserves `installation_id` if one exists)
- Room DB and WorkManager untouched
- App navigates to auth screen

**Source:** `SettingsScreen.kt:148-154` (dialog shown when `pendingCount > 0`)

### Delete All Data

**What the user wants:** Factory reset — remove all traces of GitJot from the device.

**How to do it:** Settings > Delete All Data > confirm in dialog > "Delete"

**What happens:**
1. Confirmation dialog warns about permanent deletion
2. On confirm: revokes OAuth token if applicable (5s timeout)
3. Cancels all WorkManager jobs (`workManager.cancelAllWork()`)
4. Deletes all pending notes from Room DB (`pendingNoteDao.deleteAll()`)
5. Deletes all submission history from Room DB (`submissionDao.deleteAll()`)
6. `authManager.clearAllData()` clears EncryptedSharedPreferences and DataStore **including `installation_id`** — full wipe
7. App navigates to auth screen

**Source:** `SettingsViewModel.kt:90-101`, `AuthManager.kt:137-140`

### Data survival matrix

What survives each type of disconnection:

| Data | Disconnect | Delete All Data | Uninstall App | Uninstall GitHub App (from GitHub) |
|---|---|---|---|---|
| Token (local) | Cleared | Cleared | Cleared (Android deletes app data) | Unchanged |
| Token (GitHub-side) | Revoked (OAuth) / unchanged (PAT) | Revoked (OAuth) / unchanged (PAT) | **Still valid** (not revoked) | Revoked by GitHub |
| `installation_id` | **Preserved** | Cleared | Cleared | Stale (points to uninstalled app) |
| Username | Cleared | Cleared | Cleared | Unchanged |
| Repo (owner/name) | Cleared | Cleared | Cleared | Unchanged |
| Pending notes (Room) | **Preserved** | Cleared | Cleared | Unchanged |
| Submission history (Room) | **Preserved** | Cleared | Cleared | Unchanged |
| WorkManager jobs | **Still scheduled** | Cancelled | Cleared | Still scheduled (will fail) |
| GitHub App installed | Yes | Yes | Yes | **Uninstalled** |
| Notes on GitHub | Unchanged | Unchanged | Unchanged | Unchanged |

---

## Signing Back In

### Re-auth same account (installation_id preserved)

**What the user wants:** Sign back in after disconnecting.

**How to do it:** Tap "Sign in with GitHub" on the auth screen (single tap).

**What happens:**
- Since `installation_id` was preserved during disconnect, `startOAuthFlow()` returns the **authorize URL** (not the install URL)
- User sees the GitHub authorization page, approves, and is redirected back
- App exchanges code for token, discovers installation and repo automatically
- Single tap — no re-installation needed

**Source:** `AuthViewModel.kt:86` — `cachedInstallationId != null` branch

### Re-auth same account (no installation_id — transition/reinstall case)

**What the user wants:** Sign back in after "Delete All Data", app reinstall, or migrating from an older version that didn't save `installation_id`.

**How to do it:** Tap "Sign in with GitHub" **twice**.

**What happens:**
1. **First tap:** No `installation_id` cached, so `startOAuthFlow()` returns the **install URL** (`github.com/apps/gitjot-oauth/installations/select_target`). The GitHub App is already installed, so GitHub shows the app's configuration page but **does not redirect back** to the app. User presses back. `triedInstallUrl` is now `true`.
2. **Second tap:** `triedInstallUrl` is true, so `startOAuthFlow()` returns the **authorize URL**. User authorizes, gets redirected back. App completes the flow normally.

**The `triedInstallUrl` mechanism:** This flag lives in `AuthViewModel` (not persisted). When the install URL is tried but no callback comes back, the flag ensures the next tap uses the authorize URL as a fallback. The flag resets if the ViewModel is recreated.

**Source:** `AuthViewModel.kt:50-52`, `AuthViewModel.kt:83-97`

### Re-auth PAT

**What the user wants:** Sign back in with a PAT.

**How to do it:** Same as first-time PAT setup — enter repo and token again. There's no saved state to recover from.

---

## Changing Your Setup

### Change repo (OAuth)

**What the user wants:** Point GitJot at a different repository.

**How to do it:**
1. In Settings, tap "Manage on GitHub" (M39/M41) — opens GitHub's installation settings
2. Change the repository access to include (or switch to) the new repo
3. Disconnect from GitJot
4. Sign back in — the app will auto-discover the new repo (or show a selection dialog if multiple repos, M40)

**Gaps:**
- If the user has pending notes when they change repos, those notes will upload to whatever repo is configured after re-auth

### Change repo (PAT)

**What the user wants:** Point GitJot at a different repository.

**How to do it:**
1. Disconnect
2. Sign back in with PAT, entering the new repo (and a new or existing token that has access to it)

### Switch GitHub accounts

**What the user wants:** Use GitJot with a different GitHub account.

**How to do it:**
1. Disconnect
2. Sign in as the different account (GitHub's browser session determines which account is used)

**Gap:** If the user disconnected (not "Delete All Data"), the `installation_id` from the old account is preserved. On first sign-in attempt:
- The authorize URL is used (because `installation_id` exists)
- Token exchange succeeds, but `/user/installations` returns no installations (the new account doesn't have the old account's installation)
- Stale installation recovery triggers: `installation_id` is cleared, `triedInstallUrl` is reset, and an error is shown: "GitJot isn't connected to this account. Tap 'Sign in with GitHub' to set it up."
- Second tap works normally (install URL for the new account)

**Source:** `AuthViewModel.kt:131-148`

### Switch auth methods (PAT to OAuth, or OAuth to PAT)

**What the user wants:** Change from PAT to OAuth or vice versa.

**How to do it:**
1. Disconnect
2. Choose the other auth method on the auth screen

**Gaps:**
- Old PAT is not revoked when switching to OAuth — it remains valid on GitHub until the user manually deletes it or it expires
- GitHub App stays installed when switching to PAT — the user has both an active GitHub App installation and a PAT, but only the PAT is used. Use "Manage on GitHub" in Settings to uninstall if desired

---

## Removing Access

### Disconnect only

**What the user wants:** Remove credentials from the device but keep local history.

**What happens:** Token revoked (OAuth only), local credentials cleared, pending notes and submission history preserved. GitHub App remains installed on GitHub.

### Uninstall phone app

**What the user wants:** Remove GitJot from their phone.

**What happens:**
- Android deletes all local data (EncryptedSharedPreferences, DataStore, Room DB)
- `allowBackup=false` in the manifest — no data in cloud backups
- **Token is NOT revoked on GitHub** — the OAuth token (or PAT) remains valid until it expires or is manually revoked
- **GitHub App stays installed** on the user's GitHub account

### Uninstall GitHub App from GitHub

**What the user wants:** Remove GitJot's access from their GitHub account.

**How to do it:** GitHub Settings > Applications > Installed GitHub Apps > GitJot > Uninstall

**What happens:**
- GitHub revokes all tokens associated with the app on their side
- The phone app still has the (now-invalid) token stored locally
- Next API call fails with an error, but there's **no proactive detection** — the user sees generic errors
- WorkManager continues retrying uploads with the dead token

### Full removal

**What the user wants:** Remove all traces of GitJot everywhere.

**How to do it:**
1. In the app: Settings > Delete All Data (revokes OAuth token, wipes local data)
2. On GitHub: use "Manage on GitHub" in Settings to uninstall the GitHub App, or go to GitHub Settings > Applications > Installed GitHub Apps > GitJot > Uninstall
3. If PAT was used: GitHub Settings > Developer settings > Personal access tokens > delete the token
4. Optionally: delete the forked `gitjot-notes` repo from GitHub

---

## Recovery

### Token revoked externally

**Scenario:** User (or an org admin) revokes the token from GitHub Settings, or the token expires.

**What happens:**
- API calls fail with 401 or 403
- **Reactive detection (M37):** Note submission shows "Session expired. Please disconnect and sign back in from Settings." and preserves the user's note text. Browse screen shows the same error.
- `NoteUploadWorker` detects 401/403, marks notes as `auth_failed`, and returns `Result.failure()` (stops retrying). Other errors still retry.
- No proactive detection — the app doesn't check token validity on launch or on a schedule

**Recovery:** Disconnect and sign back in to get a fresh token.

**Source:** `NoteUploadWorker.kt` (401/403 → auth_failed), `NoteRepository.kt` (AUTH_FAILED result), `NoteViewModel.kt` (preserves note text), `BrowseViewModel.kt` (session expired error)

### GitHub App uninstalled from GitHub

**Scenario:** User uninstalls the GitJot GitHub App from GitHub Settings > Applications.

**What happens:** Same as token revoked externally (API calls fail with "Session expired" error, worker stops retrying on 401/403). Additionally, on re-auth:
- The preserved `installation_id` is stale — points to a now-uninstalled app
- The authorize URL is used (because `installation_id` exists), token exchange succeeds, but `/user/installations` returns empty
- Stale installation recovery fires: clears `installation_id`, resets `triedInstallUrl`, shows error
- User taps "Sign in with GitHub" again — install URL is used, GitHub App is reinstalled

**Recovery:** Disconnect > Sign in > tap twice (first tap triggers recovery, second tap completes).

### OAuth flow interrupted (back press)

**Scenario:** User taps "Sign in with GitHub", browser opens, but user presses back without completing the flow.

**What happens:**
- `LifecycleEventEffect(ON_RESUME)` fires when the app resumes
- Calls `cancelOAuthFlow()` which resets `isOAuthInProgress` to false
- Spinner stops, button becomes tappable again
- User can retry normally
- **Guard (M41):** `cancelOAuthFlow()` has an `isValidating` check — if the OAuth callback is already being processed (token exchange in progress), the cancel is a no-op. This prevents the ON_RESUME lifecycle event from interfering with a successful callback flow.

**Source:** `AuthScreen.kt:75-77`, `AuthViewModel.kt:222-230`

### OAuth flow interrupted (app killed)

**Scenario:** User starts OAuth flow, switches to browser, but Android kills the app process before the callback arrives.

**What happens:**
- `SavedStateHandle` preserves the PKCE `oauth_verifier` and `oauth_state` across process death
- However, the browser OAuth flow is a one-time redirect — if the app was killed before receiving the callback, the browser flow is lost
- When the app restarts, `SavedStateHandle` has the PKCE state but there's no pending callback to consume it
- User must start a fresh OAuth flow

**Source:** `AuthViewModel.kt:77-79` (SavedStateHandle saves), `AuthViewModel.kt:101-109` (reads on callback)

### Reinstall app / new phone

**Scenario:** User reinstalls GitJot or sets up a new phone.

**What happens:**
- Fresh install — no local data carries over (`allowBackup=false`)
- No `installation_id` saved, so the app behaves like a first-time install
- If the GitHub App is already installed from the previous device, the user hits the two-tap path described in [Re-auth same account (no installation_id)](#re-auth-same-account-no-installation_id--transitionreinstall-case)
- Old tokens from the previous device remain valid on GitHub until expiration — multiple valid tokens can coexist across devices

---

## Known Gaps

Consolidated from all sections above.

### Fixed in M37-M41

- ~~**No proactive 401 detection.**~~ Reactive detection now shows "Session expired" errors in note submit and browse, worker stops retrying on 401/403 (M37).
- ~~**`NoteUploadWorker` retries infinitely with invalid token.**~~ Worker now returns `Result.failure()` on 401/403 and marks notes as `auth_failed` (M37).
- ~~**No warning about pending notes during disconnect.**~~ Disconnect dialog now shows pending count warning in red (M37, dialog simplified in M41).
- ~~**No in-app way to change repo for OAuth users.**~~ "Manage on GitHub" button in Settings opens GitHub's installation settings (M39, renamed M41).
- ~~**Multiple repos silently uses first.**~~ Repo selection dialog shown when multiple repos available (M40).
- ~~**No guidance when user skips forking.**~~ Error now says "Go back to Step 1 and fork the template repo..." (M38).
- ~~**Stale installation_id error message confusing.**~~ Improved to "GitJot isn't connected to this account" (M38).
- ~~**Two-tap OAuth has no hint.**~~ Hint text now appears after first tap: "Already installed GitJot on GitHub? Tap again to continue." (M38).

### Medium — confusing UX, workaround exists

- **Two-tap OAuth for returning users without `installation_id`.** After "Delete All Data", reinstall, or migration, the first tap opens the install URL which doesn't redirect (app already installed). User must tap again. Mitigated with a hint message (M38).
- **Stale `installation_id` when switching accounts.** Preserved `installation_id` from old account causes error on first attempt with new account. Recovers on second tap. Error message improved (M38).
- **Silent token revocation failure on disconnect.** Revocation is best-effort with a 5s timeout. If it fails (network error, timeout), the token remains valid on GitHub. No error shown to the user.
- **Pending notes survive disconnect and upload to whatever repo is configured next.** Room DB is not cleared on disconnect. If the user signs into a different repo, kept notes will upload there. Users who want to clear pending notes before disconnect can use "Delete All Data" instead.

### Low — edge cases, accepted

- **PAT flow doesn't validate write permissions.** A read-only PAT passes validation but note uploads fail.
- **No PAT expiration guidance.** If the user sets a short expiry, the token silently stops working.
- **Old PAT not revoked when switching to OAuth.** The PAT remains valid on GitHub.
- **GitHub App stays installed when switching to PAT.** The user has both an active GitHub App installation and a PAT, but only the PAT is used.
- **Uninstalling phone app doesn't revoke tokens.** OAuth token and PAT remain valid on GitHub until expiration or manual revocation.
- **Multiple valid tokens possible across devices.** Each device gets its own OAuth token. No mechanism to list or manage active sessions.
- **No proactive token check on app launch.** Reactive detection covers most cases (M37), but the app still doesn't check on launch or periodically.
