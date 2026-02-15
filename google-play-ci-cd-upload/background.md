# Research Background: Google Play CI/CD Upload from GitHub Actions

**Date:** 2026-02-14
**Topic:** End-to-end process for automatically uploading Android AAB files to Google Play from GitHub Actions
**Description:** Comprehensive research into Google Play Developer API setup, service accounts, Play Console API access, app signing, publishing tracks, version codes, testing requirements, and GitHub Actions integration. Focus exclusively on official Google documentation.

## Sources

[1]: https://developer.android.com/google/play/developer-api "Google Play Developer APIs | Android Developers"
[2]: https://developers.google.com/android-publisher/getting_started "Getting Started | Google Play Developer API"
[3]: https://developers.google.com/android-publisher#publishing "Publishing API Reference"
[4]: https://developers.google.com/android-publisher/authorization "Authorization | Google Play Developer API"
[5]: https://support.google.com/googleplay/android-developer/answer/6110967 "Link your developer account to Google services - Play Console Help"
[6]: https://support.google.com/googleplay/android-developer/answer/9844686 "Add developer account users and manage permissions - Play Console Help"
[7]: https://support.google.com/googleplay/android-developer/answer/9859348 "Prepare and roll out a release - Play Console Help"
[8]: https://support.google.com/googleplay/android-developer/answer/9842756 "Use Play App Signing - Play Console Help"
[9]: https://developer.android.com/studio/publish/app-signing "Sign your app | Android Studio | Android Developers"
[10]: https://developer.android.com/guide/app-bundle/faq "Android App Bundle FAQ | Android Developers"
[11]: https://developers.google.com/android-publisher/tracks "APKs and Tracks | Google Play Developer API"
[12]: https://developers.google.com/android-publisher/edits "Edits | Google Play Developer API"
[13]: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload "Method: edits.bundles.upload | Google Play Developer API"
[14]: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks "REST Resource: edits.tracks | Google Play Developer API"
[15]: https://support.google.com/googleplay/android-developer/answer/14151465 "App testing requirements for new personal developer accounts - Play Console Help"
[16]: https://support.google.com/googleplay/android-developer/community-guide/255621488 "Everything about the 12 testers requirement - Google Play Developer Community"
[17]: https://support.google.com/googleplay/android-developer/answer/9845334 "Set up an open, closed, or internal test - Play Console Help"
[18]: https://developer.android.com/studio/publish/versioning "Version your app | Android Studio | Android Developers"
[19]: https://support.google.com/googleplay/android-developer/answer/9859350 "Update or unpublish your app - Play Console Help"
[20]: https://github.com/r0adkll/upload-google-play "r0adkll/upload-google-play GitHub Repository"
[21]: https://github.com/r0adkll/upload-google-play/blob/master/README.md "r0adkll/upload-google-play README"
[22]: https://github.com/r0adkll/upload-google-play/blob/master/action.yml "r0adkll/upload-google-play action.yml"
[23]: https://github.com/r0adkll/upload-google-play/issues/70 "Issue #70: Only releases with status draft may be created on draft app"
[24]: https://developer.android.com/google/play/billing/play-developer-apis-release-notes "Google Play Developer API release notes"
[25]: https://support.google.com/googleplay/android-developer/answer/11926878 "Target API level requirements for Google Play apps"
[26]: https://developers.google.com/identity/protocols/oauth2/scopes "OAuth 2.0 Scopes for Google APIs"

## Research Log

---

### Search: "Google Play Developer API setup enable site:developer.android.com"

**The Google Play Developer APIs** are a suite of REST-based web service APIs for performing publishing, reporting, and app-management functions programmatically. ([Google Play Developer APIs][1])

