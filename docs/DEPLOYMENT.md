# Deployment Guide

Single source of truth for the CI/CD pipeline, versioning, and Google Play publishing.

## Branch Strategy

```
feature branches → develop → staging → master
                   (no deploy)  (closed testing)  (production track)
```

- **`develop`** — daily driver. Push freely, nothing deploys.
- **`staging`** — merge develop here to auto-deploy to Google Play **closed testing** track.
- **`master`** — merge staging here to auto-deploy to Google Play **production** track.
- **Feature branches** — `feature/<name>` off develop, merge back when done.

Merges always flow one direction: `develop → staging → master`. Never commit directly to `staging` or `master` except hotfixes (see [Hotfix Process](#hotfix-process)).

## How Builds Work

A single GitHub Actions workflow (`.github/workflows/deploy.yml`) triggers on push to `staging` or `master`. Using one workflow ensures a shared `github.run_number` counter — no version code collisions between tracks.

**Jobs:**

| Job | Runs on | What it does |
|-----|---------|-------------|
| `build` | `staging` and `master` | Checkout, build signed AAB, upload as artifact |
| `deploy-staging` | `staging` only | Download artifact, upload to Play Store **closed testing** track with release notes |
| `deploy-production` | `master` only | Download artifact, upload to Play Store **production** track with release notes |

Pushing to `develop` does **not** trigger any workflow.

## Versioning

### Version Code (`versionCode`)

An integer that must strictly increase with every upload to Google Play. Once a code is uploaded, it is **burned forever** — even if the release fails or is deleted.

- **CI builds:** `github.run_number + 100`. The offset avoids collision with historical codes 1–6 from manual uploads.
- **Local builds:** `VERSION_CODE` in `local.properties` (currently 6). Only matters for debug builds on a physical device. Never uploaded to Play.
- **Fallback:** If neither Gradle property nor `local.properties` provides a value, defaults to `1` so a fresh clone still builds.

**Priority chain in `build.gradle.kts`:**
1. Gradle property (`-PVERSION_CODE=101`) — used by CI
2. Env var / `local.properties` via `prop()` — used by local dev
3. Fallback `1`

### Version Name (`versionName`)

Human-readable version string following **semantic versioning**: `MAJOR.MINOR.PATCH`

- **MAJOR** — breaking changes or complete rewrites
- **MINOR** — new user-visible features
- **PATCH** — bug fixes and polish

Hardcoded in `build.gradle.kts`. Bumped manually as part of a "prepare release" commit on `develop`. Every build between version bumps shares the same name but has a unique code — this is normal.

## Releasing a New Version

All three steps happen in a single "prepare release" commit on `develop`:

1. **Bump `versionName`** in `app/build.gradle.kts`
2. **Add a new section** to `CHANGELOG.md` (developer-facing, detailed)
3. **Update `whatsnew/whatsnew-en-US`** (Play Store user-facing, max 500 characters)

Then promote through the branches:

```bash
# 1. Merge to staging → auto-deploys to internal testing
git checkout staging && git merge develop && git push

# 2. Install from Play Store internal link, verify on device

# 3. Merge to master → auto-deploys to production
git checkout master && git merge staging && git push
```

## Google Play Bootstrap (One-Time Setup)

These steps only need to be done once to enable the CI/CD pipeline.

### Step 1: Create app in Play Console

Create the app and fill out all required dashboard sections (store listing, content rating, data safety, privacy policy, etc.). All materials are in `docs/playstore/`.

### Step 2: First AAB — manual upload

Build locally: `./gradlew bundleRelease -PVERSION_CODE=7`

Upload manually to the **internal testing** track in Play Console. Accept Play App Signing (Google generates the app signing key; your keystore becomes the upload key).

**The API cannot be used until this first manual upload exists.**

### Step 3: 14-day closed testing

Closed testing track must have at least 12 testers opted in for 14 days before production access is granted. This is a one-time gate for personal developer accounts created after November 2023.

After 14 days, apply for production access via the Play Console Dashboard. Once granted, it's permanent.

**Until production access is granted:** the `deploy-production` job uploads with `status: draft` and you promote manually in Play Console.

### Step 4: Create service account

**Google Cloud Console:**
1. Create a project (or use existing)
2. Enable **Google Play Android Developer API**
3. Create a service account (`github-actions-play-publisher`), no Cloud IAM roles needed
4. Generate a JSON key and download it

**Play Console:**
1. Go to **Users and permissions** (NOT Settings → API access)
2. Invite the service account email
3. Grant **app-level** permissions:
   - "Release apps to testing tracks"
   - "Release to production, exclude devices, and use Play App Signing"

### Step 5: Configure GitHub secrets

Add these 6 secrets to the repository (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -i ../upload-keystore.jks` output |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `upload-key` |
| `KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Entire JSON key file contents |
| `PLAY_PACKAGE_NAME` | `com.carsondavis.notetaker` |

### Step 6: First CI deploy (draft mode)

1. Merge a change to `staging` → workflow runs → uploads with `status: draft`
2. Go to Play Console, review and publish the draft manually
3. Repeat for `master` branch

### Step 7: Switch to automated publishing

Edit `deploy.yml`: change `status: draft` to `status: completed` on both deploy jobs. From this point on, merges auto-publish without manual intervention.

## Google Play Long-Term

Once bootstrap is complete, the pipeline runs hands-free:

- **Merge to `staging`** → AAB uploaded to closed testing track with release notes. Beta testers can install via the Play Store closed testing link.
- **Merge to `master`** → AAB uploaded to production track with release notes from `whatsnew/whatsnew-en-US`.

Check upload status in **Play Console → Release → [track] → Release dashboard**.

The `status: draft` / `status: completed` setting in `deploy.yml` controls whether uploads require manual promotion in Play Console or go live automatically.

## Changelog & Release Notes

### `CHANGELOG.md` (developer-facing)

Detailed release notes following the existing format:

```markdown
## v0.5.0

**What's New**
- Feature description
- Feature description
```

### `whatsnew/whatsnew-en-US` (Play Store, user-facing)

Plain text, max **500 characters**. Written for end users, not developers. Bulleted list format (`- Item`). Updated in the same commit as `CHANGELOG.md`.

Only referenced by the production deploy job.

## GitHub Secrets Reference

| Secret | What it contains |
|--------|-----------------|
| `KEYSTORE_BASE64` | Base64-encoded upload keystore (`.jks` file) |
| `KEYSTORE_PASSWORD` | Password for the keystore |
| `KEY_ALIAS` | Alias of the signing key inside the keystore (e.g., `upload-key`) |
| `KEY_PASSWORD` | Password for the signing key |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON contents of the Google Play service account key |
| `PLAY_PACKAGE_NAME` | `com.carsondavis.notetaker` |

## Hotfix Process

For emergency fixes that can't wait for the normal `develop → staging → master` flow:

```bash
# 1. Branch from master
git checkout -b hotfix/critical master

# 2. Fix and test locally

# 3. Merge to master and deploy
git checkout master && git merge hotfix/critical && git push

# 4. Backport to develop so the fix isn't lost
git checkout develop && git merge hotfix/critical
```

## Troubleshooting

### "Draft app" — uploads succeed but nothing publishes

The deploy jobs use `status: draft` initially. This is required until the first manual publish in Play Console. After bootstrap, change to `status: completed` in `deploy.yml` (see [Step 7](#step-7-switch-to-automated-publishing)).

### Version code collision

Google Play rejects uploads if the version code has already been used. Since CI uses `github.run_number + 100`, collisions mean a manual upload used a code in the CI range. Fix: bump the offset in `deploy.yml` above the conflicting code.

### Service account permissions error

The service account needs **app-level** permissions granted via **Play Console → Users and permissions**, not Cloud IAM roles. Required permissions: "Release apps to testing tracks" and "Release to production, exclude devices, and use Play App Signing".

### Build fails in CI but works locally

Check that all 6 GitHub secrets are set. The signing config requires `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Missing any of these causes a Gradle build failure.

### "No existing edit" or API error on first upload

The Google Play API requires at least one AAB to have been uploaded manually before API uploads work. Complete [Step 2](#step-2-first-aab--manual-upload) first.
