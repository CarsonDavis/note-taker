# GitHub App OAuth — Implementation Plan

Replace the current PAT-based auth with a GitHub App OAuth flow that lets users
tap a button, pick a repo on GitHub, and get scoped access automatically.

**Research:** [background](research/github-app-oauth-option-b/background.md) |
[report](research/github-app-oauth-option-b/report.md) |
[ADR 001 (current PAT decision)](adr/001-pat-over-oauth.md)

---

## Why

The current PAT flow works but requires users to:
1. Leave the app and navigate to GitHub's token settings
2. Configure permissions and repo scope manually
3. Copy the token and paste it back into the app
4. Manually type their `owner/repo`

The OAuth flow replaces all of that with: tap "Connect to GitHub" → pick a repo
→ done.

---

## How It Works

### The User Experience

1. User taps **"Connect to GitHub"** on the auth screen
2. Chrome Custom Tab opens to GitHub's App installation page
3. GitHub shows the user's repos — user selects **one**
4. GitHub asks the user to authorize the app (same screen, combined flow)
5. Browser redirects through a GitHub Pages bounce page back into the app
6. App exchanges the authorization code for a token (on-device, no server)
7. User lands on the note input screen, ready to go

### The Token

- **Scoped to one repo** — the intersection of: app permissions (Contents
  read/write only) + the repo the user selected + the user's own access
- **Non-expiring** — token expiration is disabled on the GitHub App. No
  refresh logic needed.
- **Stored in EncryptedSharedPreferences** backed by Android Keystore

---

## Prerequisites

### 1. Register a GitHub App

Go to https://github.com/settings/apps/new and configure:

| Setting | Value |
|---------|-------|
| App name | `GitJot-OAuth` (globally unique on GitHub) |
| Homepage URL | App repo or landing page |
| Callback URL | `https://madebycarson.com/gitjot-oauth/callback` |
| Request user authorization during installation | **Enabled** |
| Expire user authorization tokens | **Disabled** (non-expiring tokens, simpler — no refresh logic needed) |
| Enable Device Flow | **Enabled** (fallback) |
| Webhooks | **Disabled** |
| Repository permissions → Contents | **Read & Write** |
| All other permissions | No access |

This produces a **Client ID** (public) and **Client Secret** (ships in APK,
protected by PKCE).

### 2. Set Up GitHub Pages Redirect ✅

Created [`CarsonDavis/gitjot-oauth`](https://github.com/CarsonDavis/gitjot-oauth)
with GitHub Pages enabled on the `master` branch. Live at
`https://madebycarson.com/gitjot-oauth/` (custom domain).

**`callback/index.html`**
```html
<!DOCTYPE html>
<html>
<head><title>Redirecting...</title></head>
<body>
  <p>Redirecting to GitJot...</p>
  <script>
    window.location.href = "notetaker://callback" + window.location.search;
  </script>
  <noscript><p>JavaScript is required.</p></noscript>
</body>
</html>
```

GitHub redirects to this HTTPS page after authorization. The page bounces the
`code` and `state` params to the app via the `notetaker://` custom scheme.

**`_config.yml`** — `include: [".well-known"]` so GitHub Pages serves dotfiles
(for future Android App Links).

**Optional enhancement — Android App Links:**

Add `/.well-known/assetlinks.json` to skip the bounce page entirely:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.carsondavis.notetaker",
    "sha256_cert_fingerprints": ["<signing-key-fingerprint>"]
  }
}]
```

---

## Implementation Steps

### Phase 1: OAuth Infrastructure

#### 1.1 Add OAuth constants

Add to the app (e.g., `BuildConfig` fields or a constants object):
- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`
- `GITHUB_CALLBACK_URL` (`https://madebycarson.com/gitjot-oauth/callback`)
- `GITHUB_APP_INSTALL_URL` (`https://github.com/apps/gitjot-oauth/installations/select_target`)

#### 1.2 PKCE utility

```kotlin
// Generate code_verifier (43-128 char random Base64URL string)
// Generate code_challenge = Base64URL(SHA256(code_verifier))
```

Only S256 is supported. Store `code_verifier` in memory for the duration of
the auth flow.

#### 1.3 State parameter

Generate a random `state` string, store it, and verify it when the callback
arrives to prevent CSRF.

### Phase 2: Auth Flow

#### 2.1 Launch authorization

Open a Custom Tab to the GitHub App installation URL. For the combined
install+authorize flow, the URL is:

```
https://github.com/apps/gitjot-oauth/installations/select_target
```

GitHub handles chaining from installation → OAuth authorization automatically
when "Request user authorization during installation" is enabled.

For returning users who already installed the app (detected by having a saved
`installation_id` in DataStore — preserved across sign-out), open the standard
OAuth authorize URL:

```
https://github.com/login/oauth/authorize
  ?client_id=CLIENT_ID
  &redirect_uri=CALLBACK_URL
  &state=STATE
  &code_challenge=CHALLENGE
  &code_challenge_method=S256
```

The app chooses which URL at runtime:
- **No `installation_id` saved** → install URL (first-time flow)
- **`installation_id` exists** → authorize URL (returning user, app already installed)

#### 2.2 Handle callback

Register an intent filter in `AndroidManifest.xml`:

```xml
<activity
    android:name=".ui.OAuthCallbackActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="notetaker" android:host="callback" />
    </intent-filter>
</activity>
```

The activity extracts `code` and `state` from the intent URI, verifies `state`,
and hands the `code` to the view model.

#### 2.3 Exchange code for token

POST from the device (via Retrofit/OkHttp):

```
POST https://github.com/login/oauth/access_token
Accept: application/json

client_id=CLIENT_ID
&client_secret=CLIENT_SECRET
&code=AUTH_CODE
&redirect_uri=CALLBACK_URL
&code_verifier=CODE_VERIFIER
```

Response contains `access_token` (and optionally `refresh_token`, `expires_in`
if token expiration is enabled — currently disabled).

### Phase 3: Token Storage & Refresh

#### 3.1 Store tokens

Replace the current `AuthManager` (Preferences DataStore with plain token +
repo string) with secure storage:

- `access_token` → EncryptedSharedPreferences
- `repo` → still needed; fetch from the GitHub API after auth
  (`GET /user/installations` → `GET /installation/repositories`)

Token expiration is disabled on the GitHub App, so no refresh token or expiry
tracking is needed.

#### 3.2 Repo discovery

After the token exchange, the app needs to know which repo the user selected.
Call:

```
GET https://api.github.com/user/installations
```

Then for the installation:

```
GET https://api.github.com/user/installations/{installation_id}/repositories
```

This returns the single repo the user selected. Store `owner/repo` in
preferences (replaces the manual text input).

### Phase 4: UI Changes

#### 4.1 Auth screen

Replace the current PAT input (token field + repo field + validate button) with:

- **"Connect to GitHub"** button → launches the OAuth flow
- Loading state while waiting for callback
- Error state if the flow fails or is cancelled
- Keep a small "Use Personal Access Token instead" link for power users or
  fallback

#### 4.2 Settings screen

- Show connected repo name (fetched via API, not manually entered)
- **"Disconnect"** button → clears tokens, optionally links to GitHub's
  app authorization settings so the user can revoke
- Show token status (connected, refreshing, expired — needs re-auth)

#### 4.3 Error handling

- Network errors during token exchange → retry or show error
- 401 during API calls → attempt refresh → if refresh fails, send to auth screen
- User cancelled the OAuth flow (came back without a code) → stay on auth screen

### Phase 5: Migration

#### 5.1 Existing users

Users with a saved PAT should continue working without disruption:

- On app launch, check if stored credential is a PAT (`ghp_` prefix) or an
  OAuth token (`ghu_` prefix)
- PATs continue to work with the existing API calls — no migration forced
- Show a non-blocking prompt: "Switch to GitHub sign-in for easier setup"
- Settings screen shows "Signed in with Personal Access Token" vs
  "Connected via GitHub"

#### 5.2 Keep PAT path working

The `GitHubApi` Retrofit interface doesn't change — both PATs and OAuth tokens
use the same `Authorization: Bearer <token>` header. The only difference is
token lifecycle management.

### Phase 6: Re-Authentication Flow

When a user signs out, the OAuth token is revoked and local storage is cleared
— but the GitHub App remains installed on the user's GitHub account. The app
must handle re-authentication without requiring the user to reinstall the app.

#### 6.1 Sign-out preserves installation_id

`AuthManager.signOut()` reads `installation_id` from DataStore before clearing,
then writes it back after the clear. This signals "returning OAuth user" on the
next sign-in attempt.

#### 6.2 Dual-path startOAuthFlow

`AuthViewModel.startOAuthFlow()` checks for a cached `installation_id` or a
`triedInstallUrl` fallback flag:

- **`installation_id` exists OR install URL already tried** → open the standard
  OAuth authorize URL (`/login/oauth/authorize`) with `client_id`,
  `redirect_uri`, `state`, `code_challenge`, `code_challenge_method=S256`.
  This re-authorizes without re-installing.
- **No `installation_id` AND first attempt** → open the install URL (first-time
  flow). Sets `triedInstallUrl = true` so the next attempt falls back to the
  authorize URL.

The `triedInstallUrl` flag handles the transition case: users who signed out
before this code change have no saved `installation_id`, but the GitHub App is
still installed. The install URL shows the app's settings page without
redirecting back. On the second tap, the authorize URL is used instead, which
does redirect back with a code.

The `installation_id` is cached at ViewModel init so `startOAuthFlow()` stays
non-suspend (no UI changes needed).

#### 6.3 State validation

The authorize flow returns `state` in the callback; the install flow omits it.
`handleOAuthCallback()` validates state when non-empty (authorize flow) and
skips validation when empty (install flow), maintaining backward compatibility.

#### 6.4 Stale installation_id recovery

If a user signs out and then uninstalls the GitHub App from GitHub Settings,
the saved `installation_id` becomes stale. When `getInstallations()` returns
empty after a successful token exchange:

1. Call `authManager.clearInstallationId()` to remove the stale ID
2. Null out the cached ID
3. Show error prompting the user to reinstall
4. Next tap uses the install URL (first-time flow)

#### 6.5 Delete All Data (factory reset)

`clearAllData()` in AuthManager wipes everything including `installation_id`.
This is a full factory reset — next sign-in will use the install flow regardless
of whether the GitHub App is still installed on the user's account.

---

## Dependencies

| Dependency | Purpose | Notes |
|-----------|---------|-------|
| `androidx.security:security-crypto` | EncryptedSharedPreferences | For secure token storage |
| `androidx.browser:browser` | Custom Tabs | Already in use if not, standard AndroidX |
| No new networking deps | Token exchange uses existing Retrofit/OkHttp | Just add a new endpoint |

---

## What Changes vs. Current Architecture

| Component | Current (PAT) | After (OAuth) |
|-----------|---------------|---------------|
| Auth screen | Token + repo text fields | "Connect to GitHub" button |
| Token source | User pastes from github.com | OAuth flow returns it |
| Repo source | User types `owner/repo` | Fetched from installation API |
| Token storage | Preferences DataStore (plain) | EncryptedSharedPreferences |
| Token expiry | Never (unless user sets it) | Non-expiring (expiration disabled on GitHub App) |
| Token refresh | Manual (sign out + re-setup) | Sign out and re-authorize (no refresh needed) |
| API calls | `Bearer ghp_...` | `Bearer ghu_...` (same header) |
| Permissions | User configures manually | App requests Contents R/W only |
| `GitHubApi.kt` | No change | No change |
| `NoteRepository.kt` | No change | No change |
| `AuthManager.kt` | Rewritten | Handles OAuth tokens (no refresh needed) |

---

## Security Summary

- **Client secret in APK**: Extractable, but useless without active user
  participation in the OAuth flow. PKCE binds the auth code to the originating
  app. GitHub explicitly endorses this for public clients.
- **PKCE (S256)**: Prevents authorization code interception even if another app
  registers the same custom URI scheme.
- **Non-expiring tokens**: Revoked on sign-out via GitHub API. If a token
  leaks, user can revoke from GitHub Settings or sign out from the app.
- **Minimal permissions**: Contents read/write only. No admin, no issues, no
  PRs.

See the [full security analysis](research/github-app-oauth-option-b/report.md#6-security-considerations)
in the research report.

---

## Open Questions

1. ~~**GitHub App name**~~: ✅ Registered as `GitJot-OAuth` (`gitjot` was taken). Slug: `gitjot-oauth`.
2. ~~**GitHub Pages repo**~~: ✅ Created `CarsonDavis/gitjot-oauth`, live at `https://madebycarson.com/gitjot-oauth/callback`.
3. **Device Flow fallback**: Worth implementing as a backup, or keep PAT as
   the only fallback?
4. ~~**Token expiration setting**~~: ✅ Resolved 2026-02-16. Confirmed `ghu_` tokens expire by default when the setting is enabled. Disabled token expiration on the GitHub App — non-expiring tokens keep implementation simple (no refresh logic). Can re-enable later if needed; existing tokens continue to work.
5. **Do we retire PAT support eventually?** Or keep both paths permanently?