**Key APIs available:**
- **Publishing API** - Upload new versions of apps, release apps by assigning APKs/AABs to tracks (alpha, beta, staged rollout, production), create and modify store listings. Uses transactional "edits" model - bundle changes into a draft, commit once. ([Google Play Developer APIs][1])
- **Subscriptions and In-App Purchases API** - manage catalog, verify purchases
- **Reporting API** - app-level metrics and Android vitals
- **Reply to Reviews API** - view and respond to reviews
- **Permissions API** - automate user access management
- **Voided Purchases API** - access voided orders

**For CI/CD purposes, the Publishing API is the relevant one.** It uses a transactional "edits" workflow - you create an edit, make changes (upload APK/AAB, assign to tracks), then commit the edit. ([Google Play Developer APIs][1])

**Best practice from Google:** Don't publish alpha/beta updates more than once daily; production updates even less frequently. ([Google Play Developer APIs][1])

**Getting Started guide is at:** https://developers.google.com/android-publisher/getting_started ([Google Play Developer APIs][1])

**Access methods:** OAuth client or service account. For server-to-server (CI/CD), service accounts are the way to go. ([Google Play Developer APIs][1])

---

### Search: "Google Play Developer API getting started service account setup"

**Setup involves changes in both Google Cloud Console and Google Play Console.** ([Getting Started][2])

**Step-by-step process:**

1. **Create a Google Cloud Project** in Google Cloud Console (or use an existing one) ([Getting Started][2])
2. **Enable the Google Play Developer API** - go to the Google Play Developer API page in Google Cloud Console and click Enable ([Getting Started][2])
3. **Create a Service Account** - in Google Cloud Console, go to Service Accounts, click "Create service account" ([Getting Started][2])
4. **Grant Permissions in Play Console** - go to Users & Permissions in Play Console, click "Invite new users", enter the service account email address, grant necessary rights ([Getting Started][2])

**Important discovery:** "You no longer need to link your developer account to a Google Cloud Project in order to access the Google Play Developer API." This is a change from the older process. ([Getting Started][2])

**Service account credentials** must be securely managed - stored in a secure environment like a server (or GitHub Actions secrets). ([Getting Started][2])

---

### Search: "Google Play Console permissions list release apps production testing"

**Play Console has two levels of permissions:** ([Add developer account users][6])
- **Account permissions** - apply to all apps in your developer account
- **App permissions** - apply only to the selected app

**Key permissions for CI/CD releases:**
- **"Release apps to testing tracks"** - needed to create a new release ([Prepare and roll out a release][7])
- **"Release to production"** - needed to publish updates to production ([Prepare and roll out a release][7])
- **"Use Play App Signing"** - needed for app signing operations ([Prepare and roll out a release][7])

**Three access levels:** account owner, admins, and users. The account owner or admin can add users and manage permissions. ([Add developer account users][6])

**For a service account doing CI/CD uploads, you'd need at minimum:** "Release apps to testing tracks" and potentially "Release to production" depending on which track you're targeting.

---

### Search: "Play App Signing upload key signing key difference how works mandatory"

**Play App Signing uses two keys:** ([Sign your app][9])
- **App signing key** - Google manages and protects this key. Used to sign the final APKs distributed to users. You never need this key after enrollment.
- **Upload key** - You keep this key and use it to sign your app bundle/APK before uploading to Google Play. Google uses the upload certificate to verify your identity.

**How it works:** App bundles defer building and signing APKs to Google Play Store. You upload an AAB signed with your upload key. Google verifies the upload key signature, then uses the app signing key to sign the optimized APKs generated from the bundle. ([Sign your app][9])

**Play App Signing is MANDATORY for all new apps since August 2021.** Required for new apps to use AABs. ([Android App Bundle FAQ][10])

**Enrollment options for new apps:** ([Sign your app][9])
- **Let Google generate the app signing key** (recommended) - Google generates a secure key. The key you used to sign the app becomes your upload key.
- **Provide your own app signing key** - You can upload your own signing key to Play App Signing.

**Security recommendation:** Make sure your app signing key and upload key are different. ([Sign your app][9])

