# Privacy Policy — Note Taker

*Last updated: 2026-02-12*

Note Taker is a free, open-source Android application developed by Carson Davis. This privacy policy describes how the app handles data.

**The key point: Note Taker does not collect, store, or transmit any data to the developer or any third party.** The app is a tool that stores your data locally on your device and sends it only to your own GitHub repository at your direction.

## No Data Collection

Note Taker does **not** collect any user data. The developer has no servers, no analytics, no tracking, and no way to access anything you do in the app. Specifically:

- No analytics services (no Google Analytics, Firebase, Mixpanel, etc.)
- No advertising networks
- No crash reporting services (no Crashlytics, Sentry, etc.)
- No social media SDKs
- No tracking or fingerprinting
- No data is ever sent to the developer or any third party

## What the App Does With Your Data

Note Taker is a tool that works entirely on your behalf:

### Local storage (on your device only)
All app data stays on your device in the app's private directory:

| Data | Storage method | Purpose |
|------|---------------|---------|
| GitHub PAT | Preferences DataStore (app-private) | Authenticating your GitHub API requests |
| GitHub username | Preferences DataStore (app-private) | Displaying your username in the app |
| Repository owner/name | Preferences DataStore (app-private) | Knowing which repo to push notes to |
| Pending notes | Room database (app-private) | Offline queue — notes waiting to upload |
| Submission history | Room database (app-private) | Showing your recent submission history |

### Transmission to your own GitHub repository
When you submit a note, the app sends it to the GitHub Contents API (`api.github.com`) over HTTPS to create a markdown file in **your own repository**. Your GitHub personal access token (PAT) is sent as an authorization header. This is no different from you pushing a file to GitHub yourself — the app is just the tool that does it.

### Speech recognition
Note Taker uses Android's built-in `SpeechRecognizer` API for voice-to-text input. Speech processing is handled by your device's default speech recognition service (typically Google). **No audio is recorded or stored by Note Taker.** The app receives only the transcribed text. Your device's speech service may process audio according to its own privacy policy.

### Data NOT accessed
Note Taker does **not** access:
- Location data
- Contacts or address book
- Camera or photos
- Call logs or SMS
- Device identifiers (IMEI, advertising ID, etc.)
- Browsing history
- Calendar data
- Files outside the app's own storage

## Third-Party Services

The only external service Note Taker communicates with directly is the **GitHub API** (`api.github.com`), and only to read from and write to your own repository at your direction.

Additionally, speech-to-text input is processed by your device's default speech recognition service (typically Google's speech services). Note Taker does not control this service — it is part of your Android system.

Relevant third-party privacy policies:
- **GitHub:** https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement
- **Google Speech Services:** https://policies.google.com/privacy

## Data Retention

- **Local data** is retained on your device until you clear the app's data or uninstall the app.
- **Notes in GitHub** are retained in your repository until you delete them. Note Taker does not automatically delete notes from GitHub.

## Your Rights

You can:
- **Delete local data** at any time: Android Settings → Apps → Note Taker → Clear data
- **Delete notes from GitHub** using GitHub's web interface, API, or git
- **Revoke access** by deleting your personal access token on GitHub (Settings → Developer settings → Personal access tokens)
- **Uninstall the app** to remove all local data from your device

## Children's Privacy

Note Taker is not directed at children under 13. We do not knowingly collect personal information from children.

## Changes to This Policy

If this privacy policy changes, the updated version will be published in this repository with an updated date at the top.

## Contact

For questions about this privacy policy, open an issue on the GitHub repository or contact the developer.

Source code: https://github.com/CarsonDavis/note-taker
