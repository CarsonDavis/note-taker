# Changelog

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