**Lost upload key:** If you lose your upload key, you can contact support to reset it. The app signing key managed by Google is never lost. ([Use Play App Signing][8])

**CI/CD implication:** For CI/CD, you sign the AAB with your upload key (stored as a GitHub Actions secret), then the API uploads it to Google Play. Google handles the rest with the app signing key. This is fully compatible with CI/CD.

---

### Search: "Google Play Developer API edits tracks upload bundle first APK create new app"

**CRITICAL FINDING - First upload requirement confirmed:** "You can only use this API to make changes to an existing app (that has at least one APK uploaded); thus, you will have to upload at least one APK through the Play Console before you can use this API." ([Edits][12])

**This means:** The very first AAB/APK for a brand new app MUST be uploaded manually through the Play Console. After that, all subsequent uploads can be automated via the API.

**Available tracks:** ([APKs and Tracks][11])
- **Internal testing** - deployed to internal test track as configured in Play Console
- **Alpha** (closed testing) - deployed to users assigned to alpha test group
- **Beta** (open testing) - deployed to users assigned to beta test group
- **Production** - deployed to all users
- **Staged rollout on production** - gradual release to a percentage of users, can be increased, halted, or completed

**The API can upload to ALL tracks, including production.** There is no API restriction limiting uploads to only testing tracks.

**Edits workflow (the actual API call sequence):** ([Edits][12])
1. **Create an edit** (`edits.insert`) - creates a copy of the current deployed state
2. **Upload bundle** (`edits.bundles.upload`) - uploads the AAB to a storage area ([edits.bundles.upload][13])
3. **Assign to track** (`edits.tracks.update`) - assigns the uploaded bundle to the desired track ([edits.tracks][14])
4. **Commit the edit** (`edits.commit`) - makes the changes live
- Can also **abandon** (`edits.delete`) to discard changes

---

### Search: "app testing requirements new personal developer accounts 14 days testers closed testing"

**Who it applies to:** Developers with personal accounts created after November 13, 2023. ([App testing requirements][15])

**The requirement:** Run a closed test for your app with a minimum of **12 testers** who have been **opted-in for at least the last 14 days continuously**. ([App testing requirements][15])

**History of the requirement:** ([Everything about the 12 testers requirement][16])
- November 2023: Google introduced this policy, originally requiring **20 testers** for 14 days
- December 2024: Revised down to **12 testers** for 14 days, after recognizing challenges for individual developers

**What "opted-in" means:** Testers must be opted into your closed test (not internal test) and must have been opted-in for the last 14 consecutive days at the time you apply for production. ([App testing requirements][15])

**After meeting the criteria:** You apply for production access on the Dashboard in Play Console. You must answer questions about your app, its testing process, and its production readiness. ([App testing requirements][15])

**Key distinction - closed test vs internal test:** Internal testing is limited (up to 100 testers), and is optional but recommended as a starting point. Closed testing is the one that counts for the 14-day requirement. ([Set up an open, closed, or internal test][17])

**Does NOT apply to organization accounts** (only personal accounts created after Nov 13, 2023). However, community threads suggest some organization accounts may also be seeing this requirement.

---

### Search: "Google Play version code requirements strictly increasing versionCode upload"

