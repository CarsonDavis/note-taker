# Changelog

## v0.6.0 (in progress)

**What's New**
- High-accuracy dictation no longer loses words to bad service. The mic now records continuously into a rolling 60-second buffer that outlives the cloud connection — if the connection drops or stalls mid-dictation, the status shows **Reconnecting…**, you keep talking, and everything you said is transcribed once the connection returns. Verified in the field: a full sentence spoken entirely in airplane mode appeared in the note after reconnecting, across two consecutive outages. When there's provably no network, it waits out the full 60 seconds before falling back to on-device (on-device can't transcribe offline either, so bailing early only threw words away).
- Honest mic status. The indicator now distinguishes **Connecting…** (session starting — your words are already being captured and will transcribe once it's up), **Listening…** (actively transcribing), **Reconnecting…** (connection dropped, mic still hot, words buffered), and **Mic idle**. Previously "connecting" masqueraded as listening — including one failure mode where the session was never actually configured and transcribed nothing while claiming to listen.
- Submitting while reconnecting now asks first. If you hit Submit while the connection is down, your last words may not be in the note yet — GitJot offers to wait or submit what's shown, instead of silently saving a note missing its tail.
- Optional high-accuracy voice input. Settings now has a **Voice Input** section where you can switch from on-device dictation to cloud transcription powered by OpenAI. It's bring-your-own-key: paste your own OpenAI API key (stored encrypted on your device, never sent anywhere but OpenAI). In this mode your audio is streamed to OpenAI while you dictate; on-device stays the default and the disclosure is shown right in Settings.
- Graceful fallback if cloud voice fails. If high-accuracy transcription stops mid-dictation — your OpenAI credit runs out, the key is rejected, the connection drops — GitJot now switches to on-device voice automatically so you keep dictating, and shows a banner explaining what happened with one-tap **Retry high-accuracy**. The fallback lasts only until you next open the app to dictate: high accuracy is retried automatically each new session, and your saved key is never touched. Switching engines permanently is done in Settings ("On-device only" / "High accuracy").

**What's Changed**
- Removed the topic bar from the top of the note screen — notes now carry their own context, so a single repo-wide topic is no longer shown. The browse and settings icons remain.

**Improved**
- Dictation drops fewer words between phrases — the voice recognizer now reuses its session instead of fully tearing down and rebuilding on every pause, which roughly cut in half the brief gap where speech wasn't being captured. (Further refinement in progress.)
- Cloud transcription is now locked to English, so it no longer occasionally transcribes in another language.
- High-accuracy dictation now uses OpenAI's newer `gpt-transcribe` model instead of `gpt-4o-transcribe` — OpenAI's current-generation speech-to-text, and cheaper per minute on your own API key ($0.0045/min vs $0.006/min).
- The screen no longer sleeps while you're dictating. (While you're typing or just reading the note it can sleep normally.)
- An unsent note is now saved to your device as you go and restored the next time you open GitJot, so a crash, a low-memory kill, or swiping the app away can no longer lose what you've dictated. It's cleared once the note is sent or queued.

**Bug Fix**
- Fixed the mic silently turning off mid-dictation and staying dead until you tapped the text box. Three causes: the cloud connection closing without triggering any recovery, rare on-device recognizer errors being treated as fatal, and nothing in the app ever re-arming a mic that died on its own. Dictation now recovers automatically — a watchdog restarts the mic within seconds whenever it stops while you still want it on.
- Fixed changing voice settings turning the microphone on while you're still on the Settings screen.
- Fixed a watchdog blind spot where a failed restart attempt could leave the mic dead with no further retries — the watchdog now keeps retrying on a backoff loop until the mic is back or you turn it off.
- Fixed a spurious "Client error" message appearing after ending a dictation session.
- Fixed the mic occasionally being inactive right after opening the app — a startup race when cloud voice was the selected engine.

## v0.5.2

**Bug Fix**
- Fixed OAuth sign-in on Play Store builds — renamed secrets from `GITHUB_CLIENT_ID` to `OAUTH_CLIENT_ID` (GitHub reserves the `GITHUB_` prefix for its own use, silently blocking secret creation) and switched CI to write credentials to `local.properties` instead of env vars

## v0.5.1

**Bug Fix**
- Attempted fix for OAuth sign-in on Play Store builds — added env vars to CI but the `GITHUB_`-prefixed secrets could not be created (see v0.5.2)

## v0.5.0

