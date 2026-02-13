# Data Safety Declaration — Note Taker

Answers for the Google Play Console Data Safety section.

## Overview

Note Taker does **not** collect any user data. The developer has no servers, no analytics, and no access to anything users do in the app. All data stays on the user's device or is sent to the user's own GitHub repository at their direction — the app is just a tool, like a git client.

> **Note on Google Play's form:** Google's data safety form defines "collected" as any data transmitted off the device, even to user-controlled destinations. Because note text is sent to the GitHub API (to write to the user's own repo), we must declare it in the form below. But the developer never receives, sees, or has access to any of this data.

## Section 1: Data Collection & Sharing

### Does your app collect or share any of the required user data types?

**Yes** (per Google's definition — data leaves the device to reach the GitHub API). In practice, the app sends data only to the user's own GitHub repository. The developer does not collect, receive, or have access to any user data.

### Is all of the user data collected by your app encrypted in transit?

**Yes** — all data is transmitted over HTTPS to the GitHub API (`api.github.com`).

### Do you provide a way for users to request that their data be deleted?

**Yes** — users can delete notes directly from their GitHub repository. Users can also clear all local app data (Settings → Apps → Note Taker → Clear data) or uninstall the app.

## Section 2: Data Types

### Personal info
| Data type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Name | No | — | — | — |
| Email | No | — | — | — |
| User IDs (GitHub username) | Yes | No | App functionality — displayed in-app, used for API calls | Required |
| Other | No | — | — | — |

### Financial info
Not collected.

### Health and fitness
Not collected.

### Messages
| Data type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Other in-app messages (note text) | Yes | No* | App functionality — note content entered by user | Required |

*Note text is transmitted to the GitHub API but only to the user's own repository. It is not shared with other users or third parties.

### Photos and videos
Not collected.

### Audio
| Data type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Voice or sound recordings | No | No | — | — |

> The app uses `RECORD_AUDIO` permission for speech-to-text input via Android's built-in `SpeechRecognizer` API. No audio is recorded or stored by Note Taker. Speech processing is handled by the device's default speech recognition service (e.g., Google). The app receives only the transcribed text.

### Files and docs
Not collected.

### Calendar
Not collected.

### Contacts
Not collected.

### App activity
Not collected.

### Web browsing
Not collected.

### App info and performance
Not collected. No crash reporting or analytics SDK.

### Device or other IDs
Not collected.

### Location
Not collected.

## Section 3: Security Practices

### Is your app's data encrypted in transit?
**Yes** — HTTPS only (GitHub API).

### Can users request that their data be deleted?
**Yes** — delete notes from GitHub repo, clear app data, or uninstall.

### Does your app follow Google's Families Policy?
**No** — the app targets adults (18+), not designed for children.

## Summary Statement (for Data Safety UI)

> Note Taker does not collect any data. Your notes are stored locally on your device and sent only to your own GitHub repository over HTTPS at your direction. Speech-to-text is processed by your device's default speech service (e.g., Google) — no audio is recorded or stored by Note Taker. No analytics, ads, tracking, or third-party data collection. The developer has no access to your data.

## Data Flow Diagram

```
User speaks → Android SpeechRecognizer API → device speech service (e.g., Google) → transcribed text
User types  → text field
                ↓
         Note text stored locally (Room DB: pending_notes)
                ↓
         GitHub Contents API (HTTPS) → user's own GitHub repo
                ↓
         Submission recorded locally (Room DB: submissions)
```

### Locally Stored Data
| Data | Storage | Purpose |
|------|---------|---------|
| GitHub PAT | Preferences DataStore (app-private) | API authentication |
| GitHub username | Preferences DataStore (app-private) | Display and API calls |
| Repo owner/name | Preferences DataStore (app-private) | Target repo for notes |
| Pending notes | Room database (app-private) | Offline queue |
| Submission history | Room database (app-private) | Recent submissions display |
