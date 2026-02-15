# Android CI/CD Branching Strategies with Google Play Deployment

**Date:** 2026-02-14

## Executive Summary

For a solo developer shipping a single Android app via GitHub Actions, the recommended setup is a **simplified Git Flow** (`develop` + `main` with short-lived feature branches), a **single GitHub Actions workflow** with branch conditionals, **`github.run_number` with offset** for version codes, **`versionName` managed manually or from Git tags**, and the **`r0adkll/upload-google-play` action** for Play Store uploads. Fastlane is overkill for this use case. The key constraint to plan around is that the very first Play Store release must be done manually -- CI/CD can only produce `draft` uploads until that first manual publish is complete.

---

## 1. Branching Strategy

### The Three Contenders

| Strategy | Complexity | Best For | Mobile Fit |
|----------|-----------|----------|------------|
| **Git Flow** (develop/main/release) | Medium | Scheduled releases, QA stages | Good -- maps naturally to Play Store tracks |
| **GitHub Flow** (main + feature branches) | Low | Frequent releases, web-style deployment | Decent but no built-in staging |
| **Trunk-Based Development** | Low-Medium | Continuous integration, large teams with feature flags | Gaining traction but needs feature flags |

### Recommendation: Simplified Git Flow

For a solo developer deploying to Google Play, a **two-branch model** works best:

- **`develop`** -- Active development. Pushes here trigger builds that upload to the **internal testing** track on Google Play. This is your daily driver.
- **`main`** -- Production-ready code. Merges from `develop` to `main` trigger builds that upload to the **production** track (or `beta` if you want a staging step).
- **Feature branches** -- Short-lived branches off `develop` for individual features. Merge back to `develop` via PR.

This gives you the core benefit of Git Flow (separate staging and production) without the overhead of release branches and hotfix branches. You can always add those later if needed.

**Why not trunk-based?** Trunk-based development is a strong pattern, and solo developers can absolutely use it. But it shines most when you have feature flags to gate incomplete work, and for a solo developer with a simple app, the two-branch model gives you a natural "internal test" vs "production" split that maps directly to Play Store tracks without any feature flag infrastructure.

**Why not pure GitHub Flow?** It works fine if you only want one deployment target, but the two-branch model gives you a free internal testing pipeline that catches issues before they hit production.

### Mobile-Specific Considerations

Mobile apps have a unique constraint that web apps do not: releases go through app store review. You cannot deploy continuously to production the way you can with a web service. This makes having a separate development/testing track valuable -- you can push builds continuously to internal testing while being more deliberate about production releases.

The "release train" model (cutting releases on a schedule, e.g., every 2 weeks) pairs well with this branching strategy for mobile.

---

## 2. Automatic Version Code Management

### versionCode vs versionName

- **`versionCode`** -- An integer used by Google Play to determine update ordering. Must be strictly increasing with every upload. Users never see it. Max value: 2,100,000,000.
- **`versionName`** -- A human-readable string (e.g., `1.2.3`) displayed to users. Has no impact on Play Store update logic.

### Recommended Approach: `github.run_number` with Offset

The simplest approach that works reliably:

```yaml
env:
  VERSION_CODE: ${{ github.run_number }}
```

If your app already has a versionCode higher than your current run number, add an offset:

```yaml
- name: Calculate version code
  run: echo "VERSION_CODE=$((1000 + ${{ github.run_number }}))" >> $GITHUB_ENV
```

**Why this works well for a single workflow:**
- `github.run_number` is per-workflow and always increases (never resets)
- If the same workflow handles both `develop` and `main` branches, the counter is shared, so version codes are always unique and strictly increasing across both tracks
- Does not change on re-runs (same run number = same version code, which is correct behavior)
- No external dependencies, no API calls, no plugins

**How to inject it into Gradle:**

In `build.gradle.kts` (or `build.gradle`):

```kotlin
android {
    defaultConfig {
        versionCode = (project.findProperty("VERSION_CODE") as? String)?.toInt() ?: 1
        versionName = "1.0.0"  // Manage manually or derive from Git tags
    }
}
```

Pass from CI:

```yaml
- name: Build Release AAB
  run: ./gradlew bundleRelease -PVERSION_CODE=${{ env.VERSION_CODE }}
```

The fallback to `1` means local development builds still work without any environment setup.

### versionName Management

For a solo developer, the simplest approach is to **manage versionName manually in `build.gradle.kts`**. You bump it when you make a release to production (or when the version semantically changes). This is a conscious decision, not something that should auto-increment on every build.

