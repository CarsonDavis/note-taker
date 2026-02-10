# Background Research: GitHub OAuth Authentication for Android Apps

**Date:** 2026-02-09
**Topic:** Best practices for GitHub OAuth authentication in Android mobile/native apps (2025-2026)

## Sources

[1]: https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/ "PKCE support for OAuth and GitHub App authentication - GitHub Changelog"
[2]: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/best-practices-for-creating-an-oauth-app "Best practices for creating an OAuth app - GitHub Docs"
[3]: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps "Authorizing OAuth apps - GitHub Docs"
[4]: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps "Differences between GitHub Apps and OAuth apps - GitHub Docs"
[5]: https://github.com/orgs/community/discussions/15752 "Support PKCE flow for OAuth apps - GitHub Community Discussion"
[6]: https://github.com/orgs/community/discussions/54568 "Can the Client Secret Safely Be Public? - GitHub Community Discussion"
[7]: https://github.com/openid/AppAuth-Android "AppAuth-Android - OpenID Foundation"
[8]: https://approov.io/blog/strengthening-oauth2-for-mobile "Enhancing Mobile Security: Strengthening OAuth2 with App Attestation"
[9]: https://dev.to/theplebdev/oauth20-and-android-login-with-github-and-get-the-authorization-code-40n3 "OAuth2.0 and Android: Login with Github"
[10]: https://github.blog/changelog/2022-03-16-enable-oauth-device-authentication-flow-for-apps/ "Enable OAuth Device Authentication Flow for Apps - GitHub Changelog"
[11]: https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app "Generating a user access token for a GitHub App - GitHub Docs"
[12]: https://www.rfc-editor.org/rfc/rfc8252.html "RFC 8252: OAuth 2.0 for Native Apps"
[13]: https://curity.io/resources/learn/oauth-for-mobile-apps-best-practices/ "OAuth for Mobile Apps - Best Practices | Curity"
[14]: https://oauth.net/2/native-apps/ "OAuth 2.0 for Mobile and Native Apps"
[15]: https://www.oauth.com/oauth2-servers/oauth-native-apps/redirect-urls-for-native-apps/ "Redirect URLs for Native Apps - OAuth 2.0 Simplified"
[16]: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens "Managing your personal access tokens - GitHub Docs"
[17]: https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/deciding-when-to-build-a-github-app "Deciding when to build a GitHub App - GitHub Docs"
[18]: https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-user-authorization-callback-url "About the user authorization callback URL - GitHub Docs"
[19]: https://github.com/orgs/community/discussions/61238 "How to customize the redirect URL when registering a GitHub App?"

## Research Log

---

### Search: "GitHub OAuth authorization native mobile apps documentation 2025 2026"

- **GitHub added PKCE support on July 14, 2025** for both OAuth Apps and GitHub Apps ([GitHub Changelog][1])
- **PKCE is now recommended** for user authentication in OAuth and GitHub Apps — it protects authorization codes by ensuring only the client that initiated auth can exchange the code for a token ([GitHub Changelog][1])
- **Public clients (native apps) cannot secure client secrets** — GitHub acknowledges this explicitly. You "will have to ship the client secret in the application's code" and "should use PKCE to better secure the authentication flow" ([Best Practices][2])
- **Authorization code with PKCE is preferred over device flow** for public clients. The device flow is vulnerable to phishing because it doesn't require redirect URIs, so GitHub recommends against enabling it unless the app runs in a constrained environment (CLIs, IoT, headless systems) ([Best Practices][2])
- **Loopback URLs supported for native apps** — the optional redirect_uri can use loopback URLs for native desktop apps ([Authorizing OAuth apps][3])

---

### Search: "GitHub OAuth PKCE support mobile apps"

- **PKCE works with both OAuth Apps and GitHub Apps** — not limited to one type ([GitHub Changelog][1])
- **Only S256 code challenge method is accepted** — plain is not supported ([GitHub Changelog][1])
- **GitHub does not currently require PKCE** — it's optional but recommended. GitHub "does not distinguish between public and confidential clients" formally ([GitHub Changelog][1])
- **PKCE was originally designed for mobile apps** but is now recommended for all OAuth clients ([GitHub Changelog][1])

---

