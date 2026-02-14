# Changelog

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