If you want automation, the `ReactiveCircus/app-versioning` Gradle plugin can derive both versionCode and versionName from Git tags (e.g., tag `v1.2.3` produces versionName `1.2.3`). This avoids "version bump" commits, but requires fetching tags in CI (`git fetch --depth=100 --tags`).

### Approaches Comparison

| Approach | Complexity | Multi-Branch Safe | External Deps |
|----------|-----------|-------------------|---------------|
| `github.run_number` (single workflow) | Very low | Yes (shared counter) | None |
| `github.run_number` + offset | Low | Yes | None |
| Gradle plugin (ReactiveCircus) | Medium | Yes (Git tag based) | Gradle plugin |
| Fetch from Play Console + increment | High | Yes (source of truth) | API access |
| Modify build.gradle with sed/grep | Low | Fragile | None |

---

## 3. Multi-Track Deployment from Branches

### Branch-to-Track Mapping

| Branch | Play Store Track | Status | Purpose |
|--------|-----------------|--------|---------|
| `develop` | `internal` | `completed` | Every push deploys for self-testing |
| `main` | `production` | `completed` | Merges deploy to all users |

For the `internal` track, `completed` status makes the build immediately available to internal testers (just you, as a solo developer). There is no review process for internal track uploads.

For the `production` track, `completed` means the release is submitted for review and will go live when approved. You could instead use `inProgress` with a `userFraction` (e.g., `0.1` for 10%) for staged rollouts.

### The Draft Status Exception

**Critical for new apps:** If your app has never been published through the Play Console, the Google Play API only allows `status: draft`. You must:

1. Set `status: draft` in your CI/CD configuration
2. Complete the first publish manually via Play Console (fill out store listing, content rating, pricing, etc.)
3. After the first manual publish succeeds, switch CI/CD to `status: completed`

This is the single most common source of CI/CD failures for new Android apps.

---

## 4. GitHub Actions Workflow Patterns

### Recommendation: Single Workflow with Conditional Deploy Jobs

Use one workflow file that triggers on both branches. The build job runs for every push. Deploy jobs use `if` conditions to determine which track to upload to.

```yaml
name: Build and Deploy

on:
  push:
    branches: [develop, main]

env:
  VERSION_CODE: ${{ github.run_number }}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Build Release AAB
        run: ./gradlew bundleRelease -PVERSION_CODE=${{ env.VERSION_CODE }}

      - name: Upload AAB artifact
        uses: actions/upload-artifact@v4
        with:
          name: release-aab
          path: app/build/outputs/bundle/release/*.aab

  deploy-internal:
    needs: build
    if: github.ref == 'refs/heads/develop'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: release-aab

      - name: Upload to Internal Track
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.example.app
          releaseFiles: "*.aab"
          track: internal
          status: completed

  deploy-production:
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: release-aab

      - name: Upload to Production Track
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.example.app
          releaseFiles: "*.aab"
          track: production
          status: completed
          whatsNewDirectory: whatsnew/
```

**Why a single workflow?**
- The `github.run_number` counter is shared across all runs, guaranteeing unique, increasing version codes regardless of which branch triggered the build
- No YAML duplication between files
- The full pipeline is visible in one place
- Conditional jobs are clean and readable

**Why not separate workflows?**
- Separate workflows have independent `run_number` counters, creating a risk of versionCode collisions (e.g., both workflows produce versionCode 5)
- You would need offset tricks to avoid this, adding unnecessary complexity

---

## 5. The `r0adkll/upload-google-play` Action

### Key Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `serviceAccountJsonPlainText` | Yes* | -- | Service account JSON (store in GitHub secret) |
| `packageName` | Yes | -- | Your app's package name |
| `releaseFiles` | Yes | -- | Path to .aab or .apk files (comma-separated for multiple) |
| `track` | No | `production` | Target track: `internal`, `alpha`, `beta`, `production` |
| `status` | No | `completed` | Release status: `draft`, `completed`, `inProgress`, `halted` |
| `userFraction` | No | -- | Fraction for staged rollout (e.g., `0.33`). Requires `inProgress` status |
| `whatsNewDirectory` | No | -- | Directory with locale-specific release notes |
| `mappingFile` | No | -- | Path to ProGuard/R8 mapping file |
| `debugSymbols` | No | -- | Path to native debug symbols zip |
| `changesNotSentForReview` | No | `false` | If `true`, changes won't auto-submit for review |

*You can alternatively use `serviceAccountJson` (path to file) instead of `serviceAccountJsonPlainText`.

### Release Notes Structure

Create a `whatsnew/` directory in your repo:

```
whatsnew/
  whatsnew-en-US    # English release notes (plain text, 500 char max)
```