**What's New**
- Settings restructured into "Device Connection" and "GitJot on GitHub" cards
- "Disconnect" replaces "Sign Out" — honest about what it does
- One-tap reconnect after disconnecting (no need to reinstall the GitHub App)
- Repo selection dialog when GitJot has access to multiple repositories
- Session expired errors when your token is revoked or invalid
- "Manage on GitHub" button to change repos or uninstall GitJot

**Clearer Settings**
- "Device Connection" card shows your username, repo, and auth method with a Disconnect button
- Helper text explains that disconnecting only removes credentials from this device and reconnecting is one tap
- "GitJot on GitHub" card (OAuth only) describes exact permissions — read & write file contents, nothing else
- "Manage on GitHub" opens GitHub's installation settings to change repos or uninstall

**Disconnect Flow**
- "Disconnect" terminology replaces "Sign Out" — this device disconnects, GitJot stays on GitHub
- Confirmation dialog warns about unsent notes (all auth types)
- OAuth token revoked on GitHub's side
- Reconnecting is one tap since the GitHub App stays installed

**Auth Improvements**
- Repo selection dialog when the GitHub App has access to multiple repos
- Session expired errors on note submit and browse when token is invalid
- Worker stops retrying uploads on auth failure instead of looping
- Better error messages for OAuth edge cases (stale installation, no repos)

## v0.4.0

**What's New**
- Sign in with GitHub (OAuth) — one tap to connect, no tokens to manage
- Redesigned setup screen with a cleaner two-step card layout
- Help icons explain the notes repo template and GitHub permissions
- Sign-out revokes access and prompts to uninstall the GitHub App

**GitHub OAuth**
- "Sign in with GitHub" as the primary auth method — installs the GitJot GitHub App on one repo you choose
- PKCE-protected OAuth flow with EncryptedSharedPreferences token storage
- Personal Access Token remains available as a manual fallback

**Setup Screen Redesign**
- Two clear cards: "1. Create Your Notes Repo" and "2. Connect Your Repo"
- Each card has a description and a single action button
- Help icon on card 1 explains the template repo and the Claude Code inbox processor agent
- Help icon on card 2 explains exactly what permissions GitJot gets (read/write one repo, nothing else)
- PAT flow toggles inline within the connect card instead of expanding a separate section
- Updated tagline: "Your voice notes, saved to Git, organized by AI."

**Clean Sign-Out**
- OAuth sign-out shows a confirmation dialog explaining that the GitHub App will remain installed
- "Uninstall from GitHub" button opens GitHub Settings to fully remove the app
- Access token is revoked on GitHub's side when you sign out
- PAT users sign out immediately with no changes to their experience

## v0.3.0

**What's New**
- "Need help?" link on the setup screen opens a YouTube walkthrough video
- Settings now walks you through both steps to enable the side button shortcut

**Setup Help Video**
- Added a "Need help? Watch the setup walkthrough" link at the bottom of the auth screen
- Opens a YouTube video that walks through the full setup process

**Side Button Setup Guide**
- The Digital Assistant settings card now has two clearly numbered steps
- Step 1: Set GitJot as your default digital assistant (existing)
- Step 2: Change your side button's long-press from Bixby to Digital assistant (new)
- "Open Side Button Settings" takes you directly to Samsung's side key settings
- Graceful fallback on non-Samsung devices

## v0.2.0

**What's New**
- Step-by-step setup walkthrough with help icons and PAT instructions
- Distinct error messages for invalid token vs missing repository
- Note input field expands to fill the screen with a scrollbar on overflow
- First-launch dialog introduces the side-button shortcut
- Settings accessible from every screen

**Guided Setup Flow**
- Redesigned the auth screen as a clear 4-step walkthrough: fork the notes repo, enter your repository, generate a token, paste it in
- Each step is numbered with help icons that explain what to enter and how your token is stored
- "Generate Token on GitHub" now shows detailed instructions before opening the browser
- Repository field accepts `owner/repo` or a full GitHub URL — no more guessing the format

**Better Error Messages**
- Invalid token and wrong repository now show distinct error messages instead of a generic failure
- "Personal access token is invalid" vs "Repository not found — check the name and token permissions"

**Digital Assistant Onboarding**
- First-time dialog explains the side-button shortcut and offers to open system settings to enable it
- Dismissed permanently until you sign out and back in

**Growing Text Field**
- Note input field now expands to fill available screen space instead of a fixed height
- Scrollbar appears when text overflows and fades out after scrolling

**Settings Access**
- Settings gear icon added to the Browse screen — accessible from every authenticated screen
