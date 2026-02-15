# Changelog

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