File naming uses BCP 47 locale format: `whatsnew-<language>-<COUNTRY>`.

### Common Pitfalls

1. **"Only releases with status draft may be created on draft app"** -- Your app hasn't been published yet. Use `status: draft` until first manual publish.
2. **"changesNotSentForReview must not be set"** -- Don't set this parameter unless you specifically need it. Remove it from your config if you're getting this error.
3. **"Changes cannot be sent for review automatically"** -- Your app's metadata is incomplete. Complete all required fields in Play Console first.

---

## 6. Fastlane: When to Use It

### Fastlane vs Direct GitHub Actions

| Factor | Fastlane | GitHub Actions + r0adkll |
|--------|----------|-------------------------|
| **Setup complexity** | Medium (Ruby, Gemfile, Fastfile) | Low (just YAML) |
| **Upload to Play Store** | `supply` action -- full metadata management | `r0adkll/upload-google-play` -- binary upload only |
| **Screenshot management** | Yes (`screengrab`) | No |
| **Cross-platform** | Yes (iOS + Android) | Android only |
| **Local testing** | Run lanes locally | Need `act` or similar |
| **Maintenance burden** | Ruby dependency management | Minimal |

### Verdict for Solo Developer

**Skip Fastlane.** For a single Android app where you manage store listing metadata through the Play Console UI, `r0adkll/upload-google-play` does everything you need with zero additional dependencies. The direct GitHub Actions approach is simpler to set up, simpler to debug, and simpler to maintain.

Consider Fastlane if you later need to:
- Automate screenshot generation
- Manage store listing metadata (descriptions, screenshots) in version control
- Add an iOS app and want a unified pipeline
- Use complex multi-step build/test/deploy lanes

---

## 7. Release Notes Automation

### Practical Options (Least to Most Complex)

1. **Manual `whatsnew-en-US` file** -- Just edit the file before merging to `main`. Simple, gives you full control over user-facing text. Best for a solo developer.

2. **GitHub Release Notes** -- Create a GitHub Release when merging to `main`. GitHub auto-generates notes from PR titles and commit messages. Copy relevant bits to `whatsnew-en-US`.

3. **Conventional Commits + Auto-generation** -- Use structured commit messages (`feat:`, `fix:`, `chore:`) and a tool to auto-generate changelogs. Overkill for a solo developer but nice if you adopt the convention anyway.

4. **Release Drafter** -- GitHub App that maintains a draft release body from merged PR titles. Good middle ground if you use PRs consistently.

### Recommendation

Start with option 1 (manual file). Play Store release notes are user-facing marketing text, not developer changelogs. "Bug fixes and performance improvements" is what 90% of apps ship anyway. Automate this only if you find yourself forgetting to update it.

---

## 8. New App Bootstrapping Checklist

For an app that has never been published, here is the sequence to set up CI/CD:

1. **Complete Play Console setup:**
   - Create the app in Play Console
   - Fill out all store listing information (description, screenshots, contact info)
   - Complete content rating questionnaire
   - Set up pricing and distribution
   - Configure Play App Signing

2. **If you have a new personal developer account (created after Nov 13, 2023):**
   - You must run a closed test with at least **12 testers for 14 consecutive days** before you can publish to production
   - This is a Google Play policy, not a technical limitation

3. **Set up the Google Play Developer API:**
   - Enable the API in Google Cloud Console
   - Create a service account
   - Grant the service account "Release Manager" (or equivalent) permissions in Play Console
   - Download the JSON key and store it as a GitHub secret

4. **First deploy with CI/CD (use `status: draft`):**
   - Configure your workflow with `status: draft`
   - Push to trigger the workflow
   - Verify the draft appears in Play Console

5. **First manual publish:**
   - Go to Play Console and publish the draft release manually
   - This completes the initial setup

6. **Switch to automated publishing:**
   - Change `status: draft` to `status: completed` in your workflow
   - From now on, pushes to `main` will automatically publish to production

---

## Summary: Recommended Stack for Solo Developer

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Branching** | `develop` + `main` + feature branches | Maps to internal/production tracks |
| **CI/CD** | Single GitHub Actions workflow | Shared run number, no duplication |
| **Version code** | `github.run_number` (+ offset if needed) | Zero config, always unique |
| **Version name** | Manual in `build.gradle.kts` | Intentional, not every build |
| **Play Store upload** | `r0adkll/upload-google-play` | Native GitHub Action, sufficient features |
| **Track mapping** | `develop` -> internal, `main` -> production | Simple, effective |
| **Release notes** | Manual `whatsnew-en-US` file | User-facing text deserves intention |
| **Fastlane** | Skip | Overkill for single Android app |
