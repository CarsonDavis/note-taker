# GitHub App OAuth (Option B) for Android Mobile App

**Date:** 2026-02-13

## Executive Summary

GitHub App OAuth can give each Android device a user access token scoped to a single repository with read/write Contents access -- no backend server required. The recommended approach combines GitHub App installation (where the user picks a repo) with OAuth authorization in a single flow, uses PKCE for security, and resolves GitHub's HTTPS-only callback URL requirement by hosting a static redirect page on GitHub Pages with Android App Links verification.

There are two viable paths, and one important fallback:

| Approach | UX | Security | Complexity |
|---|---|---|---|
| **Web Flow + GitHub Pages redirect** | Best (seamless) | Good (PKCE + client_secret in APK) | Medium |
| **Device Flow** | Worse (manual code entry) | Best (no client_secret needed) | Low |
| **Web Flow + serverless proxy** | Best (seamless) | Best (secret stays server-side) | Higher |

The web flow with GitHub Pages redirect is the recommended starting point. It gives the best UX with acceptable security tradeoffs for this use case.

---

## 1. GitHub App Registration

### What to Configure

Create a GitHub App at `https://github.com/settings/apps/new` with these settings:

**Identity:**
- **App name:** Max 34 characters, must be unique across GitHub
- **Homepage URL:** Link to your app or its repository
- **Description:** Users see this when they install the app

**Callback & Redirect:**
- **Callback URL:** `https://yourusername.github.io/note-taker/callback` (your GitHub Pages URL)
- You can register up to 10 callback URLs
- **Setup URL:** Optional. Redirects users after installation for additional setup.

