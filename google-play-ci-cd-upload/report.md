# Google Play CI/CD Upload from GitHub Actions: Complete Guide

**Date:** 2026-02-14

## Executive Summary

Automating Android app uploads to Google Play from GitHub Actions is fully supported through the **Google Play Developer API v3 Publishing API**. The process requires a one-time manual setup across Google Cloud Console, Google Play Console, and GitHub, after which every subsequent build can be uploaded automatically. There is no official Google-provided GitHub Action -- the community-maintained `r0adkll/upload-google-play` is the de facto standard and wraps the API correctly.

The most important constraint to know upfront: **the very first AAB for a new app must be uploaded manually** through Play Console before the API can be used. The API cannot create a new app -- it can only modify existing ones.

---

## 1. Google Play Developer API Setup

### What is it?

The Google Play Developer API is a suite of REST-based web service APIs. For CI/CD purposes, the relevant component is the **Publishing API**, which handles uploading app bundles, assigning them to release tracks, and managing store listings.

The Publishing API uses a transactional **"edits"** model. You create an edit (a draft workspace), make changes within it (upload a bundle, assign to a track), and then commit the edit to make everything live atomically.

### How to enable it

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or select an existing one)
3. Navigate to **APIs & Services > Library**
4. Search for **"Google Play Android Developer API"**
5. Click **Enable**

That is all that is needed in Cloud Console for the API itself. No IAM roles need to be assigned at the Cloud project level for the API to function -- permissions are managed in Play Console.