### Fetch: GitHub Changelog PKCE announcement + GitHub Docs Authorizing OAuth Apps

**Critical finding: client_secret is STILL REQUIRED even when using PKCE.** The GitHub docs list `client_secret` as "required" in the token exchange endpoint parameters, alongside the PKCE `code_verifier` which is "strongly recommended." PKCE does not replace the client secret — it adds an additional layer of protection. ([Authorizing OAuth apps][3])

**Full authorization request parameters** (GET `https://github.com/login/oauth/authorize`):
- `client_id` (required)
- `redirect_uri` (strongly recommended)
- `scope` (context dependent)
- `state` (strongly recommended)
- `code_challenge` (strongly recommended with PKCE)
- `code_challenge_method` (strongly recommended with PKCE)
- `login`, `allow_signup`, `prompt` (optional)

**Full token exchange parameters** (POST `https://github.com/login/oauth/access_token`):
- `client_id` (required)
- `client_secret` (required)
- `code` (required)
- `redirect_uri` (strongly recommended)
- `code_verifier` (strongly recommended with PKCE)

([Authorizing OAuth apps][3])

**Loopback URLs:** Docs recommend `127.0.0.1` over `localhost` for native desktop apps. No mention of Android Custom Tabs, deep links, or mobile-specific redirect patterns. ([Authorizing OAuth apps][3])

**PKCE is NOT used with device code flow** or installation token flows. ([GitHub Changelog][1])

---

### Search: "GitHub Apps vs OAuth Apps mobile native client authentication differences"

- **GitHub Apps are generally preferred over OAuth Apps** because they use fine-grained permissions, give more control over repository access, and use short-lived tokens ([Differences][4])
- **GitHub Apps use JWT authentication** — app generates JWT, GitHub verifies it, returns short-lived Installation Access Token (typically 1 hour validity) ([Differences][4])
- **OAuth App tokens do not expire** until the person who authorized them revokes the token ([Differences][4])
- **GitHub Apps have short-lived tokens** — if leaked, damage is time-limited ([Differences][4])

---

### Search: "GitHub OAuth mobile app client secret required PKCE public client workaround"

- **GitHub ALWAYS requires client_secret** — they don't yet distinguish between public and confidential clients for the token exchange ([Community Discussion][5])
- **GitHub's explicit recommendation for mobile apps:** "You will have to ship the client secret in the application's code, and you should use PKCE to better secure the authentication flow" ([Best Practices][2])
- **Removing client_secret requirement is on GitHub's public roadmap** for SPA and native app support ([Community Discussion][5])
- **Security nuance about exposed client_secret:** "The client secret can only be used to manipulate a token someone has in possession — there's nothing it can do without a token." However, caution is needed if gating access to your own services based on tokens, because "public clients are trivially spoofable — anyone can reuse your app's client ID to sign in." ([Community Discussion][6])
- **Current options for mobile:** (1) Ship client_secret in app code + use PKCE, or (2) Use Device Flow (less convenient, user must input a code) ([Community Discussion][5])

---

### Search: "GitHub OAuth Android app backend proxy token exchange client secret security pattern"

- **Backend token exchange is the recommended security pattern** — have the Android app handle auth/redirect, but proxy the token exchange through your backend where client_secret stays hidden ([AppAuth-Android][7], [Approov][8])
- **AppAuth-Android (OpenID Foundation)** follows RFC 8252 (OAuth 2.0 for Native Apps) best practices, including Custom Tabs for auth requests and PKCE support ([AppAuth-Android][7])
- **Architecture pattern:** Android app handles user auth + gets authorization code via Custom Tab redirect -> sends code to your backend -> backend exchanges code for token using client_secret -> returns token to app ([AppAuth-Android][7], [Dev.to Tutorial][9])

---

### Search: "GitHub device flow OAuth mobile app authentication 2025"

- **Device flow does NOT require client_secret** — only client_id is needed, no URL redirect either ([Device Flow Changelog][10])
- **Device flow must be manually enabled** on the OAuth App or GitHub App settings — it's off by default to reduce phishing risk ([Device Flow Changelog][10])
- **Device flow UX:** App displays a user code -> user goes to github.com/login/device and enters code -> app polls for auth status -> once user authorizes, app gets access token ([Authorizing OAuth apps][3])
- **GitHub explicitly discourages device flow for mobile apps** that can use browser redirects, because the flow lacks redirect URI binding and is more vulnerable to phishing ([Best Practices][2])