**Authorization:**
- **"Request user authorization (OAuth) during installation":** ENABLE THIS. It combines the installation and OAuth authorization into a single user-facing flow.
- **"Enable Device Flow":** Enable this too, as a fallback option.
- **"Expire user authorization tokens":** Keep enabled (GitHub's recommendation). Gives 8-hour tokens with 6-month refresh tokens.

**Permissions (Repository):**
- **Contents:** Read & Write (this is the only permission you need for pushing files)
- Leave everything else at "No access"

**Webhooks:**
- Disable. Not needed for a client-only app.

**Installation scope:**
- "Any account" if you want others to use it; "Only on this account" for personal use

### What You Get After Registration

- **Client ID** -- goes in the app, this is public
- **Client Secret** -- goes in the app (for web flow) or stays unused (for device flow)
- **App ID** -- different from client ID, not used in OAuth flows

---

## 2. The Complete End-to-End Flow

### Path A: Web Application Flow (Recommended for UX)

#### Step 1: User Taps "Connect to GitHub"

The app opens a browser (Chrome Custom Tab) to the GitHub App's installation URL:

```
https://github.com/apps/YOUR-APP-NAME/installations/select_target
```

This takes the user to GitHub's installation UI where they:
1. Choose which account to install on (personal or organization)
2. Select **"Only select repositories"** and pick exactly one repo
3. Authorize the app (OAuth consent -- combined because of the setting above)

#### Step 2: PKCE Setup (Before Opening Browser)

Before launching the browser, the app generates PKCE values:

```
code_verifier = random 43-128 character string (Base64URL encoded)
code_challenge = Base64URL(SHA256(code_verifier))
```

The authorization URL includes:

```
https://github.com/login/oauth/authorize
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=https://yourusername.github.io/note-taker/callback
  &state=RANDOM_STRING_FOR_CSRF
  &code_challenge=BASE64URL_SHA256_HASH
  &code_challenge_method=S256
```

Note: When using the combined install+authorize flow, GitHub handles the redirect to the authorization URL automatically after installation. The app opens the installation URL and GitHub chains through to the authorize endpoint.

#### Step 3: GitHub Redirects to Callback

After authorization, GitHub redirects to:

```
https://yourusername.github.io/note-taker/callback?code=AUTH_CODE&state=YOUR_STATE
```

This is where the HTTPS callback URL solution kicks in (see Section 3).

#### Step 4: App Receives the Authorization Code

The app extracts the `code` and `state` parameters from the callback URL. It verifies that `state` matches what was sent to prevent CSRF attacks.

#### Step 5: Token Exchange

The app makes a direct HTTPS POST (not through a browser):

```
POST https://github.com/login/oauth/access_token
Accept: application/json

client_id=YOUR_CLIENT_ID
&client_secret=YOUR_CLIENT_SECRET
&code=AUTH_CODE_FROM_STEP_4
&redirect_uri=https://yourusername.github.io/note-taker/callback
&code_verifier=ORIGINAL_RANDOM_STRING
&repository_id=REPO_ID  (optional: further restrict to one repo)
```

#### Step 6: Store Token

Response:

```json
{
  "access_token": "ghu_xxxxxxxxxxxx",
  "expires_in": 28800,
  "refresh_token": "ghr_xxxxxxxxxxxx",
  "refresh_token_expires_in": 15897600,
  "token_type": "bearer",
  "scope": ""
}
```

Store the access token, refresh token, and expiration timestamp securely on device (Android Keystore / EncryptedSharedPreferences).

### Path B: Device Flow (Alternative -- Better Security, Worse UX)

#### Step 1: Request Device Code

```
POST https://github.com/login/device/code
  client_id=YOUR_CLIENT_ID
```

Response:

```json
{
  "device_code": "3584d83530557fdd1f46af8289938c8ef79f9dc5",
  "user_code": "WDJB-MJHT",
  "verification_uri": "https://github.com/login/device",
  "expires_in": 900,
  "interval": 5
}
```

#### Step 2: Show Code to User

Display `WDJB-MJHT` and a "Open GitHub" button that launches `https://github.com/login/device` in the browser.

#### Step 3: Poll for Token

```
POST https://github.com/login/oauth/access_token
  client_id=YOUR_CLIENT_ID
  &device_code=3584d83530557fdd1f46af8289938c8ef79f9dc5
  &grant_type=urn:ietf:params:oauth:grant-type:device_code
```

Poll every `interval` seconds until the user completes authorization. No `client_secret` needed.

#### Tradeoff

Device flow has no callback URL problem and no client_secret problem. But the user has to: open a separate browser tab, navigate to github.com/login/device, type in the code, and authorize. This is 3-4 extra steps compared to the web flow.

---

## 3. The HTTPS Callback URL Problem

GitHub only supports HTTPS callback URLs. Android apps need the redirect to land back in the app, not a web page. Here are the solutions ranked from simplest to most robust.

### Option A: GitHub Pages + JavaScript Redirect (Simplest)

Host a static HTML page on GitHub Pages at the callback URL path:

```html
<!-- https://yourusername.github.io/note-taker/callback/index.html -->
<!DOCTYPE html>
<html>
<head><title>Redirecting...</title></head>
<body>
  <p>Redirecting to app...</p>
  <script>
    // Pass through all query params to the app's custom scheme
    const params = window.location.search;
    window.location.href = "notetaker://callback" + params;
  </script>
  <noscript>
    <p>JavaScript is required. Please enable it and try again.</p>
  </noscript>
</body>
</html>
```

The Android app registers an intent filter for the `notetaker://callback` custom scheme. When GitHub redirects to the HTTPS URL, the page loads briefly in the browser, then JavaScript redirects to the custom scheme, which opens the app.

**Pros:** Dead simple. No domain verification needed. Works on all Android versions.
**Cons:** Brief page flash in the browser. Custom scheme redirect is less secure (another app could register the same scheme -- mitigated by PKCE).

### Option B: GitHub Pages + Android App Links (More Robust)

Use Android App Links so the HTTPS URL opens the app directly, without the page loading at all.

**Setup:**

1. Create a GitHub Pages repo (or use an existing one)

2. Add `/.well-known/assetlinks.json`:
```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.yourcompany.notetaker",
    "sha256_cert_fingerprints": [
      "AA:BB:CC:DD:..."
    ]
  }
}]
```

3. Add `_config.yml` to the repo root (critical -- GitHub Pages hides dotfiles by default):
```yaml
include: [".well-known"]
```

4. In `AndroidManifest.xml`, add an intent filter with `autoVerify="true"`:
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="yourusername.github.io"
        android:pathPrefix="/note-taker/callback" />
</intent-filter>
```

5. Also host the JavaScript redirect page as a fallback for when App Links verification fails.

**Pros:** Seamless -- URL opens the app directly. Cryptographically verified domain ownership. Secure.
**Cons:** Requires hosting `assetlinks.json` and getting SHA-256 fingerprints right. Known issues with Chrome Custom Tabs not always respecting App Links (historically buggy). Requires API 23+.

### Option C: Chrome Auth Tab (Newest, Chrome 137+)

Chrome 137 (released January 2025) introduced Auth Tab, a purpose-built Custom Tab for OAuth. It natively handles HTTPS redirects with Digital Asset Links verification.

```kotlin
val authTabIntent = AuthTabIntent.Builder().build()
authTabIntent.launch(
    activity,
    Uri.parse(authUrl),
    redirectHost = "yourusername.github.io",
    redirectPath = "/note-taker/callback"
)
```

**Pros:** Cleanest integration. No intent filter wrangling. Built for exactly this use case.
**Cons:** Chrome 137+ only. Must fall back to Options A/B for older browsers.

### Option D: Serverless Function Redirect

A minimal AWS Lambda / Cloud Function / Cloudflare Worker at your own domain that receives the GitHub callback and redirects to a custom scheme:

```javascript
// e.g., Cloudflare Worker
export default {
  async fetch(request) {
    const url = new URL(request.url);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    return Response.redirect(
      `notetaker://callback?code=${code}&state=${state}`, 302
    );
  }
}
```

**Pros:** Reliable redirect. Could also handle the token exchange (keeping client_secret server-side).
**Cons:** Adds infrastructure. Not truly "self-contained client."

### Recommendation

**Start with Option A (GitHub Pages + JavaScript redirect)** for simplicity. It works, requires no infrastructure, and the security gap (custom scheme hijacking) is mitigated by PKCE -- even if another app intercepted the redirect, it cannot complete the token exchange without the `code_verifier`.

Add Option B (App Links verification) as an enhancement if you want the seamless experience and have time to debug the `assetlinks.json` setup.

Consider Option C (Auth Tab) as a forward-looking upgrade path for newer devices.

---

## 4. Token Lifecycle

### With Expiring Tokens Enabled (Recommended)

| Token | Prefix | Lifetime | Storage |
|---|---|---|---|
| Access token | `ghu_` | 8 hours | EncryptedSharedPreferences |
| Refresh token | `ghr_` | 6 months | EncryptedSharedPreferences |

### Refresh Flow

When the access token expires (check `expires_in` or track expiration timestamp):

```
POST https://github.com/login/oauth/access_token
  client_id=YOUR_CLIENT_ID
  &client_secret=YOUR_CLIENT_SECRET
  &grant_type=refresh_token
  &refresh_token=ghr_xxxxxxxxxxxx
```

Response: new access token + new refresh token. The old refresh token is invalidated (token rotation).

### What the App Must Store

- `access_token` -- for GitHub API calls
- `refresh_token` -- for renewing access tokens
- `token_expiry` -- timestamp to know when to refresh (compute from `expires_in`)
- `client_id` + `client_secret` -- baked into the app binary, needed for refresh

### Edge Cases

- **Refresh token expires (6 months of inactivity):** User must re-authorize through the full flow
- **User revokes authorization on GitHub:** API calls return 401; app must clear stored tokens and re-prompt
- **Token rotation failure (network error during refresh):** The old refresh token may already be invalidated. If the app didn't save the new one, the user must re-authorize.

### Without Expiring Tokens

If you disable token expiration in the GitHub App settings, access tokens never expire and no refresh token is issued. Simpler but less secure -- a leaked token works forever until manually revoked.

---

## 5. Scope and Permissions

### How Permission Scoping Works

The user access token's effective permissions are the **intersection** of three layers:

```
Effective permissions = min(App permissions, Installation scope, User permissions)
```

1. **GitHub App permissions** (configured at registration): Sets the ceiling. If you configure "Contents: Read & Write" only, the token can never do more than that -- even if the user is an admin.

2. **Installation repository selection** (chosen by the user during install): Limits which repos the token can access. If the user selects one repo, the token only works on that repo.

3. **User's own permissions**: If the user only has read access to a repo but the app has read/write, the token gets read-only for that repo.

### Single-Repo Enforcement

For this use case, the user selects exactly one repo during installation. The token is automatically restricted to that repo through the installation scope. As a belt-and-suspenders measure, you can also pass `repository_id` during the token exchange to further restrict the token to a single specific repository.

### What the Token Can Do

With "Contents: Read & Write" as the only permission:
- Read file contents, directory listings
- Create, update, delete files via the Contents API
- Read and create commits (needed for pushing)
- Cannot: manage issues, PRs, releases, settings, webhooks, or anything else

---

## 6. Security Considerations

### Client Secret in the APK

**The risk is real but bounded.** The `client_secret` can be extracted from any APK using basic reverse engineering tools (`strings`, `jadx`, `apktool`). R8/ProGuard obfuscation slows but does not prevent extraction.

**What an attacker can do with the extracted secret:**
- Impersonate the app during OAuth flows
- Combined with PKCE, they still need the user to authorize AND the `code_verifier` to complete the exchange

**What an attacker cannot do:**
- Access any user's data (tokens are per-user, require user interaction)
- Use the secret alone to read or write repos
- Escalate beyond the app's configured permissions

**Mitigations:**
- PKCE makes the authorization code useless without the code_verifier
- Short-lived tokens (8 hours) limit exposure window
- Refresh token rotation detects replay attacks
- The app only has Contents read/write permission -- no admin access even if compromised

**Pragmatic assessment:** Many production Android apps ship GitHub/Google/Twitter client secrets in their APKs. It is not ideal but is a widely accepted tradeoff for client-only apps. The real question is what damage can an attacker do with it, and here the answer is: very little, because every token exchange requires active user participation.

### PKCE Protection

PKCE (Proof Key for Code Exchange) addresses the core vulnerability of public clients:

- **Without PKCE:** An attacker who intercepts the authorization code during the redirect (e.g., via a malicious app registered on the same custom scheme) can exchange it for a token using the known client_secret.
- **With PKCE:** The authorization code is bound to a `code_verifier` that only the originating app knows. Intercepting the code is useless without the verifier.

GitHub supports SHA-256 (`S256`) code challenges only. The `plain` method is not supported.

### Token Storage

Store tokens using Android's `EncryptedSharedPreferences` (part of AndroidX Security) backed by the Android Keystore. This provides hardware-backed encryption on devices that support it.

### Other Considerations

- **Custom scheme hijacking:** Another app could register the same custom URI scheme and intercept the redirect. PKCE prevents exploitation. App Links eliminates this entirely.
- **Network interception:** All GitHub OAuth endpoints use HTTPS. Certificate pinning adds defense against MITM but is generally overkill for this threat model.
- **The client_secret can be rotated:** If it leaks publicly, generate a new one in GitHub App settings and ship an app update.

---

## 7. Android Implementation Details

### Library Choice

**AppAuth-Android** (`net.openid:appauth`) is the standard library for OAuth on Android. It handles Custom Tabs, PKCE, token exchange, and token refresh. However, GitHub's OAuth is simple enough that a manual implementation is also reasonable.

### High-Level Architecture (Manual Implementation)

```kotlin
// 1. Generate PKCE values
val codeVerifier = generateCodeVerifier() // 43-128 char random Base64URL
val codeChallenge = generateCodeChallenge(codeVerifier) // SHA-256 + Base64URL

// 2. Build authorization URL
val authUrl = Uri.Builder()
    .scheme("https")
    .authority("github.com")
    .path("/login/oauth/authorize")
    .appendQueryParameter("client_id", CLIENT_ID)
    .appendQueryParameter("redirect_uri", CALLBACK_URL)
    .appendQueryParameter("state", generateState())
    .appendQueryParameter("code_challenge", codeChallenge)
    .appendQueryParameter("code_challenge_method", "S256")
    .build()

// 3. Launch Custom Tab
val customTabsIntent = CustomTabsIntent.Builder().build()
customTabsIntent.launchUrl(context, authUrl)

// 4. Handle callback in Activity (via intent filter)
// Extract code and state from intent.data

// 5. Exchange code for token (background thread / coroutine)
val response = httpClient.post("https://github.com/login/oauth/access_token") {
    parameter("client_id", CLIENT_ID)
    parameter("client_secret", CLIENT_SECRET)
    parameter("code", authCode)
    parameter("redirect_uri", CALLBACK_URL)
    parameter("code_verifier", codeVerifier)
}

// 6. Store tokens securely
encryptedPrefs.edit {
    putString("access_token", response.accessToken)
    putString("refresh_token", response.refreshToken)
    putLong("token_expiry", System.currentTimeMillis() + response.expiresIn * 1000)
}
```

### AndroidManifest.xml

```xml
<!-- Activity that handles the OAuth callback -->
<activity
    android:name=".OAuthCallbackActivity"
    android:exported="true"
    android:launchMode="singleTask">

    <!-- For App Links (HTTPS, verified) -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="yourusername.github.io"
            android:pathPrefix="/note-taker/callback" />
    </intent-filter>

    <!-- For custom scheme fallback -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="notetaker"
            android:host="callback" />
    </intent-filter>
</activity>
```

### Token Refresh Logic

```kotlin
suspend fun getValidToken(): String {
    val expiry = encryptedPrefs.getLong("token_expiry", 0)
    if (System.currentTimeMillis() < expiry - 300_000) { // 5 min buffer
        return encryptedPrefs.getString("access_token", "")!!
    }

    // Token expired or about to expire -- refresh
    val refreshToken = encryptedPrefs.getString("refresh_token", "")!!
    val response = httpClient.post("https://github.com/login/oauth/access_token") {
        parameter("client_id", CLIENT_ID)
        parameter("client_secret", CLIENT_SECRET)
        parameter("grant_type", "refresh_token")
        parameter("refresh_token", refreshToken)
    }

    // Store new tokens (old refresh token is now invalid)
    encryptedPrefs.edit {
        putString("access_token", response.accessToken)
        putString("refresh_token", response.refreshToken)
        putLong("token_expiry", System.currentTimeMillis() + response.expiresIn * 1000)
    }

    return response.accessToken
}
```

### Using the Token for Git Operations

The user access token works as an HTTP Basic auth password for Git over HTTPS:

```
https://x-access-token:{TOKEN}@github.com/{owner}/{repo}.git
```

Or via the GitHub REST API Contents endpoint for individual file operations:

```
PUT https://api.github.com/repos/{owner}/{repo}/contents/{path}
Authorization: Bearer ghu_xxxxxxxxxxxx
```

---

## 8. Real-World Examples and Patterns

### Open Source References

- **AppAuth-Android** (`openid/AppAuth-Android`) -- the canonical OAuth library for Android, supports PKCE and Custom Tabs
- **github-oauth** (`geniushkg/github-oauth`) -- a simple Android library for GitHub OAuth
- **oauth.mobilesample.android** (`gary-archer/oauth.mobilesample.android`) -- complete OpenID Connect sample using AppAuth pattern

### Common Pattern for GitHub OAuth on Android

Most production apps that do GitHub OAuth on Android use one of:

1. **Backend proxy for token exchange** -- the most common production pattern. The app opens a browser, GitHub redirects to a backend server, the server exchanges the code for a token (keeping client_secret server-side), and returns the token to the app.

2. **Client-side token exchange with PKCE** -- less common but viable. The client_secret is in the APK, and PKCE provides the primary security guarantee. This is the "no backend" approach.

3. **Device Flow** -- used by CLI tools and apps like GitHub CLI. Simple and secure but worse UX for a mobile app.

### No Established Pattern for "GitHub App OAuth + Android + No Backend"

This specific combination (GitHub App OAuth with combined install+authorize flow, client-only Android, PKCE, GitHub Pages redirect) is not a well-documented pattern. It is assembling known pieces in a somewhat novel way. Each piece is well-understood individually, but the integration requires careful attention to the callback URL handling.

---

## Summary: Recommended Implementation Plan

1. **Register a GitHub App** with Contents (Read & Write), "Request user authorization during installation" enabled, token expiration enabled, device flow enabled
2. **Set up a GitHub Pages site** with:
   - A callback HTML page that does a JavaScript redirect to a custom URI scheme
   - An `assetlinks.json` for App Links verification (optional enhancement)
   - A `_config.yml` with `include: [".well-known"]`
3. **Implement the OAuth flow in the Android app:**
   - Open the GitHub App installation URL in a Custom Tab
   - Handle the callback via intent filter (custom scheme and/or App Links)
   - Exchange the code for a token with PKCE
   - Store tokens in EncryptedSharedPreferences
   - Implement token refresh logic
4. **Keep Device Flow as a fallback** for users who have trouble with the web flow
5. **Use the token** for Git operations over HTTPS or the GitHub Contents API
