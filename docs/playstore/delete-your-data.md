# How to Delete Your Data — Note Taker

Note Taker stores all data locally on your device. The developer has no servers and no access to your data. You can delete everything directly from the app.

## Delete from within the app

1. Open Note Taker
2. Tap the gear icon to open **Settings**
3. Scroll to **Delete All Data** and tap the red button
4. Confirm in the dialog

### What gets deleted

- **Submission history** — local record of past notes
- **Pending notes** — any notes queued for upload
- **GitHub credentials** — your personal access token, username, and repository info
- **Pending upload jobs** — any background retry tasks

All data is removed immediately. There is no retention period.

### What is NOT deleted

- **Notes already pushed to your GitHub repository** — these live in your own repo and are not controlled by the app. To delete them, use GitHub's web interface, API, or git.

## Alternative: Android system settings

You can also clear all app data from Android Settings → Apps → Note Taker → Clear data. This has the same effect.

## Revoke GitHub access

To revoke the app's ability to access your repository, delete your personal access token on GitHub: Settings → Developer settings → Personal access tokens.