---

### Search: "GitHub App user OAuth flow 'generating a user access token' client_secret required"

- **GitHub Apps also require client_secret for the web flow** — "Client secrets are required to generate user access tokens for your app, unless your app uses the device flow" ([GitHub App User Access Token Docs][11])
- **GitHub Apps have the same token exchange flow as OAuth Apps** for user-facing auth — authorization code grant with client_secret required ([GitHub App User Access Token Docs][11])
- **Device flow is the only flow that avoids client_secret entirely** for both OAuth Apps and GitHub Apps ([GitHub App User Access Token Docs][11])

---

### Search: "RFC 8252 OAuth 2.0 native apps Android Custom Tabs best practice 2025 2026"

- **RFC 8252 is the standard for OAuth in native apps** — it requires using an external user-agent (browser or in-app browser tab), NOT embedded WebViews ([RFC 8252][12])
- **Custom Tabs are RECOMMENDED on Android** — they're "in-app browser tabs" that present the browser within the app context while preserving shared auth state and security context ([RFC 8252][12], [Curity][13])
- **Embedded WebViews are prohibited** — they're susceptible to phishing and don't share browser state ([RFC 8252][12])
- **AppAuth-Android is the reference implementation** of RFC 8252 for Android ([OAuth.net][14], [AppAuth-Android][7])

---

### Search: "GitHub OAuth redirect URI Android custom scheme app links deep link callback"

- **GitHub reportedly only supports HTTPS redirect URIs** — not custom URI schemes ([Redirect URLs for Native Apps][15])
- **Two redirect approaches for Android:** Custom URI schemes (e.g., `myapp://callback`) work on all Android versions; App Links (verified HTTPS deep links) require API 23+ and domain verification ([Redirect URLs for Native Apps][15])
- **Custom URI schemes are less secure** — no global registry, risk of conflicts/interception by other apps. HTTPS App Links are more secure but more complex to set up ([Redirect URLs for Native Apps][15])

---

### Search: "GitHub fine-grained personal access token API mobile app alternative to OAuth 2025 2026"

- **Fine-grained PATs offer repo-level and permission-level granularity** — more secure than classic PATs ([Managing PATs][16])
- **PATs are not suitable for multi-user mobile apps** — tied to a single user, manually generated ([Managing PATs][16])
- **For a personal/single-user app, a PAT could work** — but this is a static credential approach, not OAuth ([Managing PATs][16])

---

### Search: "GitHub OAuth app callback URL custom scheme redirect native mobile accepted"

- **Confirmed: GitHub does NOT support custom URI schemes** for callback URLs — only HTTPS URLs are accepted ([About the user authorization callback URL][18], [Community Discussion][19])
- **Workaround for mobile:** Use an HTTPS callback URL pointing to your backend/web server, which then redirects to your app via custom scheme or Android App Links ([Community Discussion][19])
- **This means the "pure client-side" OAuth flow is not possible with GitHub on Android** — you need either a backend intermediary for the redirect, or Android App Links with a verified domain.

---

### Search: "GitHub OAuth remove client secret requirement public client 2026 roadmap update"

- **No evidence of GitHub removing the client_secret requirement as of Feb 2026** — the roadmap item from community discussions has not been shipped
- **Current status unchanged:** client_secret remains required for all authorization code flows
- **GitHub's stance:** Ship the secret in mobile apps and use PKCE to add protection ([Best Practices][2])

---

### Fetch: GitHub Best Practices page — exact quotes

> "If your app is a public client (a native app that runs on the user's device, CLI utility, or single-page web application), you cannot secure your client secret."

> "you should use PKCE to better secure the authentication flow"

> "The device flow does not require redirect URIs at all, which means that an attacker can use the device flow to remotely impersonate your app as part of a phishing attack."

> "do not enable the device flow for your application unless you are using the app in a constrained environment (CLIs, IoT devices, or headless systems)"

> "you should use caution if you plan to gate access to your own services based on tokens generated by your app because public clients are trivially spoofable"

([Best Practices][2])