**Version code is a positive integer** used as an internal version number. Higher numbers indicate more recent versions. This is NOT the version number shown to users (that's `versionName`). ([Version your app][18])

**Google Play enforces unique version codes:** You cannot reuse a version code that has already been used. The error "version code X has already been used" will be returned if you try. ([Version your app][18])

**Version codes must be strictly increasing for updates:** The version code of a new upload must be greater than the current version code. You cannot upload a lower or equal version code as an update to an existing track. ([Update or unpublish your app][19])

**Once a version code is used, it's burned forever** - even if the release was rejected or you deleted the draft. You cannot reuse that version code number for the same app.

**For CI/CD, this means:** Your build system needs to ensure every build produces a unique, incrementing version code. Common approaches include using build numbers from CI, git commit counts, or timestamp-based codes.

---

### Search: "r0adkll upload-google-play GitHub Action" (multiple searches combined)

**What it is:** A community-maintained GitHub Action to upload Android .apk or .aab files to the Google Play Console using the Google Play Developer API v3. ([r0adkll/upload-google-play][20])

**No official Google GitHub Action exists** for uploading to Google Play. The `r0adkll/upload-google-play` is the most widely used community action. Google provides `google-github-actions/auth` for authentication but not for Play Store uploads.

**What it does under the hood:** It wraps the Google Play Developer API v3 Publishing API. It performs the edits workflow: insert edit, upload bundle, assign to track, commit edit. ([r0adkll/upload-google-play README][21])

**Key inputs:** ([r0adkll/upload-google-play action.yml][22])
- `serviceAccountJson` - path to the service account JSON key file
- `serviceAccountJsonPlainText` - raw JSON text of the service account key (alternative)
- `packageName` - the app's package name / application ID (required)
- `releaseFiles` - the AAB/APK file(s) to upload, supports glob patterns
- `track` - which track to upload to (internal, alpha, beta, production). Default: production
- `status` - release status: `completed`, `inProgress`, `halted`, or `draft`. Default: completed
- `whatsNewDirectory` - directory containing release notes files
- `mappingFile` - path to ProGuard/R8 mapping file
- `debugSymbols` - path to native debug symbols
- `changesNotSentForReview` - if true, changes are not immediately sent for review. Default: false
- `inAppUpdatePriority` - priority for in-app updates (0-5)

**Critical gotcha - draft app issue:** If the app has never been published (is still in "draft" state in Play Console), you MUST use `status: draft`. Using any other status (like `completed`) will fail with: "Only releases with status draft may be created on draft app." ([Issue #70][23])

**This reinforces the first-upload requirement:** The first AAB must be manually uploaded and the app must leave draft state before the action can be used with `status: completed`.

**Alternatives:**
- `KevinRohn/github-action-upload-play-store` - simpler alternative
- `smartone-solutions/actions-upload-google-play` - fork of r0adkll
- Fastlane `supply` action - another popular option (not a GitHub Action, but a CI tool)

---

### Search: "Google Play Developer API changes 2025 2026 new requirements"

**Target API level requirement (August 2025):** New apps and app updates must target Android 15 (API level 35) by August 31, 2025. Existing apps must target Android 14+ to remain available to new users. ([Target API level requirements][25])

**Billing Library v7 required (August 2025):** All new apps and updates must use Billing Library version 7 or newer by August 31, 2025. ([Google Play Developer API release notes][24])

**Android Developer Verification (late 2026):** All Android apps must be registered by verified developers to be installed on certified devices. Rolling out to Brazil, Singapore, Indonesia, Thailand first, with global rollout to follow.

**Financial features declaration:** Must be completed for every app - even if it has no financial features. Can't make updates until this is completed.

**No breaking changes to the Publishing API itself** were found in the 2025-2026 timeframe. The API v3 remains the current version. The edits workflow, tracks system, and service account authentication remain unchanged.

---

### Search: "Google Play Developer API authorization service account JSON key OAuth scope"

**OAuth scope required:** `https://www.googleapis.com/auth/androidpublisher` - this is the single scope needed for the Google Play Developer API. ([Authorization][4], [OAuth 2.0 Scopes][26])

**Authentication flow for service accounts:** ([Authorization][4])
1. Load the service account JSON key file
2. Request an access token using the `androidpublisher` scope
3. Pass the access token in the Authorization header of API requests

**The JSON key file contains:** project ID, private key ID, private key, client email, client ID, auth URI, token URI, and other metadata. Must be kept secret.

**For the r0adkll GitHub Action:** The entire JSON key file content is stored as a GitHub Actions secret and passed via the `serviceAccountJsonPlainText` input.