**Official documentation:** [Getting Started | Google Play Developer API](https://developers.google.com/android-publisher/getting_started)

### OAuth scope

The single scope required for all Publishing API operations is:

```
https://www.googleapis.com/auth/androidpublisher
```

This scope is automatically used by the service account authentication flow and the `r0adkll/upload-google-play` action.

**Official documentation:** [Authorization | Google Play Developer API](https://developers.google.com/android-publisher/authorization)

---

## 2. Service Account Creation

A service account is the correct authentication mechanism for CI/CD (server-to-server access without human interaction).

### Steps in Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and select the project where you enabled the API
2. Navigate to **IAM & Admin > Service Accounts**
3. Click **Create Service Account**
4. Give it a descriptive name (e.g., `github-actions-play-publisher`)
5. You do not need to grant any Cloud IAM roles to this service account -- its permissions come from Play Console, not from Cloud IAM
6. Click **Done**
7. Click on the newly created service account
8. Go to the **Keys** tab
9. Click **Add Key > Create new key**
10. Select **JSON** format
11. Download the JSON key file -- this is the credential you will store as a GitHub Actions secret

The JSON key file contains the private key, client email, project ID, and authentication endpoints. It must be kept secret at all times.

**Official documentation:** [Getting Started | Google Play Developer API](https://developers.google.com/android-publisher/getting_started)

### Storing the key in GitHub Actions

In your GitHub repository:
1. Go to **Settings > Secrets and variables > Actions**
2. Create a new repository secret named something like `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
3. Paste the entire contents of the JSON key file as the secret value

---

## 3. Play Console API Access and Permissions

### Linking the service account to Play Console

**Important change:** You no longer need to link a Google Cloud project to Play Console. The old flow through "Settings > API access" for project linking is no longer required. Instead, you invite the service account directly as a user.

### Steps in Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Navigate to **Users and permissions**
3. Click **Invite new users**
4. Enter the service account's email address (it looks like `name@project-id.iam.gserviceaccount.com`)
5. Set the appropriate permissions (see below)
6. Click **Invite user**

### Required permissions

Play Console uses granular, individually selectable permissions -- there is no single "Release manager" predefined role. Permissions are split into two levels:

- **Account permissions** -- apply to all apps in the developer account
- **App permissions** -- apply only to a specific app

For CI/CD uploading to testing tracks, the service account needs at minimum these **app-level permissions**:

| Permission | Required for |
|---|---|
| **Release apps to testing tracks** | Uploading to internal, alpha (closed), or beta (open) testing tracks |
| **Release to production** | Uploading directly to the production track |
| **Use Play App Signing** | Required if app signing operations are involved |

If you want the service account to handle only internal/testing track uploads (recommended for safety), you can omit "Release to production" to prevent accidental production deployments.

**Official documentation:** [Add developer account users and manage permissions](https://support.google.com/googleplay/android-developer/answer/9844686)

---

## 4. Google Play App Signing

### Upload key vs. app signing key

Play App Signing uses a two-key system:

| Key | Who holds it | Purpose |
|---|---|---|
| **App signing key** | Google (managed in their infrastructure) | Signs the final APKs delivered to users' devices |
| **Upload key** | You (stored in your keystore) | Signs the AAB before uploading to Google Play; used to verify your identity |

When you upload an AAB signed with your upload key, Google verifies the signature, strips the upload signature, and re-signs the generated APKs with the app signing key before distribution.

### Is it mandatory?

**Yes.** Play App Signing has been mandatory for all new apps since August 2021. You cannot upload an AAB without being enrolled.

### Enrollment for new apps

When creating a new app and uploading the first AAB:
- **Recommended:** Let Google generate the app signing key. The key you used to build the AAB automatically becomes your upload key.
- **Alternative:** Provide your own app signing key by uploading it to Play App Signing.

Google recommends making the app signing key and upload key different for security.

### Compatibility with CI/CD

Fully compatible. In your CI/CD pipeline:
1. Store the upload keystore file and its password as GitHub Actions secrets
2. The build step signs the AAB with the upload key
3. The upload step sends the signed AAB to Google Play via the API
4. Google handles the rest with the app signing key

If you lose the upload key, you can contact Google support to reset it. The app signing key is never lost because Google manages it.

**Official documentation:**
- [Sign your app | Android Developers](https://developer.android.com/studio/publish/app-signing)
- [Use Play App Signing | Play Console Help](https://support.google.com/googleplay/android-developer/answer/9842756)

---

## 5. First Upload Requirement

### The API cannot create a new app

This is stated explicitly in the official documentation:

> "You can only use this API to make changes to an existing app (that has at least one APK uploaded); thus, you will have to upload at least one APK through the Play Console before you can use this API."

**What this means in practice:**

1. Create the app in Play Console manually (set the package name, store listing basics)
2. Upload the very first AAB manually through the Play Console UI (typically to the internal testing track)
3. After that, all subsequent uploads can be fully automated via the API / GitHub Actions

There is no workaround. The API will return a "Package not found" error if you attempt to use it on an app that has never had an upload through the console.

### Draft app gotcha

Even after the first manual upload, if the app is still in "draft" state (has never been published to any track with a completed status), the API will only accept releases with `status: draft`. Attempting to use `status: completed` will fail with: "Only releases with status draft may be created on draft app."

**Official documentation:** [Edits | Google Play Developer API](https://developers.google.com/android-publisher/edits)

---

## 6. Publishing API Tracks

### Available tracks

| Track | API name | Purpose |
|---|---|---|
| Internal testing | `internal` | Quick distribution to up to 100 internal testers. Does not require review. |
| Closed testing (Alpha) | `alpha` | Distribution to a controlled group of testers you define. |
| Open testing (Beta) | `beta` | Distribution to anyone who joins your open test. |
| Production | `production` | Distribution to all users on Google Play. |

### Staged rollouts

On the production track, you can do a staged rollout by specifying a `userFraction` (e.g., 0.1 for 10% of users). This can be increased, halted, or completed via the API.

### API restrictions on tracks

**There are no API-level restrictions on which tracks you can upload to.** The API can upload directly to production. The only restrictions are the Play Console permissions granted to your service account -- if the service account does not have "Release to production," the API call will be denied.

### The edits workflow (API call sequence)

The exact sequence of API calls for uploading a bundle:

1. `edits.insert` -- Creates a new edit (draft workspace mirroring the current app state)
2. `edits.bundles.upload` -- Uploads the AAB file into the edit's storage
3. `edits.tracks.update` -- Assigns the uploaded bundle to a track (e.g., `internal`, `production`) with a release status
4. `edits.commit` -- Commits the edit, making all changes live

You can also call `edits.delete` to abandon an edit and discard all changes.

**Official documentation:**
- [APKs and Tracks | Google Play Developer API](https://developers.google.com/android-publisher/tracks)
- [Edits | Google Play Developer API](https://developers.google.com/android-publisher/edits)

---

## 7. The r0adkll/upload-google-play GitHub Action

### What it is

A community-maintained GitHub Action that wraps the Google Play Developer API v3 Publishing API. It is the most widely used action for this purpose. **There is no official Google-provided GitHub Action** for uploading to Google Play.

### What it does under the hood

It performs the exact edits workflow described above: `edits.insert` -> `edits.bundles.upload` -> `edits.tracks.update` -> `edits.commit`.

### Key inputs

| Input | Required | Default | Description |
|---|---|---|---|
| `serviceAccountJsonPlainText` | One of two auth options | -- | The raw JSON text of the service account key |
| `serviceAccountJson` | One of two auth options | -- | Path to the JSON key file |
| `packageName` | Yes | -- | The app's package name (e.g., `com.example.app`) |
| `releaseFiles` | Yes (one of releaseFile/releaseFiles) | -- | Path(s) to AAB/APK file(s), supports glob patterns |
| `track` | No | `production` | Target track: `internal`, `alpha`, `beta`, `production` |
| `status` | No | `completed` | Release status: `completed`, `inProgress`, `halted`, `draft` |
| `whatsNewDirectory` | No | -- | Directory with release notes (e.g., `en-US.txt`) |
| `mappingFile` | No | -- | Path to R8/ProGuard mapping file |
| `debugSymbols` | No | -- | Path to native debug symbols |
| `changesNotSentForReview` | No | `false` | If `true`, changes are not sent for review immediately |
| `inAppUpdatePriority` | No | -- | In-app update priority (0-5) |

### Example workflow step

```yaml
- name: Upload to Google Play
  uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
    packageName: com.carsondavis.notetaker
    releaseFiles: app/build/outputs/bundle/release/app-release.aab
    track: internal
    status: completed
```

### Gotchas

1. **Draft app:** If the app has never been fully published, you must use `status: draft` or the API will reject the upload
2. **Package not found:** The app must exist in Play Console with at least one prior manual upload
3. **Version code conflicts:** The AAB's version code must be strictly higher than any previously uploaded version code

### Alternatives

- **Fastlane `supply`** -- A popular Ruby-based CI tool with deep Google Play integration. Not a GitHub Action but can be used in Actions workflows.
- **`KevinRohn/github-action-upload-play-store`** -- A simpler alternative supporting all tracks and statuses.

**Repository:** [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play)

---

## 8. 14-Day Closed Testing Requirement

### Who it applies to

Developers with **personal accounts** created after **November 13, 2023**. This does not officially apply to organization accounts, though some community reports suggest organization accounts may also encounter it.

### The requirement

Before you can apply for production access, you must:
1. Run a **closed test** (not internal test) for your app
2. Have at least **12 testers** opted into the closed test
3. Those testers must have been opted in for **at least 14 consecutive days**

### Key details

- **12 testers, not 20.** The original policy (Nov 2023) required 20 testers. This was revised down to 12 testers in **December 2024**.
- **"Opted-in" means enrolled in the closed test.** Testers join by clicking a link you provide. They do not need to actively use the app -- they just need to remain opted in.
- **Internal testing does not count.** Only closed testing (alpha track) satisfies this requirement.
- **14 days must be consecutive.** If a tester opts out and re-opts in, the clock resets.
- **After meeting the criteria,** you apply for production access through the Dashboard in Play Console and answer questions about your app and testing process.
- **This is a one-time gate** for getting the first app to production on a new personal account.

### Impact on CI/CD

This requirement does not affect the CI/CD pipeline itself. It is a manual prerequisite you must complete before the app can reach production. Once production access is granted, your CI/CD pipeline can upload to any track including production.

**Official documentation:** [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)

---

## 9. Version Code Requirements

### Rules

- **Version codes must be positive integers.** They are the internal version number, distinct from `versionName` (which is displayed to users).
- **Strictly increasing.** Every upload must have a version code higher than any previously uploaded version code for that app.
- **Globally unique per app.** Once a version code is used, it is permanently consumed -- even if the release was rejected, the draft was deleted, or the AAB was never promoted.
- **Google Play rejects duplicate version codes** with the error: "Version code X has already been used. Try another version code."

### CI/CD implications

Your build system must guarantee that every build produces a unique, strictly increasing version code. Common strategies:

- **GitHub Actions run number** (`${{ github.run_number }}`) -- simple, always increasing, but starts at 1 and can collide if you recreate workflows
- **Git commit count** -- increases with each commit but can reset if history is rewritten
- **Timestamp-based** -- e.g., `YYMMDDHHII` format, always increasing but uses large numbers
- **Manual management** -- increment in `build.gradle.kts` and commit the change

The safest approach for a solo developer is to use the version code from `build.gradle.kts` and increment it manually (or automate the increment as part of the CI pipeline).

**Official documentation:** [Version your app | Android Developers](https://developer.android.com/studio/publish/versioning)

---

## 10. Recent Changes (2025-2026)

### Publishing API stability

The Publishing API v3 has had **no breaking changes** in the 2025-2026 timeframe. The edits workflow, tracks system, and service account authentication remain unchanged. The API documentation was last updated January 29, 2026.

### Other relevant changes

| Change | Date | Impact |
|---|---|---|
| **Target API level 35 required** | August 31, 2025 | New apps and updates must target Android 15 (API level 35) |
| **Billing Library v7 required** | August 31, 2025 | Apps using billing must use version 7+ |
| **12-tester requirement** (down from 20) | December 2024 | Easier to meet closed testing requirement for new personal accounts |
| **Financial features declaration** | October 2025 | Must be completed for every app, even without financial features, or updates are blocked |
| **Android Developer Verification** | Late 2026 | All apps must be from verified developers (rolling out to select countries first) |

**Official documentation:** [Google Play Developer API release notes](https://developer.android.com/google/play/billing/play-developer-apis-release-notes)

---

## Complete End-to-End Checklist

Here is the full sequence of steps from zero to automated uploads:

### One-time setup (manual)

1. **Google Cloud Console**
   - Create a Google Cloud project (or use existing)
   - Enable the "Google Play Android Developer API"
   - Create a service account (no Cloud IAM roles needed)
   - Generate and download a JSON key file

2. **Google Play Console**
   - Go to Users and permissions
   - Invite the service account email as a new user
   - Grant permissions: "Release apps to testing tracks" (and optionally "Release to production")
   - Create the app listing (package name, basic metadata)
   - Upload the first AAB manually (to internal testing track)

3. **If new personal account (created after Nov 13, 2023)**
   - Set up closed testing track
   - Add 12+ testers and have them opt in
   - Wait 14 consecutive days
   - Apply for production access in Play Console Dashboard

4. **GitHub repository**
   - Add the service account JSON key as a repository secret (`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`)
   - Add the upload keystore and its password as repository secrets
   - Create the GitHub Actions workflow

### Automated pipeline (runs on every push/tag/release)

1. Checkout code
2. Set up JDK
3. Decode and place the upload keystore
4. Build the signed release AAB (`./gradlew bundleRelease`)
5. Upload the AAB to Google Play using `r0adkll/upload-google-play@v1`

### Workflow template

```yaml
name: Deploy to Google Play

on:
  push:
    tags:
      - 'v*'  # Trigger on version tags

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Decode keystore
        run: echo "${{ secrets.UPLOAD_KEYSTORE_BASE64 }}" | base64 -d > upload-keystore.jks

      - name: Build Release AAB
        run: ./gradlew bundleRelease
        env:
          SIGNING_STORE_FILE: upload-keystore.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}

      - name: Upload to Google Play
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.your.package.name
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: internal
          status: completed
```

---

## Key Gotchas Summary

1. **First upload must be manual.** The API cannot create a new app or upload to an app with zero prior uploads.
2. **Draft apps require `status: draft`.** If the app has never been published, the API rejects non-draft statuses.
3. **Version codes are burned forever.** Even failed/rejected uploads consume the version code permanently.
4. **12-tester / 14-day gate** exists for new personal developer accounts before production access is granted.
5. **No official Google GitHub Action.** You are relying on community-maintained `r0adkll/upload-google-play`.
6. **Google recommends rate limiting.** Do not publish testing updates more than once per day, and production updates even less frequently.
7. **Financial features declaration** must be completed or app updates will be blocked.
8. **Target API level 35** is required for new apps and updates as of August 2025.
